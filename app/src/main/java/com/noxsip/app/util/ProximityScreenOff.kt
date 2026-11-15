package com.noxsip.app.util

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * কথা বলার সময় ফোন কানের কাছে গেলে (প্রক্সিমিটি সেন্সর) স্ক্রিন বন্ধ করে, সরালে আবার চালু করে।
 * active = true হলে এবং স্ক্রিনটা সামনে (RESUMED) থাকলেই লক ধরা হয়; ব্লুটুথ/তারের হেডসেট থাকলে ধরা হয় না।
 * কল স্ক্রিন বন্ধ/ব্যাকগ্রাউন্ডে গেলে বা active=false হলে ছেড়ে দেয়।
 */
@Composable
fun ProximityScreenOffEffect(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active, context) {
        val owner = findLifecycleOwner(context)
        var lock: PowerManager.WakeLock? = null

        fun acquire() {
            if (lock?.isHeld == true) return
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
                if (isHeadsetOutputConnected(context)) return
                lock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "NoxSIP:Proximity").apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1000L) // নিরাপত্তা-সীমা: ৪ ঘণ্টা
                }
            } catch (e: Exception) {
                Log.w("Proximity", "acquire failed", e)
            }
        }

        fun release() {
            try {
                lock?.takeIf { it.isHeld }?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            } catch (e: Exception) {
                Log.w("Proximity", "release failed", e)
            }
            lock = null
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (active) acquire()
                Lifecycle.Event.ON_PAUSE -> release()
                else -> {}
            }
        }

        if (active && (owner == null || owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
            acquire()
        }
        owner?.lifecycle?.addObserver(observer)

        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            release()
        }
    }
}

private fun findLifecycleOwner(context: Context): LifecycleOwner? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

private fun isHeadsetOutputConnected(context: Context): Boolean {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        when (it.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> true
            else -> false
        }
    }
}
