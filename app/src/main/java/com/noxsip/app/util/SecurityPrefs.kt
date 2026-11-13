package com.noxsip.app.util

import android.content.Context

/** নিরাপত্তা সেটিং: TLS সার্টিফিকেট যাচাই ও ভয়েস এনক্রিপশন (SRTP) */
object SecurityPrefs {
    private const val PREFS = "security_prefs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** TLS ট্রান্সপোর্টে সার্ভারের সার্টিফিকেট ও নাম যাচাই (ডিফল্ট চালু) */
    fun verifyTls(context: Context): Boolean = prefs(context).getBoolean("verify_tls", true)
    fun setVerifyTls(context: Context, v: Boolean) = prefs(context).edit().putBoolean("verify_tls", v).apply()

    /** সার্ভার সমর্থন করলে ভয়েস SRTP দিয়ে এনক্রিপ্ট (ডিফল্ট বন্ধ; কিছু গেটওয়ে SRTP অফার নেয় না) */
    fun srtp(context: Context): Boolean = prefs(context).getBoolean("srtp", false)
    fun setSrtp(context: Context, v: Boolean) = prefs(context).edit().putBoolean("srtp", v).apply()
}
