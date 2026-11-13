package com.noxsip.app.util

import android.content.Context

/**
 * অডিও কোয়ালিটি সেটিং।
 * HD কোডেক (Opus / G.722) চালু করলে কল-অফারে সেগুলো আগে যায়; সার্ভার সমর্থন না করলে স্বয়ংক্রিয়ভাবে G.711 (PCMA/PCMU) ব্যবহার হবে।
 */
object AudioQualityPrefs {
    private const val PREFS_NAME = "audio_quality_prefs"
    private const val KEY_HD_CODECS = "hd_codecs_enabled"

    fun isHdCodecsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HD_CODECS, false)

    private const val KEY_MIC_BOOST_DB = "mic_boost_db"
    const val DEFAULT_MIC_BOOST_DB = 10
    const val MAX_MIC_BOOST_DB = 20

    /** সফটওয়্যার মাইক গেইন (dB)। ফোনের মাইক দূর থেকে খুব দুর্বল ধরলে বাড়ান; বেশি হলে ক্লিপিং/ডিস্টরশন হবে। */
    fun getMicBoostDb(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MIC_BOOST_DB, DEFAULT_MIC_BOOST_DB).coerceIn(0, MAX_MIC_BOOST_DB)

    fun setMicBoostDb(context: Context, db: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MIC_BOOST_DB, db.coerceIn(0, MAX_MIC_BOOST_DB)).apply()
    }

    private const val KEY_CODEC_CONFIG = "codec_config"

    /** একটি কোডেকের কনফিগ: key = "MIME/clockRate" (যেমন "PCMA/8000"), enabled = চালু কি না */
    data class CodecEntry(val key: String, val enabled: Boolean)

    /** ইউজারের নিজের সাজানো কোডেক তালিকা (ক্রমসহ); null হলে ডিফল্ট লজিক (HD সুইচ) চলে */
    fun getCodecConfig(context: Context): List<CodecEntry>? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CODEC_CONFIG, null)
            ?: return null
        val list = raw.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2 && parts[0].isNotBlank()) CodecEntry(parts[0], parts[1] == "1") else null
        }
        return list.ifEmpty { null }
    }

    fun setCodecConfig(context: Context, list: List<CodecEntry>?) {
        val e = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (list == null) e.remove(KEY_CODEC_CONFIG)
        else e.putString(KEY_CODEC_CONFIG, list.joinToString(",") { it.key + ":" + (if (it.enabled) "1" else "0") })
        e.apply()
    }

    fun setHdCodecsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HD_CODECS, enabled).apply()
    }
}
