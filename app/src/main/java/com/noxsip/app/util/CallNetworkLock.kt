package com.noxsip.app.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * কল চলাকালীন Wi-Fi পাওয়ার-সেভ বন্ধ রাখে (low-latency / high-perf WifiLock), যাতে জিটার ও প্যাকেট ডিলে কমে।
 * acquire/release একাধিকবার ডাকলেও নিরাপদ।
 */
object CallNetworkLock {
    private const val TAG = "CallNetworkLock"
    private var wifiLock: WifiManager.WifiLock? = null

    @Synchronized
    fun acquire(context: Context) {
        try {
            if (wifiLock?.isHeld == true) return
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, "SipDialer:CallWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "Wi-Fi lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi lock acquire failed: ${e.message}")
        }
    }

    // ---- স্ট্যান্ডবাই লক: SIP রেজিস্টার থাকা পুরো সময় Wi-Fi পাওয়ার-সেভ বন্ধ রাখে ----
    // স্ক্রিন বন্ধ থাকলে Wi-Fi ঘুমিয়ে গেলে INVITE পৌঁছায় না; কল-লক (acquire/release) থেকে আলাদা রাখা হয়েছে।
    private var standbyLock: WifiManager.WifiLock? = null

    @Synchronized
    fun acquireStandby(context: Context) {
        try {
            if (standbyLock?.isHeld == true) return
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            standbyLock = wifi.createWifiLock(mode, "SipDialer:StandbyWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "Standby Wi-Fi lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Standby Wi-Fi lock acquire failed: ${e.message}")
        }
    }

    @Synchronized
    fun releaseStandby() {
        try {
            standbyLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Standby Wi-Fi lock release failed: ${e.message}")
        } finally {
            standbyLock = null
        }
    }

    @Synchronized
    fun release() {
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
            Log.d(TAG, "Wi-Fi lock released")
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi lock release failed: ${e.message}")
        } finally {
            wifiLock = null
        }
    }
}
