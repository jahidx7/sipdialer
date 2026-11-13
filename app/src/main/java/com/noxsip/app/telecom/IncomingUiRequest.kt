package com.noxsip.app.telecom

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ইনকামিং কলের পূর্ণ-স্ক্রিন কল পেজ খুলতে অনুরোধ।
 * পপআপে ট্যাপ বা ফুল-স্ক্রিন নোটিফিকেশন MainActivity খুললে request() ডাকা হয়;
 * MainActivity তখন InCall স্ক্রিনে চলে যায় (Answer/Decline সহ)।
 */
object IncomingUiRequest {
    val tick = MutableStateFlow(0)

    @Volatile
    var pending = false

    fun request() {
        pending = true
        tick.value = tick.value + 1
    }

    fun clear() {
        pending = false
    }
}
