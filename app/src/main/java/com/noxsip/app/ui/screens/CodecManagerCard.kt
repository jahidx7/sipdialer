package com.noxsip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.noxsip.app.SipDialerApplication
import com.noxsip.app.util.AudioQualityPrefs

private fun codecTitle(key: String): String {
    val mime = key.substringBefore("/")
    val rate = key.substringAfter("/", "8000").toIntOrNull() ?: 8000
    val khz = if (mime == "G722") 16 else rate / 1000
    val name = when (mime) {
        "PCMA" -> "PCMA (G.711 A-law)"
        "PCMU" -> "PCMU (G.711 \u00B5-law)"
        "G722" -> "G.722 (HD)"
        "OPUS" -> "Opus (HD)"
        "G729" -> "G.729"
        "ILBC" -> "iLBC"
        "SPEEX" -> "Speex"
        else -> mime
    }
    return "$name \u2022 ${khz}k"
}

/** কোডেক ম্যানেজমেন্ট: প্রতিটা কোডেক চালু/বন্ধ ও অগ্রাধিকারের ক্রম (ওপরেরটা আগে অফার হয়) */
@Composable
fun CodecManagerCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun load(): List<AudioQualityPrefs.CodecEntry> {
        AudioQualityPrefs.getCodecConfig(context)?.let { return it }
        // কাস্টম কনফিগ না থাকলে Linphone-এর বর্তমান কার্যকর তালিকা
        return try {
            SipDialerApplication.core.audioPayloadTypes.map {
                AudioQualityPrefs.CodecEntry(it.mimeType.uppercase() + "/" + it.clockRate, it.enabled())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    var items by remember { mutableStateOf(load()) }
    var customized by remember { mutableStateOf(AudioQualityPrefs.getCodecConfig(context) != null) }
    var warn by remember { mutableStateOf<String?>(null) }

    fun apply(list: List<AudioQualityPrefs.CodecEntry>) {
        items = list
        customized = true
        AudioQualityPrefs.setCodecConfig(context, list)
        // নতুন তালিকা পরের কল থেকেই কার্যকর হবে
        try {
            SipDialerApplication.configureVoiceAudio(SipDialerApplication.core)
        } catch (_: Exception) {
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
        Text("Audio Codecs", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "Top codec is offered first. The server picks the first one it supports. Keep at least one enabled.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        items.forEachIndexed { index, c ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = codecTitle(c.key),
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = if (c.enabled) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (c.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        if (index > 0) {
                            val l = items.toMutableList()
                            val t = l[index]; l[index] = l[index - 1]; l[index - 1] = t
                            apply(l)
                        }
                    },
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(20.dp)) }
                IconButton(
                    onClick = {
                        if (index < items.lastIndex) {
                            val l = items.toMutableList()
                            val t = l[index]; l[index] = l[index + 1]; l[index + 1] = t
                            apply(l)
                        }
                    },
                    enabled = index < items.lastIndex,
                    modifier = Modifier.size(32.dp)
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(20.dp)) }
                Switch(
                    checked = c.enabled,
                    onCheckedChange = { on ->
                        if (!on && items.count { it.enabled } <= 1) {
                            warn = "At least one codec must stay enabled."
                        } else {
                            apply(items.toMutableList().also { it[index] = c.copy(enabled = on) })
                        }
                    },
                    modifier = Modifier.scale(0.75f)
                )
            }
        }

        if (customized) {
            TextButton(
                onClick = {
                    AudioQualityPrefs.setCodecConfig(context, null)
                    try {
                        SipDialerApplication.configureVoiceAudio(SipDialerApplication.core)
                    } catch (_: Exception) {
                    }
                    items = load()
                    customized = false
                }
            ) { Text("Reset to default", fontSize = 12.sp) }
        }
    }

    warn?.let {
        AlertDialog(
            onDismissRequest = { warn = null },
            title = { Text("Audio Codecs") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { warn = null }) { Text("OK") } }
        )
    }
}
