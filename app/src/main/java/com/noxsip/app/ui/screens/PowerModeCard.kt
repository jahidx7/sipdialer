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

/** SIP ব্যাকগ্রাউন্ড সংযোগের জন্য পাওয়ার মোড */
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
        Text("SIP connection reliability", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Option(
            PowerPrefs.RELIABLE, "Reliable (recommended for incoming calls)",
            "Keeps the SIP foreground service and CPU active in the background. Uses more battery."
        )
        Option(
            PowerPrefs.BALANCED, "Balanced (saves battery)",
            "Android Doze may delay background network and alarms, so incoming SIP calls can be delayed."
        )
        Text(
            "For best results, set this app's battery usage to Unrestricted in Android settings.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
}
