package com.noxsip.app.telecom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.ui.theme.SipDialerTheme

class CallChoiceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent?.getStringExtra("phone_number") ?: ""
        val simAccounts = TelecomHelper.getAvailableSimAccounts(this)

        setContent {
            SipDialerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            finish()
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Select Calling Account",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Single row with 3 Pill-Shaped buttons: SIM 1 | SIM 2 | SIP
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (simAccounts.isNotEmpty()) {
                                    simAccounts.forEach { sim ->
                                        ChoicePillButton(
                                            modifier = Modifier.weight(1f),
                                            label = "SIM ${sim.slotIndex}",
                                            bgColor = if (sim.slotIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            onClick = {
                                                if (number.isNotBlank()) {
                                                    SipCallRedirectionService.bypassNextNumber = number
                                                    SipCallRedirectionService.bypassUntilMs = System.currentTimeMillis() + 8000L
                                                    TelecomHelper.placeSimCallWithHandle(
                                                        this@CallChoiceActivity,
                                                        number,
                                                        sim.handle
                                                    )
                                                }
                                                finish()
                                            }
                                        )
                                    }
                                } else {
                                    ChoicePillButton(
                                        modifier = Modifier.weight(1f),
                                        label = "SIM 1",
                                        bgColor = MaterialTheme.colorScheme.primary,
                                        onClick = {
                                            if (number.isNotBlank()) {
                                                SipCallRedirectionService.bypassNextNumber = number
                                                SipCallRedirectionService.bypassUntilMs = System.currentTimeMillis() + 8000L
                                                TelecomHelper.placeSimCallWithHandle(this@CallChoiceActivity, number, null)
                                            }
                                            finish()
                                        }
                                    )
                                    ChoicePillButton(
                                        modifier = Modifier.weight(1f),
                                        label = "SIM 2",
                                        bgColor = MaterialTheme.colorScheme.secondary,
                                        onClick = {
                                            if (number.isNotBlank()) {
                                                SipCallRedirectionService.bypassNextNumber = number
                                                SipCallRedirectionService.bypassUntilMs = System.currentTimeMillis() + 8000L
                                                TelecomHelper.placeSimCallWithHandle(this@CallChoiceActivity, number, null)
                                            }
                                            finish()
                                        }
                                    )
                                }

                                ChoicePillButton(
                                    modifier = Modifier.weight(1f),
                                    label = "SIP",
                                    bgColor = MaterialTheme.colorScheme.tertiary,
                                    onClick = {
                                        if (number.isNotBlank()) {
                                            TelecomHelper.placeSipCallFallback(applicationContext, number)
                                        }
                                        finish()
                                    }
                                )
                            }

                            // Cancel button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { finish() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicePillButton(
    modifier: Modifier = Modifier,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
