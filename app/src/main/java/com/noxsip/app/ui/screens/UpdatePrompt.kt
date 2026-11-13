package com.noxsip.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.data.UpdateManager
import kotlinx.coroutines.launch

/**
 * অ্যাপ খুললে নতুন ভার্সন থাকলে ডায়লগে আপডেট করতে বলে (Update / Skip)।
 * VERSION_URL খালি থাকলে (full সংস্করণ) কিছুই করে না। Skip করলে ওই ভার্সনের জন্য আর দেখায় না;
 * Settings → User Account-এর কার্ড থেকে হাতে চেক করা যায়।
 */
@Composable
fun UpdatePrompt() {
    if (com.noxsip.app.BuildConfig.VERSION_URL.isBlank()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE) }
    var update by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val info = runCatching { UpdateManager.check(context) }.getOrNull()
        if (info != null && prefs.getLong("skipped_update_code", 0L) != info.versionCode) update = info
    }

    val u = update ?: return
    AlertDialog(
        onDismissRequest = { if (!busy) update = null },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("New version v${u.versionName} is available.")
                if (u.notes.isNotBlank()) Text(u.notes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress in 0..99) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading… $progress%", fontSize = 12.sp)
                }
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!UpdateManager.canInstall(context)) {
                    error = "Allow \"Install unknown apps\" for Nox SIP, then tap Update again."
                    UpdateManager.openInstallPermissionSettings(context)
                    return@TextButton
                }
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val file = UpdateManager.download(context, u) { progress = it }
                        progress = -1
                        UpdateManager.install(context, file)
                        update = null
                    } catch (e: Exception) {
                        progress = -1
                        error = "Update failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "Downloading…" else "Update") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                prefs.edit().putLong("skipped_update_code", u.versionCode).apply()
                update = null
            }) { Text("Skip") }
        }
    )
}
