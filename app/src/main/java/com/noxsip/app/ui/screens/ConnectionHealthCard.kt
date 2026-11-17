package com.noxsip.app.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.data.models.SipAccountData
import com.noxsip.app.sip.SipRegistrationState
import com.noxsip.app.util.SipConnectionDiagnostics
import com.noxsip.app.util.SipConnectionSnapshot
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConnectionHealthCard(
    currentAccount: SipAccountData?,
    registrationState: SipRegistrationState,
    registrationError: String?,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var snapshot by remember(currentAccount?.id) { mutableStateOf(SipConnectionSnapshot()) }

    LaunchedEffect(currentAccount?.id) {
        while (true) {
            now = System.currentTimeMillis()
            snapshot = currentAccount?.let { SipConnectionDiagnostics.snapshot(context, it.id) }
                ?: SipConnectionSnapshot()
            delay(1_000L)
        }
    }

    val connected = registrationState == SipRegistrationState.REGISTERED
    val network = networkLabel(context)
    val route = if (snapshot.server.isNotBlank()) "${snapshot.server}:${snapshot.port} (${snapshot.transport})"
        else currentAccount?.let { "${it.domain}:${it.port} (${it.transport})" } ?: "No SIP account configured"
    val retryRemaining = ((snapshot.nextRetryAt - now).coerceAtLeast(0L) / 1_000L)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        if (connected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SIP registration", fontWeight = FontWeight.SemiBold)
                        Text(
                            when (registrationState) {
                                SipRegistrationState.REGISTERED -> "Registered and ready"
                                SipRegistrationState.REGISTERING -> "Connecting or retrying"
                                SipRegistrationState.ERROR -> "Needs attention"
                                SipRegistrationState.UNREGISTERED -> "Not registered"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (snapshot.usingBackup) Text("BACKUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
                HealthRow("Network", network)
                HealthRow("Active SIP endpoint", route)
                HealthRow("Last attempt", formatTime(snapshot.lastAttemptAt))
                HealthRow("Last connected", formatTime(snapshot.lastSuccessAt))
                if (snapshot.nextRetryAt > now) {
                    HealthRow("Next retry", "in ${retryRemaining}s • attempt ${snapshot.retryAttempt}")
                }
                val error = registrationError?.takeIf { it.isNotBlank() } ?: snapshot.lastError
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (!connected && network.contains("internet available", ignoreCase = true)) {
                    Text(
                        "Internet is available, but SIP registration is separate. A 503 response means the SIP server replied that it is temporarily unavailable.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onRetry,
                    enabled = currentAccount != null && registrationState != SipRegistrationState.REGISTERING,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("  Retry registration")
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Incoming-call push", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Push wake-up is not configured. It requires RFC 8599 support on the SIP server, an FCM push gateway, and app-side Firebase setup. This build detects Wi-Fi/mobile-data handovers and re-registers while its foreground service is running; push is needed to wake the app if Android stops that service.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

private fun formatTime(value: Long): String = if (value <= 0L) "No record" else
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(value))

private fun networkLabel(context: Context): String {
    return try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return "No active network"
        val caps = manager.getNetworkCapabilities(network) ?: return "Network status unavailable"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return "Connected • internet not verified"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi • internet available"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data • internet available"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet • internet available"
            else -> "Internet available"
        }
    } catch (_: Exception) {
        "Network status unavailable"
    }
}
