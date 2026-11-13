package com.noxsip.app.beta

import android.content.Context

/** Beta: SIP কল ফরোয়ার্ডিংয়ের সেটিংস (অ্যাপ চালু থাকলে কাজ করে; SIM ফরোয়ার্ডিং অপারেটরের নেটওয়ার্কে হয়) */
object CallForward {
    const val OFF = 0
    const val ALL = 1
    const val BUSY = 2
    const val NO_ANSWER = 3

    private const val PREFS = "beta_call_forward"
    private const val K_MODE = "sip_mode"
    private const val K_NUMBER = "sip_number"
    private const val K_SECONDS = "sip_seconds"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(c: Context): Int = p(c).getInt(K_MODE, OFF)
    fun number(c: Context): String = p(c).getString(K_NUMBER, "").orEmpty()
    fun seconds(c: Context): Int = p(c).getInt(K_SECONDS, 20)

    fun save(c: Context, mode: Int, number: String, seconds: Int) {
        p(c).edit()
            .putInt(K_MODE, mode)
            .putString(K_NUMBER, number.filter { it.isDigit() || it == '+' })
            .putInt(K_SECONDS, seconds.coerceIn(5, 60))
            .apply()
    }

    /** সক্রিয় (মোড চালু এবং নম্বর আছে) হলে নম্বর, নইলে null */
    fun activeNumber(c: Context): String? =
        number(c).takeIf { mode(c) != OFF && it.isNotBlank() }
}
