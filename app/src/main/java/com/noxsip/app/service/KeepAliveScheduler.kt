package com.noxsip.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.noxsip.app.sip.SipEngine
import com.noxsip.app.util.PowerPrefs

/** Balanced মোডে: CPU ঘুমিয়ে থাকে, Alarm দিয়ে নির্দিষ্ট বিরতিতে অল্প সময় জেগে রেজিস্ট্রেশন রিফ্রেশ করে */
object KeepAliveScheduler {
    private const val TAG = "KeepAliveScheduler"
    private const val ACTION = "com.noxsip.app.action.KEEPALIVE"
    private const val REQUEST_CODE = 7001
    const val INTERVAL_MS = 30_000L

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext, REQUEST_CODE,
            Intent(context.applicationContext, KeepAliveReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context, delayMs: Long = INTERVAL_MS) {
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = SystemClock.elapsedRealtime() + delayMs
        val pi = pendingIntent(context)
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } catch (e: Exception) {
            Log.w(TAG, "schedule failed: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        try {
            val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(context))
        } catch (_: Exception) {
        }
    }
}

class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = PowerPrefs.mode(context)
        // প্রহরী: সার্ভিস মরে গেছে অথচ ব্যবহারকারী বন্ধ করেননি, তাহলে আবার চালু
        if (!SipForegroundService.isActive() && !KeepAliveState.stoppedByUser(context)) {
            Log.i("KeepAliveReceiver", "service not running, reviving")
            SipForegroundService.start(context.applicationContext)
            KeepAliveScheduler.schedule(context)
            return
        }
        // রিলায়েবল মোড: CPU আগে থেকেই জাগানো, শুধু প্রহরী অ্যালার্ম ঘুরিয়ে দিই
        if (mode == PowerPrefs.RELIABLE) {
            KeepAliveScheduler.schedule(context)
            return
        }
        if (!SipForegroundService.isActive()) return
        // অল্প সময়ের জন্য CPU জাগিয়ে রেখে রেজিস্ট্রেশন রিফ্রেশ
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SipDialer:KeepAlive").apply {
                setReferenceCounted(false)
                acquire(8_000L)
            }
        } catch (_: Exception) {
        }
        try {
            SipEngine.getInstance(context.applicationContext).keepAliveTick()
        } catch (e: Exception) {
            Log.w("KeepAliveReceiver", "tick failed: ${e.message}")
        }
        KeepAliveScheduler.schedule(context)
    }
}
