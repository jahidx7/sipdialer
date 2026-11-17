package com.noxsip.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the appearance choice while allowing Compose to react immediately. */
object ThemeModePrefs {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val PREFS = "ui_prefs"
    private const val KEY_MODE = "theme_mode"
    private val _mode = MutableStateFlow(SYSTEM)
    val mode = _mode.asStateFlow()

    fun load(context: Context) {
        _mode.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, SYSTEM)
            ?.takeIf { it in setOf(SYSTEM, LIGHT, DARK) } ?: SYSTEM
    }

    fun set(context: Context, mode: String) {
        val value = mode.takeIf { it in setOf(SYSTEM, LIGHT, DARK) } ?: SYSTEM
        _mode.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, value)
            .apply()
    }
}
