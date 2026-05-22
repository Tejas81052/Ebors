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

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Pins the app's day/night mode to the user's manual "Dark mode"
 * preference instead of following the system theme. Set once per
 * process before any activity inflates, so the chosen mode drives
 * the very first frame. Settings re-applies it live when toggled.
 *
 * Default is dark (the [BrowserPreferences.forceDark] default), so a
 * fresh install lands in dark mode regardless of the system setting.
 */
class EborsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            if (BrowserPreferences.from(this).forceDark) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            },
        )
    }
}
