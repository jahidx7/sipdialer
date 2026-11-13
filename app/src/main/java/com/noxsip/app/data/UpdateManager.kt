package com.noxsip.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * অ্যাপ আপডেট: BuildConfig.VERSION_URL-এর version.json পড়ে নতুন ভার্সন থাকলে APK নামিয়ে ইনস্টলার খোলে।
 * VERSION_URL ফাঁকা থাকলে (ডিফল্ট) আপডেট চেক বন্ধ।
 * version.json: {"versionCode":19,"versionName":"1.3.0","apkUrl":"https://example.com/app/NoxSIP-v1.3.0.apk","notes":"...","sha256":"<64 hex>"}
 */
object UpdateManager {
    /** pub সংস্করণে VPS-এর version.json; full সংস্করণে খালি (আপডেট চেক বন্ধ, হাতে ইনস্টল) */
    val VERSION_URL: String = com.noxsip.app.BuildConfig.VERSION_URL

    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val notes: String,
        val sha256: String
    )

    fun currentVersionCode(context: Context): Long {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    /** নতুন ভার্সন থাকলে UpdateInfo, না থাকলে null */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        if (VERSION_URL.isBlank()) return@withContext null
        val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            useCaches = false
        }
        try {
            if (conn.responseCode != 200) throw Exception("Server returned ${conn.responseCode}")
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val info = UpdateInfo(
                j.getLong("versionCode"),
                j.optString("versionName", ""),
                j.getString("apkUrl"),
                j.optString("notes", ""),
                j.optString("sha256", "").lowercase()
            )
            if (info.versionCode > currentVersionCode(context)) info else null
        } finally {
            conn.disconnect()
        }
    }

    /** APK নামায় (cache/updates/), progress 0..100 */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (!info.apkUrl.startsWith("https://")) throw Exception("Update link must use HTTPS")
            if (info.sha256.length != 64) throw Exception("Update is missing a SHA-256 checksum")
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "NoxSIP-${info.versionName}.apk")
            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
            }
            try {
                if (conn.responseCode != 200) throw Exception("Download failed (${conn.responseCode})")
                val total = conn.contentLengthLong
                var done = 0L
                var last = -1
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { input ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) { last = pct; onProgress(pct) }
                            }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != info.sha256) {
                    out.delete()
                    throw Exception("Checksum mismatch, the download was rejected")
                }
                out
            } finally {
                conn.disconnect()
            }
        }

    fun canInstall(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** "Install unknown apps" পারমিশন সেটিংস খোলে */
    fun openInstallPermissionSettings(context: Context) {
        val i = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }
}
