package com.noxsip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.service.SipForegroundService
import com.noxsip.app.util.PowerPrefs

/** ব্যাটারি মোড: Balanced (কম ব্যাটারি) বা Reliable (CPU সারাক্ষণ জাগানো) */
@Composable
fun PowerModeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(PowerPrefs.mode(context)) }

    @Composable
    fun Option(value: String, title: String, desc: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = mode == value,
                onClick = {
                    mode = value
                    PowerPrefs.setMode(context, value)
                    SipForegroundService.refreshPower(context)
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("Battery mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Option(
            PowerPrefs.BALANCED, "Balanced (saves battery)",
            "Phone sleeps and wakes every 30 s to keep the account registered. Allow \"Unrestricted\" battery for this app."
        )
        Option(
            PowerPrefs.RELIABLE, "Reliable (uses more battery)",
            "Keeps the CPU awake all the time. Use this if incoming calls are missed in Balanced mode."
        )
    }
}
