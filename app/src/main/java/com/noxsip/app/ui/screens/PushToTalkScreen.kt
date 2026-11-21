package com.noxsip.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noxsip.app.ptt.PttLanEngine

@Composable
fun PushToTalkScreen() {
    val context = LocalContext.current
    val engine = androidx.compose.runtime.remember { PttLanEngine.getInstance(context) }
    val peers by engine.peersState.collectAsStateWithLifecycle()
    val status by engine.status.collectAsStateWithLifecycle()
    val speaker by engine.speaker.collectAsStateWithLifecycle()
    val transmitting by engine.transmitting.collectAsStateWithLifecycle()
    var hasMicPermission by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }

    DisposableEffect(engine) {
        engine.acquireScreenUse()
        onDispose { engine.releaseScreenUse() }
    }

    val talking = speaker != null
    val tone = when {
        transmitting -> MaterialTheme.colorScheme.primary
        talking -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Push to Talk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Same Wi-Fi network", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = if (status == "Local network ready") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "${peers.size} nearby",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(status, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Hold here to send. When SIP service is running, this phone can receive in the background. Use a trusted Wi-Fi; nearby NoxSIP devices can hear live voice. Background listening may use extra battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status.startsWith("Connect to Wi-Fi") || status.startsWith("PTT unavailable")) {
                    TextButton(onClick = { engine.start() }) { Text("Retry connection") }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            when {
                transmitting -> "You are talking…"
                speaker != null -> "${speaker} is talking…"
                else -> "Hold to talk"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (talking) tone else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(216.dp)
                .clip(CircleShape)
                .background(if (transmitting) MaterialTheme.colorScheme.tertiary else tone)
                .border(8.dp, tone.copy(alpha = 0.18f), CircleShape)
                .pointerInput(hasMicPermission, status, speaker) {
                    androidx.compose.foundation.gestures.detectTapGestures(onPress = {
                        if (!hasMicPermission) {
                            permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }
                        if (speaker == null || speaker == "You") engine.beginTransmit()
                        tryAwaitRelease()
                        engine.endTransmit()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Icon(
                    imageVector = if (transmitting) Icons.Filled.Mic else if (talking) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Hold to talk",
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    if (transmitting) "SPEAKING" else "HOLD TO TALK",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Release the button to finish", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Devices on this network", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (peers.isEmpty()) {
                    Text("No other NoxSIP device found yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    peers.forEach { peer ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(peer.name, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                            if (speaker == peer.name) {
                                Spacer(Modifier.weight(1f))
                                Text("Speaking", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
        if (!hasMicPermission) {
            TextButton(onClick = { permissionRequest.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Allow microphone access")
            }
        }
    }
}
