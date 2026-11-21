package com.noxsip.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.sip.AutoCallController
import com.noxsip.app.sip.AutoCallItem
import com.noxsip.app.sip.AutoCallItemStatus
import com.noxsip.app.sip.SipRegistrationState
import com.noxsip.app.util.AutoCallPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.text.DateFormat
import java.util.Date

@Composable
fun AutoCallScreen(
    controller: AutoCallController,
    registrationState: SipRegistrationState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress by controller.progress.collectAsState()
    var numbersText by rememberSaveable { mutableStateOf(AutoCallPrefs.numbers(context).joinToString("\n")) }
    var importedNumbers by remember { mutableStateOf<List<String>?>(null) }
    var importSummary by remember { mutableStateOf<AutoCallPrefs.ImportResult?>(null) }
    var voiceName by remember { mutableStateOf(AutoCallPrefs.voiceName(context)) }
    var importing by remember { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var showStartConfirmation by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var queuedRetryStart by remember { mutableStateOf(false) }
    var pendingStartFromFile by remember { mutableStateOf(false) }
    val pendingStartNumbers = remember { mutableStateOf<List<String>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val manualNumbers = remember(numbersText) { AutoCallPrefs.parseNumbers(numbersText) }
    val voicePath = AutoCallPrefs.voicePath(context)
    val voiceDurationMs = remember(voicePath) { voicePath?.let(AutoCallPrefs::voiceDurationMs) }
    val voiceReady = voiceDurationMs != null
    val newQueueNumbers = importedNumbers ?: manualNumbers
    val canPrepareQueue = !importing && !progress.running && newQueueNumbers.isNotEmpty() && voiceReady && consent

    val numberPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { AutoCallPrefs.importNumbers(context, uri) }
                    importedNumbers = result.numbers
                    importSummary = result
                    numbersText = ""
                    Toast.makeText(context, "Scanned ${result.scannedValues}; found ${result.numbers.size} unique Bangladesh numbers", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    importedNumbers = null
                    importSummary = null
                    Toast.makeText(context, error.message ?: "Could not scan this file", Toast.LENGTH_LONG).show()
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
        AutoCallPrefs.saveNumbers(context, manualNumbers)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(42.dp).padding(1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Auto Call", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Sequential SIP campaign", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
                            Text("${newQueueNumbers.size}/50", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        "Calls one number at a time. The selected recording plays after the recipient answers and SIP audio is ready. No-answer calls are skipped after 45 seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("The handset microphone is muted during playback. Call only recipients who agreed to receive this recording.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            OutlinedTextField(
                value = numbersText,
                onValueChange = {
                    numbersText = it
                    importedNumbers = null
                    importSummary = null
                },
                enabled = !progress.running && importedNumbers == null,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                label = { Text("Type or paste numbers (max 50)") },
                supportingText = {
                    Column {
                        Text("Bangladesh format: +8801XXXXXXXXX or 01XXXXXXXXX • Valid: ${manualNumbers.size}/50")
                        Text("If you enter more than 50, only the first 50 distinct valid numbers are used.", style = MaterialTheme.typography.labelSmall)
                    }
                },
                placeholder = { Text("+88017XXXXXXXX\n017XXXXXXXX") }
            )
        }

        item {
            androidx.compose.material3.Button(
                onClick = { numberPicker.launch(arrayOf("*/*")) },
                enabled = !progress.running && !importing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                if (importing) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                else androidx.compose.material3.Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Text(if (importing) "  Scanning file…" else "  Import contact list")
            }
        }

        importSummary?.let { summary ->
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(summary.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("Scanned ${summary.scannedValues} values • Found ${summary.numbers.size} unique mobile numbers", style = MaterialTheme.typography.bodySmall)
                        Text("Duplicates: ${summary.duplicates} • Invalid or non-mobile values: ${summary.invalidValues}", style = MaterialTheme.typography.bodySmall)
                        Text("No recipient-count cap for file imports. File size is limited to 12 MB. Supports .xlsx, CSV, TSV, TXT and readable text files; only +8801… or 01… Bangladesh mobile numbers are queued.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { importedNumbers = null; importSummary = null }, enabled = !progress.running) { Text("Remove imported list") }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Icon(Icons.Filled.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.weight(1f)) {
                            Text("Recorded voice", fontWeight = FontWeight.SemiBold)
                            Text(voiceName ?: "Choose an MP3 or WAV file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        if (voiceReady) Surface(shape = RoundedCornerShape(50), color = Color(0xFF1F8D62).copy(alpha = .14f)) {
                            Text("Ready", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4FC18A), fontWeight = FontWeight.SemiBold)
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
        }

        if (registrationState != SipRegistrationState.REGISTERED) {
            item {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f)) {
                    Text("SIP account is not registered. Connect an account before starting.", modifier = Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !progress.running)
                    Text("I confirm every recipient agreed to receive this recorded call.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress.running) {
                    OutlinedButton(onClick = { controller.stop() }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp)) {
                        androidx.compose.material3.Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("  Stop queue")
                    }
                } else {
                    if (progress.readyCount > 0) {
                        Button(
                            onClick = { queuedRetryStart = true; showStartConfirmation = true },
                            enabled = !importing && voicePath != null && voiceReady && consent,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            androidx.compose.material3.Icon(Icons.Filled.Refresh, contentDescription = null)
                            Text("  Start queued retries (${progress.readyCount})")
                        }
                    }
                    Button(
                        onClick = {
                            pendingStartNumbers.value = newQueueNumbers
                            pendingStartFromFile = importedNumbers != null
                            queuedRetryStart = false
                            showStartConfirmation = true
                        },
                        enabled = canPrepareQueue,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("  Start ${newQueueNumbers.size} calls")
                    }
                }
                when {
                    importing -> Text("Preparing the selected file…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    newQueueNumbers.isEmpty() -> Text("Add at least one valid Bangladesh mobile number to continue.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    voicePath == null -> Text("Choose an audio recording before starting.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    !voiceReady -> Text("The saved recording could not be read. Choose the audio file again.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    !consent -> Text("Confirm recipient consent to enable recorded calls.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    registrationState != SipRegistrationState.REGISTERED -> Text("The SIP account is not currently registered. Start will report the live SIP state before dialing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                if (progress.items.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearConfirmation = true },
                        enabled = !progress.running,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Text("  Clear call queue and report")
                    }
                }
            }
        }

        if (progress.message.isNotBlank()) item {
            Text(progress.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        if (progress.items.isNotEmpty()) item {
            Text("Call report • ${progress.items.size} attempts • ${progress.completedCount} processed • ${progress.answeredCount} answered • ${progress.unansweredCount} not answered", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        items(progress.items, key = { it.id }) { item ->
            AutoCallReportCard(
                item = item,
                isCurrent = progress.running && item.id == progress.currentId,
                enabled = !progress.running,
                onRetry = {
                    val error = controller.queueRetry(item.id)
                    if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    if (showStartConfirmation) {
        val queuedCount = if (queuedRetryStart) progress.readyCount else pendingStartNumbers.value.size
        AlertDialog(
            onDismissRequest = { showStartConfirmation = false },
            title = { Text(if (queuedRetryStart) "Start retry queue?" else "Confirm Auto Call") },
            text = { Text("This will place up to $queuedCount sequential recorded SIP calls. Continue only if every number belongs to a recipient who agreed to receive this call.") },
            confirmButton = {
                TextButton(onClick = {
                    showStartConfirmation = false
                    val error = if (voicePath == null) "Choose a voice recording first."
                    else if (queuedRetryStart) controller.startQueued(voicePath)
                    else controller.start(pendingStartNumbers.value, voicePath, importedFromFile = pendingStartFromFile)
                    if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    else {
                        numbersText = ""
                        importedNumbers = null
                        importSummary = null
                        consent = false
                    }
                }) { Text("Start calls") }
            },
            dismissButton = { TextButton(onClick = { showStartConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear call report?") },
            text = { Text("This removes the saved queue and all call results from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    val error = controller.clearReport()
                    if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AutoCallReportCard(item: AutoCallItem, isCurrent: Boolean, enabled: Boolean, onRetry: () -> Unit) {
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.number, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(item.status.label(), color = statusColor(item.status), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Attempt ${item.attempt} • ${if (item.answered) "Answered" else "Not answered"} • Call ${item.callDurationSeconds}s • Voice ${"%.1f".format(Locale.US, item.voicePlayedMillis / 1000.0)}s", style = MaterialTheme.typography.bodySmall)
            if (item.startedAtMillis > 0L) Text("Started: ${formatReportTime(item.startedAtMillis)}${if (item.finishedAtMillis > 0L) " • Finished: ${formatReportTime(item.finishedAtMillis)}" else ""}", style = MaterialTheme.typography.bodySmall)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.endedBy.isNotBlank() || item.sipCode > 0 || item.terminationReason.isNotBlank()) {
                Text("Hangup source reported by SIP: ${item.endedBy.ifBlank { "Not reported" }}${if (item.sipCode > 0) " • SIP ${item.sipCode}" else ""}${if (item.terminationReason.isNotBlank()) " • Reason: ${item.terminationReason}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.status.canRetry() && enabled) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("  Add to retry queue")
                }
            }
        }
    }
}

private fun formatReportTime(timeMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timeMillis))

private fun AutoCallItemStatus.label(): String = when (this) {
    AutoCallItemStatus.READY -> "Queued"
    AutoCallItemStatus.CALLING -> "Calling"
    AutoCallItemStatus.PLAYING -> "Playing voice"
    AutoCallItemStatus.ANSWERED -> "Answered"
    AutoCallItemStatus.NO_ANSWER -> "No answer"
    AutoCallItemStatus.REJECTED -> "Rejected"
    AutoCallItemStatus.BUSY -> "Busy"
    AutoCallItemStatus.FAILED -> "Failed"
    AutoCallItemStatus.STOPPED -> "Stopped"
}

private fun AutoCallItemStatus.canRetry(): Boolean = this in setOf(
    AutoCallItemStatus.NO_ANSWER,
    AutoCallItemStatus.REJECTED,
    AutoCallItemStatus.BUSY,
    AutoCallItemStatus.FAILED,
    AutoCallItemStatus.STOPPED
)

@Composable
private fun statusColor(status: AutoCallItemStatus) = when (status) {
    AutoCallItemStatus.ANSWERED -> MaterialTheme.colorScheme.primary
    AutoCallItemStatus.BUSY, AutoCallItemStatus.NO_ANSWER -> MaterialTheme.colorScheme.tertiary
    AutoCallItemStatus.REJECTED, AutoCallItemStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
