package com.noxsip.app.service

import android.content.Context

/** ব্যবহারকারী নিজে সার্ভিস বন্ধ করেছেন কি না। বন্ধ না করে থাকলে বুট, আপডেট বা সোয়াইপের পর সার্ভিস নিজে ফিরে আসে */
object KeepAliveState {
    private const val PREFS = "keepalive_state"
    private const val K_STOPPED = "stopped_by_user"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stoppedByUser(c: Context): Boolean = p(c).getBoolean(K_STOPPED, false)

    fun setStoppedByUser(c: Context, v: Boolean) {
        p(c).edit().putBoolean(K_STOPPED, v).apply()
    }
}
