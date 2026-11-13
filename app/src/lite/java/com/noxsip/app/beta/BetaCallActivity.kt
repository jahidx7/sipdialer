package com.noxsip.app.beta

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.ui.components.CallControls
import com.noxsip.app.ui.components.DialPad
import com.noxsip.app.ui.components.IncomingCallControls
import kotlinx.coroutines.delay

/**
 * Beta: SIM কলের কল স্ক্রিন। চেহারা SIP কল স্ক্রিনের (InCallScreen) মতো: একই গ্রেডিয়েন্ট, ব্যাজ,
 * ঢেউ-অ্যানিমেশন ও একই কন্ট্রোল বাটন। লক স্ক্রিনের উপরেও দেখা যায়।
 */
class BetaCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "beta_call_action"
        const val ACTION_ANSWER = "answer"
        const val ACTION_DECLINE = "decline"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        handleAction(intent)
        setContent { BetaCallScreen(onClose = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        SimCallManager.activityVisible.value = true
    }

    override fun onUserLeaveHint() {
        SimCallManager.activityVisible.value = false
        super.onUserLeaveHint()
    }

    override fun onPause() {
        SimCallManager.activityVisible.value = false
        super.onPause()
    }

    override fun onDestroy() {
        SimCallManager.activityVisible.value = false
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(i: Intent?) {
        val action = i?.getStringExtra(EXTRA_ACTION) ?: return
        i.removeExtra(EXTRA_ACTION)
        val call = SimCallManager.ringingCall() ?: return
        when (action) {
            ACTION_ANSWER -> SimCallManager.answer(call)
            ACTION_DECLINE -> SimCallManager.reject(call)
        }
    }
}

@Composable
private fun BetaCallScreen(onClose: () -> Unit) {
    val calls by SimCallManager.calls.collectAsState()
    val tick by SimCallManager.tick.collectAsState()
    val audio by SimCallManager.audioState.collectAsState()

    // সব কল শেষ হলে স্ক্রিন বন্ধ
    LaunchedEffect(calls) {
        if (calls.isEmpty()) {
            delay(600)
            if (SimCallManager.calls.value.isEmpty()) onClose()
        }
    }

    // ইনকামিং আগে, না হলে প্রথম কল
    val call: Call? = calls.firstOrNull { it.state == Call.STATE_RINGING } ?: calls.firstOrNull()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call, tick) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    var showDialpad by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1428), Color(0xFF13274F), Color(0xFF0A1428))
                )
            )
    ) {
        if (call == null) {
            Text(
                "Call Ended",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }
        val state = call.state
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val title = callTitle(call)
        val simCtx = androidx.compose.ui.platform.LocalContext.current
        val simLabel = remember(call) { simSlotLabel(simCtx, call) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp)
            ) {
                Surface(color = Color(0xFF1E3A6E), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = simLabel.uppercase() + " CALL",
                        color = Color(0xFF93C5FD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(40.dp))
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                if (number.isNotBlank() && number != title) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = number,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF94A3B8)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stateLabel(state, call, now, simLabel),
                    fontSize = 16.sp,
                    color = if (state == Call.STATE_ACTIVE) Color(0xFF22C55E) else Color(0xFFCBD5E1),
                    fontWeight = if (state == Call.STATE_ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                if (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING) {
                    Spacer(Modifier.height(10.dp))
                    SimQualityLine(call)
                }
            }

            val isActive = state == Call.STATE_ACTIVE
            if (!(showDialpad && isActive)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (state == Call.STATE_RINGING || state == Call.STATE_DIALING ||
                        state == Call.STATE_CONNECTING || state == Call.STATE_PULLING_CALL
                    ) {
                        BetaCallingRipples()
                    }
                }
            }
            if (showDialpad && isActive) {
                DialPad(
                    onDigitPressed = { d -> SimCallManager.playDtmf(call, d.first()) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isActive) {
                    // SIM কল রেকর্ড বন্ধ (পরীক্ষায় ভরসাযোগ্য নয়); শুধু DTMF কিপ্যাড বাটন
                    FilledTonalIconButton(onClick = { showDialpad = !showDialpad }) {
                        Icon(Icons.Filled.Dialpad, contentDescription = "DTMF Keypad")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (state == Call.STATE_RINGING) {
                    IncomingCallControls(
                        onAnswer = { SimCallManager.answer(call) },
                        onDecline = { SimCallManager.reject(call) }
                    )
                } else {
                    val holding = state == Call.STATE_HOLDING
                    CallControls(
                        isMuted = audio?.isMuted == true,
                        isSpeaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
                        isOnHold = holding,
                        onMuteToggle = { SimCallManager.setMuted(audio?.isMuted != true) },
                        onSpeakerToggle = {
                            SimCallManager.setSpeaker(audio?.route != CallAudioState.ROUTE_SPEAKER)
                        },
                        onHoldToggle = {
                            if (holding) SimCallManager.unhold(call) else SimCallManager.hold(call)
                        },
                        onEndCall = { SimCallManager.disconnect(call) }
                    )
                }
            }
        }
    }
}

private data class SimQuality(
    val network: String,
    val hd: Boolean,
    val wifi: Boolean,
    val dbm: Int?,
    val level: Int
)

private fun networkName(type: Int): String = when (type) {
    TelephonyManager.NETWORK_TYPE_NR -> "5G"
    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
    TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi Calling"
    TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B,
    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
    TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
    TelephonyManager.NETWORK_TYPE_GSM -> "2G"
    else -> "Mobile"
}

/** SIM কল মডেম সামলায়: RTT/জিটার/লস বা মাইক লেভেল অ্যাপ পায় না। যা পাওয়া যায় তা দেখাই (নেটওয়ার্ক, HD, সিগন্যাল) */
private fun readSimQuality(context: android.content.Context, call: Call): SimQuality? {
    return try {
        var tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
        call.details?.accountHandle?.id?.toIntOrNull()?.let { subId ->
            tm = tm.createForSubscriptionId(subId)
        }
        val hd = call.details?.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO) == true
        val wifi = call.details?.hasProperty(Call.Details.PROPERTY_WIFI) == true
        var network = networkName(tm.voiceNetworkType)
        if (wifi) network = "Wi-Fi Calling"
        var dbm: Int? = null
        var level = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ss = tm.signalStrength
            if (ss != null) {
                level = ss.level
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dbm = ss.cellSignalStrengths.firstOrNull()?.dbm?.takeIf { it != Int.MAX_VALUE }
                }
            }
        }
        SimQuality(network, hd, wifi, dbm, level)
    } catch (e: Exception) {
        null
    }
}

/** SIP স্ক্রিনের কানেকশন-কোয়ালিটি লাইনের মতো: রঙিন ডট + HD/নেটওয়ার্ক/সিগন্যাল */
@Composable
private fun SimQualityLine(call: Call) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var q by remember { mutableStateOf<SimQuality?>(null) }
    LaunchedEffect(call) {
        while (true) {
            q = readSimQuality(context, call)
            delay(2000)
        }
    }
    val info = q ?: return
    val color = when {
        info.level >= 3 -> Color(0xFF22C55E)
        info.level == 2 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val label = when {
        info.level >= 3 -> "Good signal"
        info.level == 2 -> "Fair signal"
        else -> "Weak signal"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = buildString {
                    append(if (info.hd) "HD Voice" else "Standard voice")
                    append(" • ").append(info.network)
                    append(" • ").append(label)
                    info.dbm?.let { append(" (").append(it).append(" dBm)") }
                },
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "Mic level and loss/jitter are handled by the modem (not available for SIM calls)",
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
    }
}

private fun stateLabel(state: Int, call: Call, now: Long, sim: String): String = when (state) {
    Call.STATE_RINGING -> "Incoming call via $sim"
    Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> "Calling via $sim…"
    Call.STATE_HOLDING -> "Call On Hold"
    Call.STATE_DISCONNECTING -> "Ending…"
    Call.STATE_DISCONNECTED -> "Call Ended"
    Call.STATE_ACTIVE -> {
        val start = call.details?.connectTimeMillis ?: 0L
        val secs = if (start > 0) ((now - start) / 1000).coerceAtLeast(0) else 0L
        "Connected • %02d:%02d".format(secs / 60, secs % 60)
    }
    else -> ""
}

/** কল যাওয়া/আসার সময় ঢেউয়ের মতো ছড়িয়ে পড়া তিনটি রিং ও মাঝে ফোন আইকন (SIP স্ক্রিনের মতো) */
@Composable
private fun BetaCallingRipples() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ripples")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                2400,
                easing = androidx.compose.animation.core.LinearEasing
            )
        ),
        label = "ripplesProgress"
    )
    val iconPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "iconPulse"
    )
    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val minR = 34.dp.toPx()
            val maxR = size.minDimension / 2f
            for (i in 0 until 3) {
                val p = (progress + i / 3f) % 1f
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.35f * (1f - p)),
                    radius = minR + (maxR - minR) * p,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
        }
        Box(
            modifier = Modifier
                .size((64 * iconPulse).dp)
                .clip(CircleShape)
                .background(Color(0xFF2563EB)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
