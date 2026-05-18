package me.thimmaiah.effectivebrowser

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * Resolve a theme-attribute reference to a concrete ARGB colour
 * against the receiver's current theme. Used by callers that need
 * a colour value at runtime (e.g. constructing a `ForegroundColorSpan`
 * or feeding `SwipeRefreshLayout.setColorSchemeColors`) and don't
 * want to plumb a Resources.Theme instance through.
 */
@ColorInt
fun Context.resolveThemeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}
