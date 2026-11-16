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
