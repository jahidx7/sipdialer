package com.noxsip.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.noxsip.app.CHANNEL_CALL_ID
import com.noxsip.app.MainActivity
import com.noxsip.app.R
import com.noxsip.app.receiver.CallActionReceiver

/**
 * নোটিফিকেশন হেল্পার
 */
object NotificationHelper {

    private const val INCOMING_CALL_NOTIFICATION_ID = 2001
    private const val ONGOING_CALL_NOTIFICATION_ID = 2002

    private fun displayNumber(callerAddress: String): String =
        callerAddress.removePrefix("sip:").removePrefix("sips:").substringBefore("@").ifBlank { callerAddress }

    private fun actionIntent(context: Context, action: String, callerAddress: String, requestCode: Int): PendingIntent {
        val i = Intent(context, CallActionReceiver::class.java).apply {
            this.action = action
            putExtra("caller_address", callerAddress)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun accountNotificationId(accountId: String) = 3000 + (accountId.hashCode() and 0xFFFF)

    /** SIP অ্যাকাউন্ট কানেক্ট হতে ব্যর্থ হলে জানায়, যাতে কল মিস হওয়ার আগে ধরা যায় */
    fun showAccountProblem(context: Context, accountId: String, accountName: String, detail: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                context, accountNotificationId(accountId), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
                .setContentTitle("SIP account not connected")
                .setContentText("$accountName: $detail")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$accountName: $detail\nYou may miss incoming calls on this account."))
                .setSmallIcon(R.drawable.ic_phone)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(accountNotificationId(accountId), n)
        } catch (_: Exception) {
        }
    }

    fun clearAccountProblem(context: Context, accountId: String) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(accountNotificationId(accountId))
        } catch (_: Exception) {
        }
    }

    /** আনলক ছাড়াই চলে এমন অ্যাকশন বাটন (লক স্ক্রিন থেকেও কাজ করে) */
    private fun action(icon: Int, title: String, pi: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(icon, title, pi)
            .setAuthenticationRequired(false)
            .build()

    /**
     * ইনকামিং কল নোটিফিকেশন দেখান (Answer / Decline বাটনসহ)
     */
    fun showIncomingCallNotification(context: Context, callerAddress: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incoming_call", true)
            putExtra("caller_address", callerAddress)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPi = actionIntent(context, CallActionReceiver.ACTION_ANSWER, callerAddress, 11)
        val declinePi = actionIntent(context, CallActionReceiver.ACTION_DECLINE, callerAddress, 12)

        val builder = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setContentTitle(context.getString(R.string.incoming_call))
            .setContentText(
                displayNumber(callerAddress) +
                    (com.noxsip.app.sip.SipEngine.getInstance(context).callAccountUser.value?.let { " • via $it" } ?: "")
            )
            .setSmallIcon(R.drawable.ic_phone)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            // লক স্ক্রিনে/স্ক্রিন বন্ধ থাকলে পূর্ণ স্ক্রিনে কল স্ক্রিন খোলে; ফোন যদি এটা আটকে দেয়, নিচের বাটন দিয়ে লক স্ক্রিন থেকেই রিসিভ/কাটা যায়
            .setFullScreenIntent(pendingIntent, true)
            .addAction(action(R.drawable.ic_call_end, "Decline", declinePi))
            .addAction(action(R.drawable.ic_phone, "Answer", answerPi))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build())
    }

    /**
     * রিসিভ করার পর চলমান কলের নোটিফিকেশন (Hang up বাটনসহ); কল শেষে cancelOngoingCallNotification
     */
    fun showOngoingCallNotification(context: Context, callerAddress: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_native_dialer", true)
        }
        val contentPi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val hangupPi = actionIntent(context, CallActionReceiver.ACTION_HANGUP, callerAddress, 13)

        val builder = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setContentTitle("Call in progress")
            .setContentText(displayNumber(callerAddress))
            .setSmallIcon(R.drawable.ic_phone)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(contentPi)
            .addAction(action(R.drawable.ic_call_end, "Hang up", hangupPi))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ONGOING_CALL_NOTIFICATION_ID, builder.build())
    }

    /**
     * ইনকামিং কল নোটিফিকেশন বাতিল করুন
     */
    fun cancelIncomingCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    fun cancelOngoingCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ONGOING_CALL_NOTIFICATION_ID)
    }
}
