package com.noxsip.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/** Private storage for an explicitly user-started, one-pass SIP call list. */
object AutoCallPrefs {
    const val MAX_RECIPIENTS = 50
    private const val PREFS = "sip_auto_call"
    private const val KEY_NUMBERS = "numbers"
    private const val KEY_AUDIO_PATH = "audio_path"
    private const val KEY_AUDIO_NAME = "audio_name"
    private const val MAX_IMPORT_BYTES = 12 * 1024 * 1024

    fun numbers(context: Context): List<String> = parseNumbers(
        prefs(context).getString(KEY_NUMBERS, "").orEmpty()
    )

    fun saveNumbers(context: Context, numbers: List<String>) {
        prefs(context).edit().putString(KEY_NUMBERS, numbers.take(MAX_RECIPIENTS).joinToString("\n")).apply()
    }

    fun voicePath(context: Context): String? = prefs(context).getString(KEY_AUDIO_PATH, null)
        ?.takeIf { File(it).isFile }

    fun voiceName(context: Context): String? = if (voicePath(context) != null) {
        prefs(context).getString(KEY_AUDIO_NAME, null)
    } else null

    fun importVoice(context: Context, uri: Uri): String {
        val name = displayName(context, uri)
        val output = File(context.filesDir, "auto_call_voice.wav")
        AudioToWavConverter.convert(context, uri, output)
        prefs(context).edit().putString(KEY_AUDIO_PATH, output.absolutePath).putString(KEY_AUDIO_NAME, name).apply()
        return name
    }

    fun clearVoice(context: Context) {
        voicePath(context)?.let { File(it).delete() }
        prefs(context).edit().remove(KEY_AUDIO_PATH).remove(KEY_AUDIO_NAME).apply()
    }

    fun importNumbers(context: Context, uri: Uri): List<String> {
        val name = displayName(context, uri).lowercase()
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected file")
        val text = source.use {
            when {
                name.endsWith(".xlsx") -> parseXlsx(it.readBytesBounded())
                name.endsWith(".csv") || name.endsWith(".txt") -> parseDelimited(it.readBytesBounded().toString(Charsets.UTF_8))
                else -> throw IllegalArgumentException("Choose an .xlsx, .csv, or .txt file")
            }
        }
        val result = parseNumbers(text)
        if (result.isEmpty()) throw IllegalArgumentException("No valid phone numbers were found")
        return result
    }

    /** Accepts one number per line or separated by comma, semicolon, or tab. */
    fun parseNumbers(raw: String): List<String> {
        val normalized = raw.map { ch ->
            if (ch.isDigit()) Character.digit(ch, 10).takeIf { it in 0..9 }?.let { ('0'.code + it).toChar() } ?: ch else ch
        }.joinToString("")
        val output = LinkedHashSet<String>()
        normalized.split(Regex("[\\r\\n,;\\t|]+"))
            .asSequence()
            .map { token ->
                val compact = token.trim().trim('"', '\'').filter { it.isDigit() || it == '+' }
                val digits = compact.count(Char::isDigit)
                when {
                    digits !in 7..15 -> null
                    compact.indexOf('+') > 0 -> compact.replace("+", "")
                    compact.count { it == '+' } > 1 -> "+" + compact.filter(Char::isDigit)
                    else -> compact
                }
            }
            .filterNotNull()
            .forEach { output.add(it) }
        return output.take(MAX_RECIPIENTS)
    }

    /** Duration of the normalized 44-byte PCM WAV used by the SIP call player. */
    fun voiceDurationMs(path: String): Long? {
        return try {
            val file = File(path)
            if (!file.isFile || file.length() <= 44L) return null
            val header = ByteArray(44)
            file.inputStream().use { input -> if (input.read(header) != header.size) return null }
            val byteRate = littleEndianInt(header, 28).toLong() and 0xffffffffL
            val pcmBytes = (file.length() - 44L).coerceAtLeast(0L)
            if (byteRate <= 0L || pcmBytes <= 0L) null else (pcmBytes * 1000L / byteRate).coerceAtLeast(500L)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDelimited(text: String): String = buildString {
        csvCells(text).forEach { append(it).append('\n') }
    }

    private fun csvCells(text: String): List<String> {
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && (ch == ',' || ch == ';' || ch == '\t' || ch == '\r' || ch == '\n') -> {
                    cells.add(cell.toString())
                    cell.setLength(0)
                }
                else -> cell.append(ch)
            }
            i++
        }
        cells.add(cell.toString())
        return cells
    }

    private fun parseXlsx(zipBytes: ByteArray): String {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/sharedStrings.xml" ||
                    entry.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))
                ) {
                    entries[entry.name] = zip.readBytesBounded()
                }
                zip.closeEntry()
            }
        }
        val sheetName = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .sortedBy { it.substringAfter("sheet").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
            .firstOrNull() ?: throw IllegalArgumentException("The Excel file has no worksheet")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val values = ArrayList<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(entries.getValue(sheetName)), "UTF-8")
        var event = parser.eventType
        var inCell = false
        var cellType = ""
        var cellValue = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> {
                        inCell = true
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = StringBuilder()
                    }
                    "v", "t" -> if (inCell) cellValue.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "c" && inCell) {
                    val raw = cellValue.toString()
                    values.add(if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw)
                    inCell = false
                }
            }
            event = parser.next()
        }
        return values.joinToString("\n")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = ArrayList<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var event = parser.eventType
        var inItem = false
        var value = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; value = StringBuilder() }
                    "t" -> if (inItem) value.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si" && inItem) {
                    strings.add(value.toString())
                    inItem = false
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_IMPORT_BYTES) throw IllegalArgumentException("File is larger than 12 MB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "File"
    }
}
