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
    const val MAX_MANUAL_RECIPIENTS = 50
    private const val PREFS = "sip_auto_call"
    private const val KEY_NUMBERS = "numbers"
    private const val KEY_AUDIO_PATH = "audio_path"
    private const val KEY_AUDIO_NAME = "audio_name"
    private const val MAX_IMPORT_BYTES = 12 * 1024 * 1024

    data class ImportResult(
        val fileName: String,
        val numbers: List<String>,
        val scannedValues: Int,
        val invalidValues: Int,
        val duplicates: Int
    )

    fun numbers(context: Context): List<String> = parseNumbers(
        prefs(context).getString(KEY_NUMBERS, "").orEmpty()
    )

    fun saveNumbers(context: Context, numbers: List<String>) {
        prefs(context).edit().putString(KEY_NUMBERS, numbers.take(MAX_MANUAL_RECIPIENTS).joinToString("\n")).apply()
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

    fun importNumbers(context: Context, uri: Uri): ImportResult {
        val fileName = displayName(context, uri)
        val name = fileName.lowercase()
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected file")
        val bytes = source.use { it.readBytesBounded() }
        val text = when {
            name.endsWith(".xlsx") || bytes.startsWithZipSignature() -> parseXlsx(bytes)
            name.endsWith(".xls") -> throw IllegalArgumentException("Legacy .xls files are not supported. Save this workbook as .xlsx or CSV and import it again.")
            bytes.isReadableText() -> parseDelimited(bytes.decodeUtf8())
            else -> throw IllegalArgumentException("This file is not a readable spreadsheet or text file. Import .xlsx, .csv, .tsv, .txt, .json, .xml, .vcf, or another plain-text file.")
        }
        val result = scanNumbers(text, maxRecipients = null)
        if (result.numbers.isEmpty()) throw IllegalArgumentException("No Bangladesh mobile numbers were found. Use +8801XXXXXXXXX or 01XXXXXXXXX.")
        return result.copy(fileName = fileName)
    }

    /** Accepts one number per line or separated by comma, semicolon, or tab. */
    fun parseNumbers(raw: String): List<String> = scanNumbers(raw, MAX_MANUAL_RECIPIENTS).numbers

    private fun scanNumbers(raw: String, maxRecipients: Int?): ImportResult {
        val output = LinkedHashSet<String>()
        var scanned = 0
        var invalid = 0
        var duplicates = 0
        raw.split(Regex("[\\r\\n,;\\t|]+"))
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { token ->
                scanned++
                val number = normalizeBangladeshMobile(token)
                if (number == null) {
                    invalid++
                } else if (number in output) {
                    duplicates++
                } else if (maxRecipients == null || output.size < maxRecipients) {
                    output.add(number)
                }
            }
        return ImportResult("", output.toList(), scanned, invalid, duplicates)
    }

    private fun normalizeBangladeshMobile(raw: String): String? {
        val converted = raw.map { ch ->
            if (ch.isDigit()) Character.digit(ch, 10).takeIf { it in 0..9 }?.let { ('0'.code + it).toChar() } ?: ch else ch
        }.joinToString("")
        val digits = converted.filter(Char::isDigit)
        return when {
            digits.length == 13 && digits.startsWith("8801") -> "+$digits"
            digits.length == 11 && digits.startsWith("01") -> "+880${digits.drop(1)}"
            // Excel may strip the display-leading zero when a phone cell was stored as a number.
            digits.length == 10 && digits.startsWith("1") -> "+880$digits"
            else -> null
        }
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
        val sheetNames = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .sortedBy { it.substringAfter("sheet").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
        if (sheetNames.isEmpty()) throw IllegalArgumentException("The Excel file has no worksheet")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val values = ArrayList<String>()
        sheetNames.forEach { sheetName ->
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

    private fun ByteArray.startsWithZipSignature(): Boolean =
        size >= 4 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()

    private fun ByteArray.isReadableText(): Boolean {
        if (isEmpty() || any { it == 0.toByte() }) return false
        val sampleLength = minOf(size, 4096)
        val controls = take(sampleLength).count { byte ->
            val value = byte.toInt() and 0xff
            value < 0x09 || value in 0x0e..0x1f
        }
        return controls * 20 < sampleLength
    }

    private fun ByteArray.decodeUtf8(): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(this))
            .toString()
    } catch (_: Exception) {
        throw IllegalArgumentException("Could not read this file as UTF-8 text. Save it as CSV or UTF-8 text and import it again.")
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
