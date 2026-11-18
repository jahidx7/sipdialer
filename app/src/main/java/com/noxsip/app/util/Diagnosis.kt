package com.noxsip.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.noxsip.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Only caller-controlled event labels, SDK enums, and numeric response codes enter this journal. */
object Diagnosis {
    private val history = MutableStateFlow<List<String>>(emptyList())
    val events = history.asStateFlow()
    private const val limit = 500
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("sip_diagnosis", Context.MODE_PRIVATE)
    private val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)

    @Synchronized fun start(context: Context) {
        app = context.applicationContext
        history.value = prefs.getString("events", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(limit)
        record("App", "Started " + BuildConfig.VERSION_NAME)
    }

    private fun route(): String {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return "Unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "Other"
        }
    }

    @Synchronized fun record(kind: String, detail: String) {
        if (!::app.isInitialized) return
        val entry = listOf(clock.format(Date()), route(), kind, detail).joinToString(" | ")
        val rows = (history.value + entry).takeLast(limit)
        prefs.edit().putString("events", rows.joinToString("\n")).apply()
        history.value = rows
    }

    private val cseq = Regex("""(?im)^CSeq:[ \t]*[0-9]+[ \t]+(REGISTER|INVITE|ACK|CANCEL|BYE)\b""")
    private val response = Regex("""(?m)^SIP/2\.0[ \t]+([1-6][0-9]{2})\b""")

    fun signaling(message: String) {
        // SDK versions format packets differently. Record direction only when explicitly supplied.
        val direction = when {
            message.contains("bytes from", ignoreCase = true) -> "received"
            message.contains("bytes to", ignoreCase = true) -> "sent"
            else -> return
        }
        val method = cseq.find(message)?.groupValues?.get(1) ?: return
        val code = response.find(message)?.groupValues?.get(1)
        record("SIP", if (code == null) "$method request $direction" else "$method response $code $direction")
    }

    @Synchronized fun clear() {
        prefs.edit().remove("events").apply()
        history.value = emptyList()
    }

    fun summary(rows: List<String>): String = buildString {
        for (network in listOf("Wi-Fi", "Mobile")) {
            val observed = rows.filter { it.substringAfter(" | ").substringBefore(" | ") == network }
            val connected = observed.count { it.contains(" | Connected | ") }
            val incoming = observed.count { it.contains(" Incoming IncomingReceived;") }
            val dialed = observed.count { it.endsWith(" | Outgoing dial requested") }
            val failed = observed.count { it.contains(" | Call error | ") }
            appendLine("$network: $dialed dial requests, $incoming incoming events, $connected connected events, $failed call errors.")
            val lastCall = observed.lastOrNull { it.contains(" | Call | ") || it.contains(" | Connected | ") || it.contains(" | Call error | ") }
            if (lastCall != null) appendLine("Last call event: " + lastCall.substringAfterLast(" | "))
            observed.lastOrNull { it.contains(" | Call error | ") || it.contains(" | Registration error | ") }
                ?.let { appendLine("Latest error: " + it.substringAfterLast(" | ")) }
        }
        append("Events are measured on this phone. An incoming request that never arrived needs the provider’s call trace.")
    }
}
