package com.noxsip.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noxsip.app.util.Diagnosis

@Composable
fun DiagnosisPanel() {
    val rows by Diagnosis.events.collectAsState()
    val context = LocalContext.current
    val summary = remember(rows) { Diagnosis.summary(rows) }
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Call summary", style = MaterialTheme.typography.titleMedium)
        Text(summary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = rows.isNotEmpty(), onClick = {
                val body = "NoxSIP Lite " + com.noxsip.app.BuildConfig.VERSION_NAME + "\n" + summary + "\n\n" + rows.joinToString("\n")
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Diagnosis", body))
            }) { Text("Copy report") }
            OutlinedButton(enabled = rows.isNotEmpty(), onClick = { confirm = true }) { Text("Clear report") }
        }
        Text("Recent events · newest first", style = MaterialTheme.typography.titleSmall)
        for (block in remember(rows) { rows.asReversed().chunked(20) }) {
            Text(block.joinToString("\n\n"), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Clear diagnosis history?") },
        text = { Text("New events will continue to appear.") },
        confirmButton = { TextButton(onClick = { Diagnosis.clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
