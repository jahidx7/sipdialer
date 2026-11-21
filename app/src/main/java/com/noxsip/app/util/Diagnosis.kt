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

    private fun networkSnapshot(): String {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return "active=unknown"
        val network = cm.activeNetwork ?: return "active=offline; internet=false; validated=false"
        val caps = cm.getNetworkCapabilities(network) ?: return "active=unknown; capabilities=unavailable"
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
        return "active=$type; internet=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}; validated=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}; metered=${!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}"
    }

    @Synchronized fun recordNetworkChange() {
        if (!::app.isInitialized) return
        record("Network", "Default route changed; ${networkSnapshot()}")
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
        val seenNetworks = rows.map { it.substringAfter(" | ").substringBefore(" | ") }.distinct()
        val networks = listOf("Wi-Fi", "Mobile", "VPN", "Other", "Offline", "Unknown").filter { it in seenNetworks }
        for (network in networks) {
            val observed = rows.filter { it.substringAfter(" | ").substringBefore(" | ") == network }
            val connected = observed.count { it.contains(" | Connected | ") }
            val incoming = observed.count { it.contains(" Incoming IncomingReceived;") }
            val dialed = observed.count { it.endsWith(" | Outgoing dial requested") }
            val failed = observed.count { it.contains(" | Call error | ") }
            val registerOk = observed.count { it.contains(" | Registration | Ok;") }
            val registerErrors = observed.count { it.contains(" | Registration error | ") }
            appendLine("$network: $registerOk successful registrations, $registerErrors registration errors, $dialed dial requests, $incoming incoming calls observed, $connected connected calls, $failed call errors.")
            val lastCall = observed.lastOrNull { it.contains(" | Call | ") || it.contains(" | Connected | ") || it.contains(" | Call error | ") }
            if (lastCall != null) appendLine("Last call event: " + lastCall.substringAfterLast(" | "))
            observed.lastOrNull { it.contains(" | Call error | ") || it.contains(" | Registration error | ") }
                ?.let { appendLine("Latest error: " + it.substringAfterLast(" | ")) }
            val lastRegisterResponse = observed.lastOrNull { it.contains(" | SIP | REGISTER response ") }
            if (lastRegisterResponse != null) appendLine("Last REGISTER response: " + lastRegisterResponse.substringAfter(" | SIP | "))
        }
        if (networks.isEmpty()) appendLine("No network events recorded yet.")
        append("Events are measured on this phone. 401 is usually an authentication challenge; a following 200 confirms registration. A missing incoming request needs the provider’s call trace. Registration success does not prove call audio/media works.")
    }

    /** Rule-based observations from locally observed events; it never claims to see provider-side traffic. */
    fun insights(rows: List<String>): List<Pair<String, String>> {
        if (rows.isEmpty()) return listOf("Info" to "No SIP or network events have been recorded yet. Reproduce the issue with this app open, then copy the report.")
        fun net(name: String) = rows.filter { it.contains(" | $name | ") }
        val networks = listOf("Wi-Fi", "Mobile", "VPN").associateWith(::net)
        val out = mutableListOf<Pair<String, String>>()
        val wifi = networks["Wi-Fi"].orEmpty()
        val mobile = networks["Mobile"].orEmpty()
        val vpn = networks["VPN"].orEmpty()
        fun ok(events: List<String>) = events.count { it.contains(" | Registration | Ok;") }
        fun errors(events: List<String>) = events.count { it.contains(" | Registration error | ") }
        if (ok(wifi) > 0 && ok(mobile) == 0 && errors(mobile) > 0) {
            out += "Check" to "Wi-Fi registered successfully, while mobile data recorded ${errors(mobile)} registration errors and no successful registration. This points to a network-path or SIP transport reachability difference; it does not indicate that Android blocked all app internet access."
        }
        if (mobile.any { it.contains("validated=false") }) {
            out += "Check" to "Android reported a mobile-data default route without validated internet during at least one handover. Confirm mobile data is active and the carrier has working internet coverage at the time of the test."
        } else if (mobile.any { it.contains("validated=true") } && errors(mobile) > 0 && ok(mobile) == 0) {
            out += "Check" to "Android reported validated mobile internet, but the phone recorded no successful SIP registration on mobile data. The failure is likely specific to the SIP server path/port/transport or its response, rather than the app having no general internet route."
        }
        for ((network, events) in networks) {
            val timeouts = events.count { it.contains("No response before local registration timer") }
            if (timeouts >= 2 && ok(events) == 0) out += "Check" to "$network has repeated SIP registration timeouts with no observed successful registration. Ask the SIP provider to verify whether its server/port accepts this network and transport."
            else if (timeouts >= 2 && ok(events) > 0) out += "Check" to "$network registered at least once but also had repeated registration timeouts; connectivity appears intermittent."
        }
        if (vpn.isNotEmpty() && ok(vpn) > 0) out += "Info" to "VPN registration succeeded at least once. That confirms SIP signaling registration over VPN at that time only; voice media and completed calls need separate call events."
        val responseCodes = rows.mapNotNull { Regex("SIP (?:REGISTER|INVITE) response ([1-6][0-9]{2})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (401 in responseCodes || 407 in responseCodes) out += "Info" to "Authentication challenges (401/407) were observed. They are normally expected when followed by a successful authenticated request (often 200)."
        if (480 in responseCodes) out += "Check" to "An INVITE received SIP 480 (Temporarily Unavailable); the request reached a SIP responder, which reported the destination unavailable. Provider routing or destination status may need checking."
        if (rows.any { it.contains("code=408") || it.contains("NotAnswered") }) out += "Check" to "A request timed out or was not answered (408/NotAnswered). The timeline shows whether this occurred during registration or a call."
        val incoming = rows.count { it.contains(" Incoming IncomingReceived;") }
        val dial = rows.count { it.endsWith(" | Outgoing dial requested") }
        if (dial > 0 && incoming == 0) out += "Info" to "No incoming INVITE was observed by this phone in the saved timeline. If the caller reports ringing, the provider’s inbound call trace is needed to locate where delivery stopped."
        if (out.isEmpty()) out += "Info" to "No repeated registration or call-failure pattern was detected in the saved events. Keep the timeline running while reproducing the issue."
        return out.distinct()
    }

    /** Full copyable report: device-observed connectivity, analysis and every retained event. */
    fun report(context: Context, rows: List<String>): String = buildString {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val route = when {
            caps == null -> "Offline / unavailable"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "Other"
        }
        appendLine("NoxSIP Lite ${BuildConfig.VERSION_NAME} · Full diagnosis report")
        appendLine("Current Android route: $route")
        appendLine("Internet capability: ${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true}; validated by Android: ${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true}; metered: ${caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: "unknown"}")
        appendLine("Saved events: ${rows.size} / $limit; newest first")
        appendLine()
        appendLine("ANALYSIS")
        insights(rows).forEach { (kind, text) -> appendLine("[$kind] $text") }
        appendLine()
        appendLine("NETWORK SUMMARY")
        appendLine(summary(rows))
        appendLine()
        appendLine("COMPLETE EVENT TIMELINE")
        if (rows.isEmpty()) appendLine("No events recorded yet.") else rows.asReversed().forEach(::appendLine)
    }
}
