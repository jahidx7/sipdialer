package com.noxsip.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var filter by rememberSaveable { mutableStateOf("All") }
    var confirmClear by remember { mutableStateOf(false) }
    val report = remember(rows) { Diagnosis.report(context, rows) }
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
        } }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Actions stay visible while the report and full event history scroll below.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { clipboard.setText(AnnotatedString(report)); Toast.makeText(context, "Full diagnosis report copied", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Copy full report", maxLines = 1)
            }
            OutlinedButton(
                onClick = { confirmClear = true }, enabled = rows.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Clear", maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Filled.NetworkCheck, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("Connection diagnosis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("On-device SIP, call and network analysis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("${rows.size} of 500 events saved · newest first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Analysis is based on events received by this phone. A 401 followed by 200 is a normal SIP challenge. Registration does not prove that call signaling or audio media works. If no incoming INVITE was received, ask the provider for its call trace.", style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Analysis summary", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Text(Diagnosis.summary(rows), style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val insights = remember(rows) { Diagnosis.insights(rows) }
                        insights.forEach { insight ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(if (insight.first == "Check") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
                                Column {
                                    Text(insight.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (insight.first == "Check") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                    Text(insight.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    filters.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item, maxLines = 1) }) }
                }
            }
            item { Text("Event timeline · ${visible.size} events", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            if (visible.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Text("No events in this view yet. Keep the app open while reproducing the issue.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(visible, key = { it.raw }) { event ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), RoundedCornerShape(13.dp)).padding(11.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(eventAccent(event.type)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(event.type, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                                Text("· ${event.network}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
