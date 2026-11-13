package com.noxsip.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.ui.theme.Green500
import com.noxsip.app.ui.theme.Red500

@Composable
fun CallControls(
    isMuted: Boolean,
    isSpeaker: Boolean,
    isOnHold: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHoldToggle: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallControlButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = "Mute",
                isActive = isMuted,
                onClick = onMuteToggle
            )
            CallControlButton(
                icon = if (isSpeaker) Icons.Filled.VolumeUp else Icons.Filled.VolumeDown,
                label = "Speaker",
                isActive = isSpeaker,
                onClick = onSpeakerToggle
            )
            CallControlButton(
                icon = if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                label = if (isOnHold) "Resume" else "Hold",
                isActive = isOnHold,
                onClick = onHoldToggle
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FloatingActionButton(
            onClick = onEndCall,
            containerColor = Red500,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "End Call",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun IncomingCallControls(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionButton(
            onClick = onDecline,
            containerColor = Red500,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "Decline",
                modifier = Modifier.size(32.dp)
            )
        }

        FloatingActionButton(
            onClick = onAnswer,
            containerColor = Green500,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                // লালের (CallEnd) মতোই সমান্তরাল অ্যাঙ্গেল; ১৮০° ঘুরিয়ে উপরের দিকে মুখ
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "Answer",
                modifier = Modifier
                    .size(32.dp)
                    .rotate(180f)
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledIconToggleButton(
            checked = isActive,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(60.dp),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                checkedContainerColor = Color(0xFF3B82F6),
                checkedContentColor = Color.White,
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = Color.White,
            maxLines = 1
        )
    }
}
