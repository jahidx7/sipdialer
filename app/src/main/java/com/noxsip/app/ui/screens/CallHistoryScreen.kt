package com.noxsip.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.data.models.CallRecord
import com.noxsip.app.ui.theme.Green500
import com.noxsip.app.ui.theme.Red500
import com.noxsip.app.util.DeviceContact
import com.noxsip.app.util.RecordingStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private data class CallFilterTab(
    val id: Int,
    val label: String,
    val color: Color
)

@Composable
fun CallHistoryScreen(
    callRecords: List<CallRecord>,
    contacts: List<DeviceContact> = emptyList(),
    onCallBack: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = All, 1 = Missed, 2 = Received, 3 = Outgoing
    var selectedFilter by remember { mutableIntStateOf(0) }
    var playingRecordId by remember { mutableStateOf<Long?>(null) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef.value?.stop()
            } catch (_: Exception) {
            }
            try {
                mediaPlayerRef.value?.release()
            } catch (_: Exception) {
            }
            mediaPlayerRef.value = null
        }
    }

    // 4 distinct, calm, eye-friendly colors — no icons inside chips so text never truncates!
    val colorScheme = MaterialTheme.colorScheme
    val filterTabs = listOf(
        CallFilterTab(0, "All", colorScheme.primary),
        CallFilterTab(1, "Missed", colorScheme.error),
        CallFilterTab(2, "Received", Green500),
        CallFilterTab(3, "Outgoing", colorScheme.tertiary)
    )

    val filteredRecords = remember(callRecords, selectedFilter) {
        when (selectedFilter) {
            1 -> callRecords.filter { it.isIncoming && !it.isAnswered }
            2 -> callRecords.filter { it.isIncoming && it.isAnswered }
            3 -> callRecords.filter { !it.isIncoming }
            else -> callRecords
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Filter Bar: 4 full-text calm colored pills + subtle clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterTabs.forEach { tab ->
                val isSelected = selectedFilter == tab.id
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { selectedFilter = tab.id },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) tab.color else tab.color.copy(alpha = 0.10f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) tab.color else tab.color.copy(alpha = 0.25f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (!isSelected) tab.color else when (tab.id) {
                                0 -> colorScheme.onPrimary
                                1 -> colorScheme.onError
                                3 -> colorScheme.onTertiary
                                else -> Color.White
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }

            if (callRecords.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )

        if (filteredRecords.isEmpty()) {
            val activeTab = filterTabs.first { it.id == selectedFilter }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(activeTab.color.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = activeTab.color
                        )
                    }
                    Text(
                        text = "No ${activeTab.label} Calls",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // Clean native borderless list with subtle inset dividers
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(filteredRecords) { index, record ->
                    val isPlaying = playingRecordId == record.id
                    CallHistoryRow(
                        record = record,
                        contacts = contacts,
                        isPlayingRecording = isPlaying,
                        onTogglePlayRecording = { path ->
                            try {
                                mediaPlayerRef.value?.stop()
                            } catch (_: Exception) {
                            }
                            try {
                                mediaPlayerRef.value?.release()
                            } catch (_: Exception) {
                            }
                            mediaPlayerRef.value = null

                            if (isPlaying) {
                                playingRecordId = null
                            } else {
                                try {
                                    val mp = MediaPlayer().apply {
                                        RecordingStorage.setDataSource(this, ctx, path)
                                        setOnCompletionListener {
                                            playingRecordId = null
                                        }
                                        prepare()
                                        start()
                                    }
                                    mediaPlayerRef.value = mp
                                    playingRecordId = record.id
                                } catch (_: Exception) {
                                    playingRecordId = null
                                }
                            }
                        },
                        onCallBack = { onCallBack(record.remoteAddress) }
                    )
                    if (index < filteredRecords.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    record: CallRecord,
    contacts: List<DeviceContact>,
    isPlayingRecording: Boolean = false,
    onTogglePlayRecording: (String) -> Unit = {},
    onCallBack: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
    }

    val rawNum = record.remoteAddress.replace("sip:", "").split("@").firstOrNull() ?: record.remoteAddress
    val matchedContactName = remember(rawNum, contacts) {
        val digits = rawNum.filter { it.isDigit() }
        if (digits.length >= 7) {
            val tail = digits.takeLast(10)
            contacts.firstOrNull { it.normalizedDigits.endsWith(tail) }?.name
        } else {
            null
        }
    }

    val cleanNumber = matchedContactName ?: record.displayName.ifEmpty { rawNum }

    val isMissed = record.isIncoming && !record.isAnswered
    val accentColor = when {
        isMissed -> Red500
        record.isIncoming -> Green500
        else -> Indigo600
    }
    val directionText = when {
        isMissed -> "Missed"
        record.isIncoming -> "Received"
        else -> "Outgoing"
    }

    val durationLabel = remember(record.duration, isMissed) {
        if (isMissed) {
            null
        } else {
            val mins = record.duration / 60
            val secs = record.duration % 60
            when {
                mins > 0 && secs > 0 -> "${mins}m ${secs}s"
                mins > 0 -> "${mins}m"
                else -> "${secs}s"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCallBack() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Clean Contact Avatar Icon (never shows '+' or '0')
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = cleanNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isMissed) Red500 else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (record.isIncoming) {
                            if (record.isAnswered) Icons.AutoMirrored.Filled.CallReceived
                            else Icons.AutoMirrored.Filled.CallMissed
                        } else {
                            Icons.AutoMirrored.Filled.CallMade
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = dateFormat.format(Date(record.timestamp)) +
                            (if (record.accountUser.isNotBlank()) " • ${record.accountUser}" else ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (durationLabel != null) {
                        Text(
                            text = "• $durationLabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                }
            }
        }

        val rowContext = androidx.compose.ui.platform.LocalContext.current
        val hasRecording = remember(record.recordingPath) {
            RecordingStorage.exists(rowContext, record.recordingPath)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // রেকর্ডিং প্লেব্যাক Recents-এ দেখানো হয় না (ফাইল ফোনের স্টোরেজে সংরক্ষিত থাকে)
            if (false && hasRecording) {
                IconButton(
                    onClick = { onTogglePlayRecording(record.recordingPath) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingRecording) Icons.Filled.StopCircle else Icons.Filled.PlayCircle,
                        contentDescription = if (isPlayingRecording) "Stop Recording" else "Play Recording",
                        tint = if (isPlayingRecording) Red500 else Indigo600,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            IconButton(
                onClick = onCallBack,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = "Call",
                    tint = Green500,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
