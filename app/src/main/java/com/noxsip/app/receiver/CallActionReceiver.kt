package com.noxsip.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.noxsip.app.sip.SipEngine
import com.noxsip.app.util.NotificationHelper

/**
 * কল নোটিফিকেশনের Answer / Decline / Hang up বাটন। আনলক ছাড়াই লক স্ক্রিন থেকে কাজ করে।
 */
class CallActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_ANSWER = "com.noxsip.app.action.CALL_ANSWER"
        const val ACTION_DECLINE = "com.noxsip.app.action.CALL_DECLINE"
        const val ACTION_HANGUP = "com.noxsip.app.action.CALL_HANGUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val caller = intent.getStringExtra("caller_address").orEmpty()
        val engine = SipEngine.getInstance(app)
        try {
            when (intent.action) {
                ACTION_ANSWER -> {
                    engine.answerCall()
                    NotificationHelper.showOngoingCallNotification(app, caller)
                }
                ACTION_DECLINE -> engine.declineCall()
                ACTION_HANGUP -> {
                    engine.endCall()
                    NotificationHelper.cancelOngoingCallNotification(app)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Call action failed: ${intent.action}", e)
        }
    }
}
