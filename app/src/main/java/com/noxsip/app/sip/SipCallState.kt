package com.noxsip.app.sip

import org.linphone.core.Call

/**
 * SIP কল স্টেট
 */
sealed class SipCallState {
    data object Idle : SipCallState()
    data class Incoming(val remoteAddress: String, val call: Call) : SipCallState()
    data class Outgoing(val remoteAddress: String, val call: Call) : SipCallState()
    data class Connected(val remoteAddress: String, val call: Call) : SipCallState()
    data class OnHold(val remoteAddress: String, val call: Call) : SipCallState()
    data class Ended(val remoteAddress: String, val reason: String) : SipCallState()
}

/**
 * SIP রেজিস্ট্রেশন স্টেট
 */
enum class SipRegistrationState {
    UNREGISTERED,
    REGISTERING,
    REGISTERED,
    ERROR
}
