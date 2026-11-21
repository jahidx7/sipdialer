package com.noxsip.app.util

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Decodes a user-selected audio file to a plain PCM WAV file.
 * Linphone's call player accepts WAV and selected Matroska audio formats; WAV is the
 * most portable option for the call player.
 */
object AudioToWavConverter {
    private const val TAG = "AudioToWavConverter"
    private const val TIMEOUT_US = 20_000L

    fun convert(context: Context, source: Uri, destination: File) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val temporary = File(destination.parentFile ?: context.filesDir, destination.name + ".part")
        try {
            extractor.setDataSource(context, source, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IOException("The selected file has no audio track")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("The selected audio format could not be read")
            val audioDecoder = MediaCodec.createDecoderByType(mime)
            decoder = audioDecoder
            audioDecoder.configure(inputFormat, null, null, 0)
            audioDecoder.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var bytesWritten = 0L
            var inputFinished = false
            var outputFinished = false
            val info = MediaCodec.BufferInfo()

            temporary.parentFile?.mkdirs()
            FileOutputStream(temporary).use { output ->
                output.write(ByteArray(44)) // WAV header is written after decoding.

                while (!outputFinished) {
                    if (!inputFinished) {
                        val inputIndex = audioDecoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = audioDecoder.getInputBuffer(inputIndex)
                                ?: throw IOException("Audio decoder input buffer is unavailable")
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                audioDecoder.queueInputBuffer(
                                    inputIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputFinished = true
                            } else {
                                audioDecoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime.coerceAtLeast(0L),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = audioDecoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = audioDecoder.outputFormat
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = audioDecoder.getOutputBuffer(outputIndex)
                            if (outputBuffer != null &&
                                info.size > 0 &&
                                (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                bytesWritten += writePcm16(outputBuffer, info, pcmEncoding, output)
                            }
                            outputFinished =
                                (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            audioDecoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }

            if (bytesWritten == 0L) throw IOException("No playable audio was decoded")
            if (bytesWritten > Int.MAX_VALUE.toLong() - 36L) {
                throw IOException("The selected audio file is too large")
            }
            writeWavHeader(temporary, sampleRate, channels, bytesWritten)
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            temporary.delete()
            Log.w(TAG, "Audio conversion failed: ${e.message}")
            throw e
        } finally {
            try {
                decoder?.stop()
            } catch (_: Exception) {
            }
            try {
                decoder?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun writePcm16(
        source: ByteBuffer,
        info: MediaCodec.BufferInfo,
        encoding: Int,
        output: FileOutputStream
    ): Long {
        val buffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)

        return when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                output.write(bytes)
                bytes.size.toLong()
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                var byteCount = 0L
                while (buffer.remaining() >= 3) {
                    var value = (buffer.get().toInt() and 0xFF) or
                        ((buffer.get().toInt() and 0xFF) shl 8) or
                        ((buffer.get().toInt() and 0xFF) shl 16)
                    if ((value and 0x800000) != 0) value = value or -0x1000000
                    value = value shr 8
                    output.write(value and 0xFF)
                    output.write((value ushr 8) and 0xFF)
                    byteCount += 2
                }
                byteCount
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                var byteCount = 0L
                while (buffer.remaining() >= 4) {
                    val value = buffer.int shr 16
                    output.write(value and 0xFF)
                    output.write((value ushr 8) and 0xFF)
                    byteCount += 2
                }
                byteCount
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                var byteCount = 0L
                while (buffer.remaining() >= 4) {
                    val sample = buffer.float.coerceIn(-1.0f, 1.0f)
                    val value = (sample * if (sample < 0) 32768f else 32767f).toInt()
                    output.write(value and 0xFF)
                    output.write((value ushr 8) and 0xFF)
                    byteCount += 2
                }
                byteCount
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                var byteCount = 0L
                while (buffer.hasRemaining()) {
                    val unsignedSample = buffer.get().toInt() and 0xFF
                    val value = (unsignedSample - 128) shl 8
                    output.write(value and 0xFF)
                    output.write((value ushr 8) and 0xFF)
                    byteCount += 2
                }
                byteCount
            }
            else -> throw IOException("The audio decoder returned an unsupported PCM format")
        }
    }

    private fun writeWavHeader(file: File, sampleRate: Int, channels: Int, dataSize: Long) {
        val safeChannels = channels.coerceIn(1, 8)
        val safeRate = sampleRate.coerceAtLeast(8_000)
        val blockAlign = safeChannels * 2
        RandomAccessFile(file, "rw").use { wav ->
            wav.seek(0)
            wav.writeBytes("RIFF")
            writeIntLe(wav, (36L + dataSize).toInt())
            wav.writeBytes("WAVE")
            wav.writeBytes("fmt ")
            writeIntLe(wav, 16)
            writeShortLe(wav, 1) // PCM
            writeShortLe(wav, safeChannels)
            writeIntLe(wav, safeRate)
            writeIntLe(wav, safeRate * blockAlign)
            writeShortLe(wav, blockAlign)
            writeShortLe(wav, 16)
            wav.writeBytes("data")
            writeIntLe(wav, dataSize.toInt())
        }
    }

    private fun writeShortLe(file: RandomAccessFile, value: Int) {
        file.write(value and 0xFF)
        file.write((value ushr 8) and 0xFF)
    }

    private fun writeIntLe(file: RandomAccessFile, value: Int) {
        file.write(value and 0xFF)
        file.write((value ushr 8) and 0xFF)
        file.write((value ushr 16) and 0xFF)
        file.write((value ushr 24) and 0xFF)
    }
}
