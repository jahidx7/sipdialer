package com.noxsip.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.data.UpdateManager
import com.noxsip.app.ui.theme.Indigo600
import kotlinx.coroutines.launch

/** অ্যাপ আপডেট চেক ও ইনস্টল কার্ড */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var downloaded by remember { mutableStateOf<java.io.File?>(null) }

    Card(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("App Update", textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Indigo600)
            Text(
                "Current: v${runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrDefault("?")}",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val u = update
            if (u != null) {
                Text(
                    "New version available: v${u.versionName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                if (u.notes.isNotBlank()) {
                    Text(u.notes, fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (progress in 0..99) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Downloading… $progress%", fontSize = 12.sp)
            }
            message?.let { Text(it, fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (u == null) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                try {
                                    val info = UpdateManager.check(context)
                                    update = info
                                    downloaded = null
                                    message = if (info == null) "You are on the latest version." else null
                                } catch (e: Exception) {
                                    message = "Could not check for updates: ${e.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text(if (busy) "Checking…" else "Check for update") }
                } else {
                    Button(
                        enabled = !busy,
                        onClick = {
                            if (!UpdateManager.canInstall(context)) {
                                message = "Allow \"Install unknown apps\" for Nox SIP, then tap again."
                                UpdateManager.openInstallPermissionSettings(context)
                                return@Button
                            }
                            scope.launch {
                                busy = true
                                message = null
                                try {
                                    val file = downloaded ?: UpdateManager.download(context, u) { progress = it }
                                    downloaded = file
                                    progress = -1
                                    UpdateManager.install(context, file)
                                } catch (e: Exception) {
                                    progress = -1
                                    message = "Update failed: ${e.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text(if (busy) "Downloading…" else "Download & Install") }
                }
            }
        }
    }
}
