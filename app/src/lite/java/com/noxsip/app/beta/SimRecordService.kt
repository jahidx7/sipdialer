package com.noxsip.app.beta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Beta (পরীক্ষামূলক): SIM কল রেকর্ড। শুধু মাইক থেকে ধরে; অপর পাশের কথা ওঠে কেবল স্পিকারে থাকলে।
 * কিছু ফোনে কলের সময় মাইক বন্ধ থাকে, তখন ফাইল ফাঁকা আসতে পারে। রেকর্ডিং নোটিফিকেশন ও মাইক ইন্ডিকেটর লুকানো যায় না।
 * ফাইল যায় Recordings/SipDialer (Android 12+) বা Music/SipDialer-এ, নাম SIM_<নম্বর>_<সময়>.m4a।
 */
class SimRecordService : Service() {

    companion object {
        private const val TAG = "SimRecordService"
        const val EXTRA_LABEL = "label"
        private const val CHANNEL = "beta_sim_record"
        private const val NOTIF_ID = 7102

        fun start(context: Context, label: String, silent: Boolean = false) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (!silent) {
                    Toast.makeText(context, "Microphone permission is needed to record", Toast.LENGTH_LONG).show()
                }
                return
            }
            try {
                val i = Intent(context, SimRecordService::class.java).putExtra(EXTRA_LABEL, label)
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                // পটভূমি থেকে ফোরগ্রাউন্ড সার্ভিস চালু করা ফোন আটকালে (স্বয়ংক্রিয় শুরুতে) চুপচাপ বাদ
                Log.w(TAG, "could not start recording service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SimRecordService::class.java))
        }
    }

    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var label = "call"
    private val main = Handler(Looper.getMainLooper())

    // পরীক্ষা: কলের সময় মাইক খোলে এমন অডিও সোর্স খোঁজা। সাউন্ড (amplitude) শূন্য থাকলে পরের সোর্সে যাই
    private val sources = listOf(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
        MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
        MediaRecorder.AudioSource.MIC to "MIC",
        MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED"
    )
    private var sourceIndex = 0
    private var heardSound = false
    private var sampleTicks = 0

    private val sampler = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            val amp = try { r.maxAmplitude } catch (_: Exception) { 0 }
            if (amp > 300) heardSound = true
            sampleTicks++
            // ~৩ সেকেন্ড (৬ x 500ms) পরেও নীরব হলে পরের সোর্সে
            if (!heardSound && sampleTicks >= 6 && sourceIndex < sources.size - 1) {
                Log.i(TAG, "source ${sources[sourceIndex].second} silent, switching")
                switchToNextSource()
            } else {
                main.postDelayed(this, 500)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "call" }
        startAsForeground()
        if (recorder == null && !startRecorder()) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "SIM call recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val n = b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording SIM call")
            .setContentText("Microphone only (use speaker to capture both sides)")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun startRecorder(): Boolean {
        val ok = startRecorderWith(sources[sourceIndex].first)
        if (ok) {
            heardSound = false
            sampleTicks = 0
            main.postDelayed(sampler, 500)
        }
        return ok
    }

    /** চলমান সোর্স নীরব: ওই অংশ ফেলে দিয়ে পরের সোর্সে নতুন ফাইল শুরু */
    private fun switchToNextSource() {
        val r = recorder
        recorder = null
        try { r?.stop() } catch (_: RuntimeException) {}
        try { r?.release() } catch (_: Exception) {}
        tempFile?.delete()
        tempFile = null
        sourceIndex++
        if (!startRecorderWith(sources[sourceIndex].first)) {
            stopSelf()
            return
        }
        heardSound = false
        sampleTicks = 0
        main.postDelayed(sampler, 500)
    }

    private fun startRecorderWith(audioSource: Int): Boolean {
        return try {
            val file = File(cacheDir, "sim_rec_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(audioSource)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            tempFile = file
            SimCallManager.recording.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            Toast.makeText(this, "Could not start recording", Toast.LENGTH_LONG).show()
            false
        }
    }

    override fun onDestroy() {
        finishRecording()
        SimCallManager.recording.value = false
        super.onDestroy()
    }

    private fun finishRecording() {
        main.removeCallbacks(sampler)
        val r = recorder ?: return
        recorder = null
        val file = tempFile
        tempFile = null
        var valid = true
        try {
            r.stop()
        } catch (e: RuntimeException) {
            valid = false
        }
        try {
            r.release()
        } catch (_: Exception) {
        }
        if (file == null) return
        if (!valid || file.length() < 2048) {
            file.delete()
            Toast.makeText(applicationContext, "Recording was empty (the phone blocked the mic)", Toast.LENGTH_LONG).show()
            return
        }
        val digits = label.filter { it.isDigit() || it == '+' }.ifBlank { "call" }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val srcName = sources[sourceIndex].second
        val name = "SIM_${digits}_${stamp}_$srcName.m4a"
        val where = export(file, name)
        main.post {
            Toast.makeText(
                applicationContext,
                if (where != null) {
                    "SIM call saved ($srcName, " + (if (heardSound) "sound detected" else "no sound detected") + "): $where"
                } else {
                    "Could not save the recording"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** সফল হলে ফোল্ডারের নাম ফেরত দেয়, নইলে null */
    private fun export(src: File, name: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Recordings/SipDialer" else "Music/SipDialer"
                val resolver = applicationContext.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, dir)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException("no output stream")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    throw e
                }
                src.delete()
                dir
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
                src.copyTo(File(dir, name), overwrite = true)
                src.delete()
                dir.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "export failed", e)
            null
        }
    }
}
