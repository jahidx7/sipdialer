package com.noxsip.app.util

import android.content.Context

/** Device-local speed-dial favorites, independent of phone-book write permission. */
object FavoriteNumbers {
    private const val PREFS = "speed_dial_favorites"
    private const val KEY_NUMBERS = "numbers"

    fun normalize(number: String): String = number.filter { it.isDigit() || it == '+' }

    fun get(context: Context): Set<String> = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_NUMBERS, emptySet())
        .orEmpty()
        .toSet()

    fun toggle(context: Context, number: String): Boolean {
        val normalized = normalize(number)
        if (normalized.isBlank()) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = prefs.getStringSet(KEY_NUMBERS, emptySet()).orEmpty().toMutableSet()
        val existing = values.firstOrNull { normalize(it) == normalized }
        val isNowFavorite = if (existing != null) {
            values.remove(existing)
            false
        } else {
            values.add(normalized)
            true
        }
        prefs.edit().putStringSet(KEY_NUMBERS, values).apply()
        return isNowFavorite
    }
}
