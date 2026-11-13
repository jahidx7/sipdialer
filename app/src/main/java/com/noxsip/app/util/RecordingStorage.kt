package com.noxsip.app.util

import android.content.ContentValues
import android.content.Context
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
}
