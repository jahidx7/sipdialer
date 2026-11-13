package com.noxsip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.util.SecurityPrefs

@Composable
private fun SecurityRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.scale(0.75f))
    }
}

/** কল নিরাপত্তা: TLS সার্টিফিকেট যাচাই ও SRTP। পরিবর্তন কার্যকর করতে অ্যাকাউন্ট Reconnect করুন। */
@Composable
fun SecuritySettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var verifyTls by remember { mutableStateOf(SecurityPrefs.verifyTls(context)) }
    var srtp by remember { mutableStateOf(SecurityPrefs.srtp(context)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Call security", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SecurityRow(
            "Verify TLS certificate",
            "Only when the account uses TLS. Turn off only if your provider's certificate is self-signed.",
            verifyTls
        ) { verifyTls = it; SecurityPrefs.setVerifyTls(context, it) }
        SecurityRow(
            "Encrypt voice (SRTP)",
            "Use if your provider supports it. Some gateways reject it; turn off if calls fail.",
            srtp
        ) { srtp = it; SecurityPrefs.setSrtp(context, it) }
        Text(
            "Reconnect your account after changing these.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
