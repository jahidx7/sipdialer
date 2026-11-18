package com.noxsip.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Stores the global incoming SIP call auto-answer preference and its selected audio.
 * Auto-answer is disabled by default.
 */
object AutoAnswerPrefs {
    private const val PREFS_NAME = "sip_call_preferences"
    private const val KEY_AUTO_ANSWER = "auto_answer_incoming_sip_calls"
    private const val KEY_AUDIO_PATH = "auto_answer_audio_path"
    private const val KEY_AUDIO_NAME = "auto_answer_audio_name"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ANSWER, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ANSWER, enabled)
            .apply()
    }


    data class Schedule(val enabled: Boolean, val start: Int, val end: Int) {
        fun includes(minute: Int): Boolean = when {
            !enabled -> true
            start < end -> minute in start until end
            start > end -> minute >= start || minute < end
            else -> true
        }
    }

    fun schedule(context: Context): Schedule {
        val prefs = preferences(context)
        return Schedule(
            prefs.getBoolean("schedule_enabled", false),
            prefs.getInt("schedule_start", 9 * 60),
            prefs.getInt("schedule_end", 18 * 60)
        )
    }

    fun setSchedule(context: Context, value: Schedule) {
        preferences(context).edit()
            .putBoolean("schedule_enabled", value.enabled)
            .putInt("schedule_start", value.start)
            .putInt("schedule_end", value.end)
            .apply()
    }

    fun shouldAnswerNow(context: Context): Boolean {
        if (!isEnabled(context)) return false
        val now = java.util.Calendar.getInstance()
        return schedule(context).includes(
            now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        )
    }

    fun microphoneEnabled(context: Context): Boolean =
        preferences(context).getBoolean("auto_answer_microphone_enabled", true)

    fun setMicrophoneEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("auto_answer_microphone_enabled", enabled).apply()
    }

    fun audioPath(context: Context): String? {
        val path = preferences(context).getString(KEY_AUDIO_PATH, null) ?: return null
        return path.takeIf { File(it).isFile }
    }

    fun audioName(context: Context): String? =
        if (audioPath(context) != null) preferences(context).getString(KEY_AUDIO_NAME, null) else null

    /** Copies and normalizes a picked music/audio file into the app's private files directory. */
    fun importAudio(context: Context, uri: Uri): String {
        val name = displayName(context, uri)
        val output = File(context.filesDir, "auto_answer_audio.wav")
        AudioToWavConverter.convert(context, uri, output)
        preferences(context).edit()
            .putString(KEY_AUDIO_PATH, output.absolutePath)
            .putString(KEY_AUDIO_NAME, name)
            .apply()
        return name
    }

    fun clearAudio(context: Context) {
        val path = preferences(context).getString(KEY_AUDIO_PATH, null)
        if (path != null) File(path).delete()
        preferences(context).edit()
            .remove(KEY_AUDIO_PATH)
            .remove(KEY_AUDIO_NAME)
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Audio file"
    }
}
