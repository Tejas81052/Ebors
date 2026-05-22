/*
 * Copyright 2026 Tejas Thimmaiah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.thimmaiah.ebors

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Foreground service that keeps web audio/video alive while the app is
 * backgrounded and exposes a full media notification (previous / play-pause /
 * next, artwork, and a seek bar) on the shade and lock screen.
 *
 * The service owns the [MediaSession] and the audio focus request. The actual
 * media element lives in a WebView inside [MainActivity]; transport actions
 * are forwarded back through [transportCallback], which invokes the page's own
 * `navigator.mediaSession` handlers (or the `<video>`/`<audio>` element) via
 * JavaScript. The matching half — masking the Page Visibility API and forcing
 * the WebView to report window-visible so Chromium doesn't suspend background
 * media — lives on the activity / WebView.
 */
class MediaPlaybackService : Service() {

    private lateinit var session: MediaSession
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val http by lazy { OkHttpClient() }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var title: String = ""
    private var artist: String = ""
    private var playing: Boolean = true
    private var durationMs: Long = 0
    private var positionMs: Long = 0

    private var artworkUrl: String = ""
    private var artworkBitmap: Bitmap? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> transportCallback?.onPause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createNotificationChannel()
        session = MediaSession(this, "ebors-media").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    requestFocus()
                    transportCallback?.onPlay()
                }

                override fun onPause() {
                    transportCallback?.onPause()
                }

                override fun onSkipToNext() {
                    transportCallback?.onNext()
                }

                override fun onSkipToPrevious() {
                    transportCallback?.onPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    transportCallback?.onSeekTo(pos)
                }

                override fun onStop() {
                    transportCallback?.onStop()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                transportCallback?.onStop()
                stopPlayback()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                transportCallback?.onPause()
                playing = false
            }

            ACTION_PLAY -> {
                requestFocus()
                transportCallback?.onPlay()
                playing = true
            }

            ACTION_NEXT -> transportCallback?.onNext()

            ACTION_PREV -> transportCallback?.onPrevious()

            else -> {
                intent?.getStringExtra(EXTRA_TITLE)?.let { title = it }
                intent?.getStringExtra(EXTRA_ARTIST)?.let { artist = it }
                durationMs = intent?.getLongExtra(EXTRA_DURATION, durationMs) ?: durationMs
                positionMs = intent?.getLongExtra(EXTRA_POSITION, positionMs) ?: positionMs
                playing = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true
                if (playing) requestFocus()
                fetchArtwork(intent?.getStringExtra(EXTRA_ARTWORK).orEmpty())
            }
        }

        updateSession()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        abandonFocus()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopPlayback() {
        playing = false
        abandonFocus()
        session.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun updateSession() {
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs, if (playing) 1f else 0f)
                .build(),
        )
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
        if (durationMs > 0) metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        artworkBitmap?.let { metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(metadata.build())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val toggle = if (playing) {
            action(android.R.drawable.ic_media_pause, R.string.media_pause, ACTION_PAUSE)
        } else {
            action(android.R.drawable.ic_media_play, R.string.media_play, ACTION_PLAY)
        }

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_note)
            .setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .setDeleteIntent(servicePendingIntent(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(action(android.R.drawable.ic_media_previous, R.string.media_previous, ACTION_PREV))
            .addAction(toggle)
            .addAction(action(android.R.drawable.ic_media_next, R.string.media_next, ACTION_NEXT))
            .setStyle(mediaStyle)

        artworkBitmap?.let { builder.setLargeIcon(Icon.createWithBitmap(it)) }
        return builder.build()
    }

    private fun action(icon: Int, titleRes: Int, intentAction: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            getString(titleRes),
            servicePendingIntent(intentAction),
        ).build()

    /**
     * Fetch cover art off the main thread and refresh the session/notification
     * once it arrives. No-ops when the URL is unchanged so we don't re-download
     * on every position tick.
     */
    private fun fetchArtwork(url: String) {
        if (url == artworkUrl) return
        artworkUrl = url
        if (url.isBlank()) {
            artworkBitmap = null
            return
        }
        http.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val bytes = response.use { it.body?.bytes() } ?: return
                val bitmap = decodeSampled(bytes, MAX_ARTWORK_PX) ?: return
                mainHandler.post {
                    // Drop stale results if the track changed mid-download.
                    if (url != artworkUrl) return@post
                    artworkBitmap = bitmap
                    updateSession()
                    runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification()) }
                }
            }
        })
    }

    private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > maxDim) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } catch (_: Exception) {
        null
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.media_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    /** Forwards lock-screen / notification transport actions to the page. */
    interface Transport {
        fun onPlay()
        fun onPause()
        fun onStop()
        fun onNext()
        fun onPrevious()
        fun onSeekTo(positionMs: Long)
    }

    companion object {
        @Volatile
        var transportCallback: Transport? = null

        private const val CHANNEL_ID = "media_playback"
        private const val NOTIFICATION_ID = 6002
        private const val MAX_ARTWORK_PX = 600

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_PLAYING = "playing"

        private const val ACTION_PLAY = "me.thimmaiah.ebors.media.PLAY"
        private const val ACTION_PAUSE = "me.thimmaiah.ebors.media.PAUSE"
        private const val ACTION_NEXT = "me.thimmaiah.ebors.media.NEXT"
        private const val ACTION_PREV = "me.thimmaiah.ebors.media.PREV"
        private const val ACTION_STOP = "me.thimmaiah.ebors.media.STOP"

        /**
         * Start (or update) the foreground media notification. Safe to call
         * repeatedly; the service coalesces into a single notification.
         * Guarded against the Android 12+ background-start restriction —
         * the first play always happens while the activity is foreground,
         * so the service is already running by the time we go to the
         * background, but a stray background autoplay must not crash us.
         */
        fun update(
            context: Context,
            title: String,
            artist: String,
            artwork: String,
            durationMs: Long,
            positionMs: Long,
            playing: Boolean,
        ) {
            val intent = Intent(context, MediaPlaybackService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_ARTWORK, artwork)
                .putExtra(EXTRA_DURATION, durationMs)
                .putExtra(EXTRA_POSITION, positionMs)
                .putExtra(EXTRA_PLAYING, playing)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Background-start not allowed and service not yet running:
                // nothing we can legally do here, so drop it silently.
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, MediaPlaybackService::class.java).setAction(ACTION_STOP),
                )
            } catch (_: Exception) {
                // Service already gone.
            }
        }
    }
}
