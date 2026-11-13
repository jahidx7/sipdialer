package com.noxsip.app.beta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

/**
 * Beta: ডিফল্ট ডায়ালার হলে Telecom এই সার্ভিসকে বাইন্ড করে এবং প্রতিটা SIM কল (ইনকামিং/আউটগোয়িং) এখানে পাঠায়।
 * শুধু কল দেখানো ও নিয়ন্ত্রণ; কল অডিও ধরা বা রেকর্ড করা হয় না।
 */
class NoxInCallService : InCallService() {

    companion object {
        private const val TAG = "NoxInCallService"
        private const val CHANNEL_ID = "beta_sim_call"
        private const val CHANNEL_QUIET_ID = "beta_sim_call_quiet"
        private const val NOTIF_ID = 7101
    }

    override fun onCreate() {
        super.onCreate()
        SimCallManager.service = this
        SimCallBar.attach(this)
    }

    override fun onDestroy() {
        SimCallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "onCallAdded state=${call.state} handle=${call.details?.handle}")
        // *21# বা **21*নম্বর# ধরনের কোড (ফরোয়ার্ডিং/ব্যালেন্স) কল নয়: ফোন নিজেই ফলাফল দেখায়, কল স্ক্রিন খুলব না
        val dialed = call.details?.handle?.schemeSpecificPart.orEmpty()
        if (DialCodes.isCode(dialed)) return
        SimCallManager.add(call)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state != Call.STATE_RINGING) cancelRingingNotification()
            }
        })
        if (call.state == Call.STATE_RINGING) {
            showIncomingNotification()
        } else {
            openCallScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(TAG, "onCallRemoved")
        SimCallManager.remove(call)
        cancelRingingNotification()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        SimCallManager.audioState.value = audioState
    }

    private fun openCallScreen() {
        try {
            val i = Intent(this, BetaCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(i)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open call screen", e)
        }
    }

    private fun showIncomingNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // স্ক্রিন খোলা ও আনলকড এবং ওভারলে অনুমতি থাকলে অ্যাপের নিজস্ব পপ-আপ কার্ড (SimCallBar) দেখানো হয়,
        // তাই হেডস-আপ নোটিফিকেশন নিঃশব্দ/নিচু গুরুত্বে (ডবল পপ-আপ নয়); লক বা স্ক্রিন বন্ধে আগের মতো ফুল-স্ক্রিন
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val locked = km?.isKeyguardLocked == true || pm?.isInteractive == false
        val overlayCard = !locked && android.provider.Settings.canDrawOverlays(this)
        val channelId = if (overlayCard) CHANNEL_QUIET_ID else CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SIM incoming calls", NotificationManager.IMPORTANCE_HIGH)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_QUIET_ID, "SIM incoming calls (in-app pop-up)", NotificationManager.IMPORTANCE_LOW)
            )
        }
        fun pi(code: Int, action: String?): PendingIntent {
            val i = Intent(this, BetaCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (action != null) putExtra(BetaCallActivity.EXTRA_ACTION, action)
            }
            return PendingIntent.getActivity(
                this, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val who = SimCallManager.ringingCall()?.let { callTitle(it) } ?: "Unknown"
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        b.setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming SIM call")
            .setContentText(who)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pi(1, null))
            .addAction(Notification.Action.Builder(null, "Decline", pi(3, BetaCallActivity.ACTION_DECLINE)).build())
            .addAction(Notification.Action.Builder(null, "Answer", pi(4, BetaCallActivity.ACTION_ANSWER)).build())
        if (!overlayCard) b.setFullScreenIntent(pi(2, null), true)
        try {
            nm.notify(NOTIF_ID, b.build())
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
            openCallScreen()
        }
    }

    private fun cancelRingingNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }
}

/** কলের নাম বা নম্বর */
fun callTitle(call: Call): String {
    val d = call.details
    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) d?.contactDisplayName else null
    if (!name.isNullOrBlank()) return name
    val cnap = d?.callerDisplayName
    if (!cnap.isNullOrBlank()) return cnap
    val num = d?.handle?.schemeSpecificPart
    return if (num.isNullOrBlank()) "Unknown" else num
}

/** জরুরি কল নম্বর: স্বয়ংক্রিয় রেকর্ডে কখনো নয় */
fun isEmergencyDigits(n: String): Boolean =
    n.filter { it.isDigit() } in setOf("112", "999", "911", "000", "110", "119")
