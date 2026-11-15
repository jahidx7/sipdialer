package com.noxsip.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * কল রেকর্ডিং ফোনের শেয়ার্ড স্টোরেজে (ফাইল ম্যানেজার ও গ্যালারি/মিউজিক অ্যাপে দেখা যায়) সংরক্ষণ করে।
 *
 * Linphone শুধু ফাইল-পাথে রেকর্ড করতে পারে, তাই কল চলাকালীন অ্যাপের অস্থায়ী ফাইলে রেকর্ড হয়,
 * কল শেষে সেটি MediaStore-এ কপি করে অস্থায়ী ফাইল মুছে ফেলা হয়।
 *   Android 12+ : Recordings/SipDialer
 *   Android 10-11: Music/SipDialer
 *   Android 8-9 : Music/SipDialer (WRITE_EXTERNAL_STORAGE পারমিশন থাকলে)
 */
object RecordingStorage {
    private const val TAG = "RecordingStorage"
    private const val FOLDER = "SipDialer"

    private fun relativeDir(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Recordings/$FOLDER" else "Music/$FOLDER"

    /** সফল হলে content:// URI (বা পুরনো Android-এ ফাইল পাথ) ফেরত দেয়, ব্যর্থ হলে null। */
    fun exportToPublicStorage(context: Context, source: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, source.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativeDir())
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        source.inputStream().use { it.copyTo(out) }
                    } ?: throw IllegalStateException("openOutputStream returned null")
                    val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } catch (e: Exception) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    throw e
                }
                source.delete()
                uri.toString()
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
                if (!dir.exists() && !dir.mkdirs()) return null
                val target = File(dir, source.name)
                source.copyTo(target, overwrite = true)
                source.delete()
                target.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export to public storage failed, keeping private copy: ${e.message}")
            null
        }
    }

    fun exists(context: Context, path: String): Boolean {
        if (path.isEmpty()) return false
        return try {
            if (path.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
            } else {
                File(path).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun setDataSource(player: MediaPlayer, context: Context, path: String) {
        if (path.startsWith("content://")) {
            player.setDataSource(context, Uri.parse(path))
        } else {
            player.setDataSource(path)
        }
    }

    /**
     * প্লে শুরু করা MediaPlayer ফেরত দেয়। কিছু ফোনে সরাসরি পাথ/URI-তে ব্যর্থ হয়, তাই দুই উপায়ে চেষ্টা করি:
     * ১) সরাসরি সোর্স, ২) ফাইল-ডিস্ক্রিপ্টর দিয়ে। দুটোই ব্যর্থ হলে null (কলার বাইরের প্লেয়ারে খুলবে)।
     */
    fun startPlayer(
        context: Context,
        path: String,
        onCompletion: () -> Unit,
        onError: () -> Unit
    ): RecPlayer? {
        // 1) own WAV player (does not depend on the phone's MediaPlayer)
        WavPlayer.open(context, path, onCompletion, onError)?.let { return it }
        // 2) system MediaPlayer, two ways
        for (attempt in 0..1) {
            var mp: MediaPlayer? = null
            try {
                mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (attempt == 0) {
                    setDataSource(mp, context, path)
                } else {
                    val pfd = if (path.startsWith("content://")) {
                        context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
                    } else {
                        android.os.ParcelFileDescriptor.open(File(path), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } ?: throw IllegalStateException("cannot open file descriptor")
                    pfd.use { mp.setDataSource(it.fileDescriptor) }
                }
                mp.setOnCompletionListener { onCompletion() }
                mp.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer runtime error what=$what extra=$extra")
                    onError()
                    true
                }
                mp.prepare()
                mp.start()
                return MediaPlayerRec(mp)
            } catch (e: Exception) {
                Log.e(TAG, "Playback attempt ${attempt + 1} failed for $path: ${e.javaClass.simpleName}: ${e.message}")
                try { mp?.release() } catch (_: Exception) {}
            }
        }
        return null
    }

    /** অ্যাপের প্লেয়ার ব্যর্থ হলে ফোনের অন্য অডিও প্লেয়ারে (ফাইল ম্যানেজারের মতো) খোলে। সফল হলে true। */
    fun openExternally(context: Context, path: String): Boolean {
        return try {
            val uri = if (path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "openExternally failed: ${e.message}")
            false
        }
    }
}
