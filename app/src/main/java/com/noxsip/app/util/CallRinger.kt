package com.noxsip.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Single, deterministic Ringtone & Vibration controller for incoming SIP calls.
 * Guarantees only ONE ringtone plays and stops immediately when the call is answered, declined, or ended.
 */
object CallRinger {

    private const val TAG = "CallRinger"
    private var currentRingtone: Ringtone? = null
    private var currentVibrator: Vibrator? = null

    @Synchronized
    fun start(context: Context) {
        stop()
        val appContext = context.applicationContext
        // ফোনের রিংগার মোড মানি: সাইলেন্টে কিছু না, ভাইব্রেশন মোডে শুধু কাঁপে, স্বাভাবিকে রিংটোন + কাঁপা
        val ringerMode = try {
            (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
                ?: AudioManager.RINGER_MODE_NORMAL
        } catch (_: Exception) {
            AudioManager.RINGER_MODE_NORMAL
        }
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val playRingtone = ringerMode == AudioManager.RINGER_MODE_NORMAL
        if (playRingtone) try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.isLooping = true
                }
                ringtone.play()
                currentRingtone = ringtone
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
                currentVibrator = vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    @Synchronized
    fun stop() {
        try {
            currentRingtone?.stop()
        } catch (_: Exception) {
        }
        currentRingtone = null

        try {
            currentVibrator?.cancel()
        } catch (_: Exception) {
        }
        currentVibrator = null
    }
}
