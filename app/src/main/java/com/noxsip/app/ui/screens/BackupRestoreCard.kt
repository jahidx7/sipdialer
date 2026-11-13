package com.noxsip.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.data.BackupManager
import com.noxsip.app.data.BackupPassVault
import com.noxsip.app.ui.theme.Indigo600
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** অ্যাকাউন্ট ও কল লগের এনক্রিপ্টেড ব্যাকআপ ও রিস্টোর কার্ড */
@Composable
fun BackupRestoreCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingIsExport by remember { mutableStateOf(true) }
    var passphrase by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val activity = remember(context) { BackupPassVault.findActivity(context) }
    val bioAvailable = remember(context) { BackupPassVault.available(context) }
    var hasSaved by remember { mutableStateOf(BackupPassVault.hasSaved(context)) }
    var rememberFp by remember { mutableStateOf(true) }

    fun openPasswordDialog(uri: Uri, isExport: Boolean) {
        pendingUri = uri; pendingIsExport = isExport; passphrase = ""; confirmPass = ""
    }

    fun runLocal(uri: Uri, isExport: Boolean, pass: CharArray, fromSaved: Boolean, saveOnSuccess: String?) {
        busy = true
        scope.launch {
            var fallback = false
            var ok = false
            resultMessage = try {
                if (isExport) {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Cannot open file")
                    val s = out.use { BackupManager.export(context, pass, it) }
                    ok = true
                    "Backup saved: ${s.accounts} accounts, ${s.calls} calls, ${s.contacts} contacts."
                } else {
                    val inp = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot open file")
                    val s = inp.use { BackupManager.restore(context, pass, it) }
                    ok = true
                    "Restored: ${s.accounts} accounts, ${s.calls} new calls, ${s.contacts} new contacts."
                }
            } catch (e: BackupManager.WrongPassphraseException) {
                if (fromSaved) {
                    fallback = true
                    "The saved fingerprint password does not open this backup. Enter its password."
                } else "Wrong password or damaged file."
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
            busy = false
            if (fallback) openPasswordDialog(uri, false) else pendingUri = null
            if (ok && saveOnSuccess != null && activity != null) {
                BackupPassVault.save(activity, saveOnSuccess) { saved -> hasSaved = saved }
            }
        }
    }

    fun begin(uri: Uri, isExport: Boolean) {
        if (hasSaved && activity != null) {
            BackupPassVault.unlock(activity) { saved ->
                if (saved != null) runLocal(uri, isExport, saved.toCharArray(), true, null)
                else { hasSaved = BackupPassVault.hasSaved(context); openPasswordDialog(uri, isExport) }
            }
        } else {
            openPasswordDialog(uri, isExport)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) begin(uri, true) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) begin(uri, false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Indigo600.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    text = "Local Backup",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Saves SIP accounts, call log and saved contacts into one password-protected file. Call recordings are not included.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // লাল সতর্কবার্তা: পাসওয়ার্ড হারালে ব্যাকআপ খোলা যায় না
            Text(
                text = "IMPORTANT: Write down your backup password and keep it safe. " +
                    "If you forget it, nobody can open or restore this backup.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color(0xFFDC2626)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("SipDialer-backup-$stamp.sipbak")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup", fontSize = 13.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", fontSize = 13.sp, maxLines = 1)
                }
            }
            if (hasSaved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Fingerprint unlock is on", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !busy, onClick = {
                        BackupPassVault.clear(context); hasSaved = false
                    }) { Text("Change password", fontSize = 12.sp) }
                }
            }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    pendingUri?.let { uri ->
        val needConfirm = pendingIsExport
        val canGo = passphrase.length >= (if (needConfirm) 8 else 1) && (!needConfirm || passphrase == confirmPass)
        AlertDialog(
            onDismissRequest = { if (!busy) pendingUri = null },
            title = { Text(if (pendingIsExport) "Set backup password" else "Enter backup password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(if (needConfirm) "Password (min 8 characters)" else "Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (needConfirm) {
                        OutlinedTextField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it },
                            label = { Text("Repeat password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            isError = confirmPass.isNotEmpty() && confirmPass != passphrase,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "If you forget this password the backup cannot be opened.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (bioAvailable && activity != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = rememberFp, onCheckedChange = { rememberFp = it })
                            Text("Use fingerprint next time", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canGo && !busy,
                    onClick = {
                        val pass = passphrase.toCharArray()
                        val saveIt = if (rememberFp && bioAvailable && activity != null) passphrase else null
                        runLocal(uri, pendingIsExport, pass, false, saveIt)
                    }
                ) { Text(if (pendingIsExport) "Save backup" else "Restore") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingUri = null }) { Text("Cancel") }
            }
        )
    }

    resultMessage?.let { msg ->
        val ok = msg.startsWith("Backup saved") || msg.startsWith("Restored")
        val green = androidx.compose.ui.graphics.Color(0xFF16A34A)
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            icon = if (ok) {
                { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = green, modifier = Modifier.size(36.dp)) }
            } else null,
            title = {
                Text(
                    if (ok) "Success" else "Backup & Restore",
                    color = if (ok) green else androidx.compose.ui.graphics.Color.Unspecified
                )
            },
            text = {
                Text(
                    msg,
                    color = if (ok) green else androidx.compose.ui.graphics.Color.Unspecified,
                    fontWeight = if (ok) FontWeight.SemiBold else null
                )
            },
            confirmButton = { TextButton(onClick = { resultMessage = null }) { Text("OK") } }
        )
    }
}
