package com.noxsip.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * ব্যাটারি অপটিমাইজেশন থেকে অ্যাপকে বাদ (whitelist) দেওয়ার হেল্পার।
 * না দিলে Doze/Vivo ব্যাকগ্রাউন্ড সীমাবদ্ধতায় SIP রেজিস্ট্রেশন মরে যায় ও ইনকামিং কল আসে না।
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /** সরাসরি "Allow" ডায়ালগ খোলে; না খুললে সাধারণ ব্যাটারি-অপটিমাইজেশন সেটিংস */
    fun request(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }
}
