package com.noxsip.app.util

import android.content.Context

/**
 * ব্যাটারি/পাওয়ার মোড।
 * RELIABLE: background SIP registration বজায় রাখতে CPU জাগিয়ে রাখে; ব্যাটারি বেশি খরচ হয়।
 * BALANCED: কম ব্যাটারি খরচ করে, তবে Android Doze-এ নেটওয়ার্ক ও অ্যালার্ম দেরি হতে পারে।
 */
object PowerPrefs {
    const val RELIABLE = "reliable"
    const val BALANCED = "balanced"
    private const val PREFS = "power_prefs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): String {
        val p = prefs(context)
        return when (val m = p.getString("mode", RELIABLE)) {
            RELIABLE, BALANCED -> m
            else -> {
                // অজানা/পুরনো মোডকে নির্ভরযোগ্য ব্যাকগ্রাউন্ড ডিফল্টে স্থানান্তর
                p.edit().putString("mode", RELIABLE).remove("stealth_active").apply()
                RELIABLE
            }
        }
    }

    fun setMode(context: Context, mode: String) = prefs(context).edit().putString("mode", mode).apply()
}
