package com.noxsip.app.beta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Call
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.noxsip.app.telecom.TelecomHelper

/**
 * কলটি কোন SIM-এ হচ্ছে: "SIM 1" / "SIM 2"। জানা না গেলে "SIM"।
 * আগে সাবস্ক্রিপশন আইডি থেকে আসল স্লট, না মিললে ফোনের কল-সক্ষম অ্যাকাউন্টের তালিকার সাথে মিলিয়ে।
 */
fun simSlotLabel(context: Context, call: Call): String {
    return try {
        val handle = call.details?.accountHandle ?: return "SIM"
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val subId = handle.id?.toIntOrNull()
            if (subId != null) {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                val info = sm?.getActiveSubscriptionInfo(subId)
                if (info != null && info.simSlotIndex >= 0) return "SIM ${info.simSlotIndex + 1}"
            }
            TelecomHelper.getAvailableSimAccounts(context)
                .firstOrNull { it.handle == handle }
                ?.let { return "SIM ${it.slotIndex}" }
        }
        "SIM"
    } catch (e: Exception) {
        "SIM"
    }
}
