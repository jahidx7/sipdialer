package com.noxsip.app.util

import android.content.Context

/**
 * Stores the global incoming SIP call auto-answer preference.
 * Auto-answer is disabled by default.
 */
object AutoAnswerPrefs {
    private const val PREFS_NAME = "sip_call_preferences"
    private const val KEY_AUTO_ANSWER = "auto_answer_incoming_sip_calls"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ANSWER, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ANSWER, enabled)
            .apply()
    }
}
