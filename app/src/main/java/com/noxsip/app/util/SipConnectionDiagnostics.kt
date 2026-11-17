package com.noxsip.app.util

import android.content.Context

/** Local, credential-free connection history for the Connection Health screen. */
data class SipConnectionSnapshot(
    val lastAttemptAt: Long = 0L,
    val lastSuccessAt: Long = 0L,
    val nextRetryAt: Long = 0L,
    val lastError: String = "",
    val server: String = "",
    val port: Int = 5060,
    val transport: String = "UDP",
    val retryAttempt: Int = 0,
    val usingBackup: Boolean = false
)

object SipConnectionDiagnostics {
    private const val PREFS = "sip_connection_diagnostics"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(id: String, field: String) = "$id.$field"

    fun endpoint(context: Context, id: String, server: String, port: Int, transport: String, backup: Boolean) {
        prefs(context).edit()
            .putString(key(id, "server"), server)
            .putInt(key(id, "port"), port)
            .putString(key(id, "transport"), transport)
            .putBoolean(key(id, "backup"), backup)
            .apply()
    }

    fun attempted(context: Context, id: String) {
        prefs(context).edit()
            .putLong(key(id, "attempt"), System.currentTimeMillis())
            .putLong(key(id, "retryAt"), 0L)
            .apply()
    }

    fun registered(context: Context, id: String) {
        prefs(context).edit()
            .putLong(key(id, "success"), System.currentTimeMillis())
            .putLong(key(id, "retryAt"), 0L)
            .putInt(key(id, "retryCount"), 0)
            .putString(key(id, "error"), "")
            .apply()
    }

    fun failed(context: Context, id: String, message: String) {
        prefs(context).edit().putString(key(id, "error"), message.take(500)).apply()
    }

    fun retryScheduled(context: Context, id: String, at: Long, attempt: Int) {
        prefs(context).edit()
            .putLong(key(id, "retryAt"), at)
            .putInt(key(id, "retryCount"), attempt)
            .apply()
    }

    fun snapshot(context: Context, id: String): SipConnectionSnapshot {
        val p = prefs(context)
        return SipConnectionSnapshot(
            lastAttemptAt = p.getLong(key(id, "attempt"), 0L),
            lastSuccessAt = p.getLong(key(id, "success"), 0L),
            nextRetryAt = p.getLong(key(id, "retryAt"), 0L),
            lastError = p.getString(key(id, "error"), "").orEmpty(),
            server = p.getString(key(id, "server"), "").orEmpty(),
            port = p.getInt(key(id, "port"), 5060),
            transport = p.getString(key(id, "transport"), "UDP").orEmpty(),
            retryAttempt = p.getInt(key(id, "retryCount"), 0),
            usingBackup = p.getBoolean(key(id, "backup"), false)
        )
    }
}
