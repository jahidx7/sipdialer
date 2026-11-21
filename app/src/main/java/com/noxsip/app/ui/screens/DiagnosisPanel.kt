package com.noxsip.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.util.Diagnosis

private data class DiagnosisEvent(val raw: String, val time: String, val network: String, val type: String, val detail: String)

@Composable
fun DiagnosisPanel(modifier: Modifier = Modifier) {
    val rows by Diagnosis.events.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableStateOf("All") }
    var confirmClear by remember { mutableStateOf(false) }
    val report = remember(rows) { Diagnosis.summary(rows) + if (rows.isEmpty()) "\nNo events recorded yet." else "\n\n" + rows.joinToString("\n") }
    val parsed = remember(rows) {
        rows.asReversed().map { line ->
            val parts = line.split(" | ", limit = 4)
            DiagnosisEvent(line, parts.getOrElse(0) { "" }, parts.getOrElse(1) { "Other" }, parts.getOrElse(2) { "Event" }, parts.getOrElse(3) { line })
        }
    }
    val filters = listOf("All", "Network", "Registration", "Calls", "App")
    val visible = remember(parsed, filter) {
        parsed.filter { event -> when (filter) {
            "Network" -> event.type in listOf("Network", "Route")
            "Registration" -> event.type.contains("Registration", true) || event.type == "SIP"
            "Calls" -> event.type in listOf("Call", "Call error", "Connected", "Media")
            "App" -> event.type in listOf("App", "Service")
            else -> true
        } }.take(100)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.NetworkCheck, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Connection diagnosis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("On-device SIP and network event history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(if (rows.isEmpty()) "Waiting for connection events" else "${rows.size} events saved • newest first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A SIP 401 challenge can be normal when followed by 200. Repeated timeouts mean the server did not answer on that network. VPN registration alone does not confirm voice media is reachable.", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
            }
        }

        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Report summary", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(Diagnosis.summary(rows), style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(report)); Toast.makeText(context, "Diagnosis copied", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Copy report", maxLines = 1)
                    }
                    OutlinedButton(onClick = { confirmClear = true }, enabled = rows.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Clear", maxLines = 1)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            filters.forEach { item ->
                FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item, maxLines = 1) })
            }
        }
        Text("Event timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (parsed.size > 100) Text("Showing the latest 100 timeline events; copying the report includes the full saved history.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (visible.isEmpty()) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Text("No events in this view yet. Keep the app open while reproducing the issue.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                items(visible) { event ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 3.dp).size(9.dp).clip(CircleShape).background(eventAccent(event.type)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(event.type, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                                Text("• ${event.network}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(event.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(event.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear diagnosis history?") },
        text = { Text("This removes the saved event timeline from this phone.") },
        confirmButton = { TextButton(onClick = { Diagnosis.clear(); confirmClear = false }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
}

@Composable
private fun eventAccent(type: String): Color = when {
    type.contains("error", true) -> MaterialTheme.colorScheme.error
    type == "Registration" -> MaterialTheme.colorScheme.primary
    type == "Connected" -> Color(0xFF178A62)
    else -> MaterialTheme.colorScheme.tertiary
}
