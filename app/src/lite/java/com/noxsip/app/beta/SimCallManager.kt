package com.noxsip.app.beta

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Beta: Telecom (SIM) কলগুলোর অবস্থা এক জায়গায়। SIP কল এখানে আসে না, SIP আগের মতোই SipEngine দিয়ে চলে।
 */
object SimCallManager {

    /** বর্তমান SIM কলের তালিকা */
    val calls = MutableStateFlow<List<Call>>(emptyList())

    /** কোনো কলের অবস্থা বদলালে বাড়ে, যাতে UI আবার আঁকে */
    val tick = MutableStateFlow(0)

    val audioState = MutableStateFlow<CallAudioState?>(null)

    /** BetaCallActivity এখন সামনে দৃশ্যমান কি না (পপ-আপ বার লুকানোর জন্য) */
    val activityVisible = MutableStateFlow(false)

    /** SIM কল রেকর্ড চলছে কি না (পরীক্ষামূলক, শুধু মাইক) */
    val recording = MutableStateFlow(false)

    @Volatile
    var service: InCallService? = null

    private val callbacks = HashMap<Call, Call.Callback>()

    fun add(call: Call) {
        if (calls.value.contains(call)) return
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                tick.value = tick.value + 1
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                tick.value = tick.value + 1
            }
        }
        callbacks[call] = cb
        call.registerCallback(cb)
        calls.value = calls.value + call
    }

    fun remove(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        calls.value = calls.value - call
        tick.value = tick.value + 1
    }

    /** ইনকামিং (রিং হচ্ছে) কল, থাকলে */
    fun ringingCall(): Call? = calls.value.firstOrNull { it.state == Call.STATE_RINGING }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun reject(call: Call) = call.reject(false, null)

    fun disconnect(call: Call) = call.disconnect()

    fun hold(call: Call) = call.hold()

    fun unhold(call: Call) = call.unhold()

    fun playDtmf(call: Call, c: Char) {
        call.playDtmfTone(c)
        call.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
    }
}
