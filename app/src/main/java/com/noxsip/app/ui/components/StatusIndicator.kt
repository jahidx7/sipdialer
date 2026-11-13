package com.noxsip.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noxsip.app.sip.SipRegistrationState
import com.noxsip.app.ui.theme.Green500
import com.noxsip.app.ui.theme.Red500

@Composable
fun StatusIndicator(
    state: SipRegistrationState,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = when (state) {
            SipRegistrationState.REGISTERED -> Green500
            SipRegistrationState.REGISTERING -> Color(0xFFF59E0B)
            SipRegistrationState.ERROR -> Red500
            SipRegistrationState.UNREGISTERED -> Color.Gray
        },
        animationSpec = tween(durationMillis = 300),
        label = "status_color"
    )

    val statusText = when (state) {
        SipRegistrationState.REGISTERED -> "Registered ✓"
        SipRegistrationState.REGISTERING -> "Registering..."
        SipRegistrationState.ERROR -> "Error ✗"
        SipRegistrationState.UNREGISTERED -> "Unregistered"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
