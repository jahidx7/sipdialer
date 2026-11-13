package com.noxsip.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.noxsip.app.data.AccountRepository
import com.noxsip.app.service.SipForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * বুট রিসিভার - ফোন রিস্টার্ট হলে SIP সার্ভিস অটো-স্টার্ট
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Boot/update (${intent.action}), checking account")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = AccountRepository(context)
                    val account = repository.getAccount().first()

                    // বেটা: অ্যাকাউন্ট থাকলে এবং ব্যবহারকারী নিজে বন্ধ না করলে (autoStart অফ থাকলেও) চালু
                    if (account != null && !com.noxsip.app.service.KeepAliveState.stoppedByUser(context)) {
                        SipForegroundService.start(context)
                        Log.i(TAG, "SIP service auto-started")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-start SIP service", e)
                }
            }
        }
    }
}
