package com.noxsip.app.util

import android.content.Context

/**
 * ব্যাটারি/পাওয়ার মোড।
 * RELIABLE: CPU সারাক্ষণ জাগানো থাকে (ইনকামিং কল সবচেয়ে নির্ভরযোগ্য, ব্যাটারি বেশি খায়)।
 * BALANCED: CPU ঘুমায়; প্রতি ৩০ সেকেন্ডে Alarm দিয়ে অল্প সময় জেগে রেজিস্ট্রেশন তাজা রাখে (Zoiper-এর "Alarm Scheduler"-এর মতো)।
 */
object PowerPrefs {
    const val RELIABLE = "reliable"
    const val BALANCED = "balanced"
    private const val PREFS = "power_prefs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): String {
        val p = prefs(context)
        return when (val m = p.getString("mode", BALANCED)) {
            RELIABLE, BALANCED -> m
            else -> {
                // পুরনো "stealth" (নোটিফিকেশনবিহীন) মোড সরানো হয়েছে: Balanced-এ নিয়ে যাই
                p.edit().putString("mode", BALANCED).remove("stealth_active").apply()
                BALANCED
            }
        }
    }

    fun setMode(context: Context, mode: String) = prefs(context).edit().putString("mode", mode).apply()
}
