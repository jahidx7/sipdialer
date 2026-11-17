package com.noxsip.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.sip.AutoCallController
import com.noxsip.app.sip.AutoCallItemStatus
import com.noxsip.app.sip.SipRegistrationState
import com.noxsip.app.util.AutoCallPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutoCallScreen(controller: AutoCallController, registrationState: SipRegistrationState) {
    val context = LocalContext.current
    val progress by controller.progress.collectAsState()
    var numbersText by rememberSaveable { mutableStateOf(AutoCallPrefs.numbers(context).joinToString("\n")) }
    var voiceName by remember { mutableStateOf(AutoCallPrefs.voiceName(context)) }
    var importing by remember { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val numbers = remember(numbersText) { AutoCallPrefs.parseNumbers(numbersText) }
    val voicePath = AutoCallPrefs.voicePath(context)

    val numberPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            coroutineScope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) { AutoCallPrefs.importNumbers(context, uri) }
                    numbersText = imported.joinToString("\n")
                    AutoCallPrefs.saveNumbers(context, imported)
                    Toast.makeText(context, "Imported ${imported.size} numbers", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Could not read this file", Toast.LENGTH_LONG).show()
                } finally {
                    importing = false
                }
            }
        }
    }
    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            coroutineScope.launch {
                try {
                    voiceName = withContext(Dispatchers.IO) { AutoCallPrefs.importVoice(context, uri) }
                    Toast.makeText(context, "Auto Call voice is ready", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Could not prepare this audio", Toast.LENGTH_LONG).show()
                } finally {
                    importing = false
                }
            }
        }
    }

    LaunchedEffect(numbersText) {
        delay(300)
        AutoCallPrefs.saveNumbers(context, numbers)
    }

    // AccountSetupScreen already owns the settings scroll container. A second verticalScroll here
    // gives Compose unbounded nested scroll constraints and crashes as soon as Auto Call is opened.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Sequential SIP Auto Call", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Text(
                    "Calls one number at a time using the selected SIP account. When answered, the saved voice is sent into the call and the call ends after playback. Unanswered calls are skipped after 45 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("The handset microphone stays muted during playback. SIM/mobile-network calls are not used.", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = numbersText,
            onValueChange = { numbersText = it },
            enabled = !progress.running,
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            maxLines = 9,
            label = { Text("Phone numbers (up to 50)") },
            supportingText = { Text("Paste one number per line, or separate numbers with commas. Valid: ${numbers.size}/50") },
            placeholder = { Text("+8801XXXXXXXXX\n+8801XXXXXXXXX") }
        )
        OutlinedButton(
            onClick = { numberPicker.launch(arrayOf("*/*")) },
            enabled = !progress.running && !importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (importing) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
            else androidx.compose.material3.Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Text(if (importing) "  Importing…" else "  Import Excel / CSV / TXT")
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f)) {
                        Text("Recorded voice (MP3 / WAV)", fontWeight = FontWeight.SemiBold)
                        Text(voiceName ?: "Choose an MP3 or WAV file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { voicePicker.launch(arrayOf("audio/*")) }, enabled = !progress.running && !importing) {
                        Text(if (importing) "Preparing…" else "Choose audio")
                    }
                    if (voiceName != null && !progress.running) TextButton(onClick = {
                        AutoCallPrefs.clearVoice(context)
                        voiceName = null
                    }) { Text("Remove") }
                }
            }
        }

        if (registrationState != SipRegistrationState.REGISTERED) {
            Text("SIP account is not registered. Connect an account before starting.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !progress.running)
            Text("I confirm these recipients agreed to receive this recorded call.", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    AutoCallPrefs.saveNumbers(context, numbers)
                    val error = if (voicePath == null) "Choose a voice recording first." else controller.start(numbers, voicePath)
                    if (error == null) consent = false
                    else Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                },
                enabled = !progress.running && !importing && consent && numbers.isNotEmpty() && voicePath != null && registrationState == SipRegistrationState.REGISTERED,
                modifier = Modifier.weight(1f)
            ) {
                androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("  Start")
            }
            OutlinedButton(onClick = { controller.stop() }, enabled = progress.running, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Icon(Icons.Filled.Stop, contentDescription = null)
                Text("  Stop")
            }
        }

        if (progress.items.isNotEmpty()) {
            Text(progress.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("${progress.completedCount} of ${progress.items.size} processed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            progress.items.forEachIndexed { index, item ->
                val isCurrent = progress.running && index == progress.currentIndex
                Surface(
                    color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.number, modifier = Modifier.weight(1f), fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                        Text(item.detail.ifBlank { item.status.label() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun AutoCallItemStatus.label(): String = when (this) {
    AutoCallItemStatus.READY -> "Ready"
    AutoCallItemStatus.CALLING -> "Calling"
    AutoCallItemStatus.PLAYING -> "Playing"
    AutoCallItemStatus.NO_ANSWER -> "Skipped"
    AutoCallItemStatus.FAILED -> "Failed"
    AutoCallItemStatus.DONE -> "Done"
    AutoCallItemStatus.STOPPED -> "Stopped"
}
