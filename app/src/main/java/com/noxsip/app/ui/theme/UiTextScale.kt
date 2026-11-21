package com.noxsip.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * পুরো অ্যাপের লেখার আকারের গুণক (সব sp-ভিত্তিক টেক্সটে প্রযোজ্য)।
 * Theme এটি Compose-এর LocalDensity.fontScale-এ গুণ করে বসায়, তাই প্রতিটি স্ক্রিনে আলাদা করে ফন্ট বদলাতে হয় না।
 */
object UiTextScale {
    private const val PREFS_NAME = "ui_prefs"
    private const val KEY_TEXT_SCALE = "text_scale"

    const val NORMAL = 1.0f
    const val LARGE = 1.15f
    const val EXTRA_LARGE = 1.3f

    var scale by mutableFloatStateOf(NORMAL)
        private set

    /** Application.onCreate-এ একবার ডাকুন */
    fun load(context: Context) {
        scale = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_TEXT_SCALE, NORMAL).coerceAtMost(NORMAL)
    }

    fun set(context: Context, value: Float) {
        scale = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_TEXT_SCALE, value).apply()
    }
}
