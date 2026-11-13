package com.noxsip.app.telecom

import android.content.Context
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log
import com.noxsip.app.sip.SipCallState
import com.noxsip.app.sip.SipEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * SIP কানেকশন - প্রতিটি কলকে নেটিভ ডায়ালারে রিপ্রেজেন্ট করে এবং স্টেট সিঙ্ক রাখে
 */
class SipConnection(private val context: Context) : Connection() {

    companion object {
        private const val TAG = "SipConnection"
    }

    private val sipEngine: SipEngine by lazy {
        SipEngine.getInstance(context)
    }

    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hasSeenActiveOrRinging = false

    init {
        connectionScope.launch {
            sipEngine.callState.collectLatest { state ->
                when (state) {
                    is SipCallState.Incoming -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_RINGING) {
                            setRinging()
                        }
                    }
                    is SipCallState.Outgoing -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_DIALING) {
                            setDialing()
                        }
                    }
                    is SipCallState.Connected -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_ACTIVE) {
                            setActive()
                        }
                    }
                    is SipCallState.OnHold -> {
                        hasSeenActiveOrRinging = true
                        if (this@SipConnection.state != STATE_HOLDING) {
                            setOnHold()
                        }
                    }
                    is SipCallState.Ended -> {
                        if (hasSeenActiveOrRinging && this@SipConnection.state != STATE_DISCONNECTED) {
                            setDisconnected(DisconnectCause(DisconnectCause.REMOTE, state.reason))
                            destroy()
                            connectionScope.cancel()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        super.onCallAudioStateChanged(state)
        state ?: return
        Log.d(TAG, "onCallAudioStateChanged: muted=${state.isMuted}, route=${state.route}")
        sipEngine.setMute(state.isMuted)
        val isSpeaker = state.route == CallAudioState.ROUTE_SPEAKER
        sipEngine.setSpeaker(isSpeaker)
    }

    /**
     * ইউজার কল রিসিভ করলে (নেটিভ ডায়ালার থেকে)
     */
    override fun onAnswer() {
        Log.d(TAG, "onAnswer")
        sipEngine.answerCall()
        setActive()
    }

    override fun onAnswer(videoState: Int) {
        Log.d(TAG, "onAnswer with videoState: $videoState")
        onAnswer()
    }

    /**
     * ইউজার কল ডিক্লাইন করলে
     */
    override fun onReject() {
        Log.d(TAG, "onReject")
        connectionScope.cancel()
        sipEngine.declineCall()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    /**
     * ইউজার কল ডিসকানেক্ট করলে
     */
    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect")
        connectionScope.cancel()
        sipEngine.endCall()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    /**
     * হোল্ড
     */
    override fun onHold() {
        Log.d(TAG, "onHold")
        sipEngine.toggleHold()
        setOnHold()
    }

    /**
     * আনহোল্ড
     */
    override fun onUnhold() {
        Log.d(TAG, "onUnhold")
        sipEngine.toggleHold()
        setActive()
    }

    /**
     * কল abort
     */
    override fun onAbort() {
        Log.d(TAG, "onAbort")
        connectionScope.cancel()
        sipEngine.endCall()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    /**
     * DTMF টোন পাঠানো
     */
    override fun onPlayDtmfTone(c: Char) {
        Log.d(TAG, "DTMF: $c")
        sipEngine.sendDtmf(c)
    }

    override fun onStopDtmfTone() {
        // DTMF টোন বন্ধ
    }
}
