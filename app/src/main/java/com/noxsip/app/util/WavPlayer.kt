package com.noxsip.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

/** রেকর্ডিং প্লেয়ারের সাধারণ ইন্টারফেস (নিজস্ব WAV প্লেয়ার বা সিস্টেম MediaPlayer) */
interface RecPlayer {
    fun positionMs(): Int
    fun durationMs(): Int
    fun seekTo(ms: Int)
    fun release()
}

/** সিস্টেম MediaPlayer-কে RecPlayer হিসেবে মোড়ানো */
class MediaPlayerRec(private val mp: MediaPlayer) : RecPlayer {
    override fun positionMs(): Int = try { mp.currentPosition } catch (_: Exception) { 0 }
    override fun durationMs(): Int = try { mp.duration } catch (_: Exception) { 0 }
    override fun seekTo(ms: Int) {
        try { mp.seekTo(ms) } catch (_: Exception) {}
    }
    override fun release() {
        try { mp.stop() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
    }
}

/**
 * অ্যাপের নিজস্ব WAV প্লেয়ার: ফোনের MediaPlayer/কোডেকের ওপর নির্ভর না করে PCM সরাসরি AudioTrack-এ বাজায়।
 * সমর্থিত: PCM 8/16-bit (মনো/স্টেরিও), A-law ও mu-law। হেডারে ডেটার আকার ভুল/শূন্য থাকলেও ফাইলের শেষ পর্যন্ত বাজায়।
 */
class WavPlayer private constructor(
    private val pfd: ParcelFileDescriptor,
    private val channel: FileChannel,
    private val rate: Int,
    private val channels: Int,
    private val tag: Int,
    private val bits: Int,
    private val dataStart: Long,
    dataLen: Long,
    private val onCompletion: () -> Unit,
    private val onError: () -> Unit
) : RecPlayer {

    private val ui = Handler(Looper.getMainLooper())
    private val frameSize = channels * (bits / 8)
    private val totalFrames = dataLen / frameSize

    private class Run {
        val cancelled = AtomicBoolean(false)
        @Volatile var track: AudioTrack? = null
        @Volatile var thread: Thread? = null
    }

    @Volatile private var run: Run? = null
    @Volatile private var baseFrame = 0L
    @Volatile private var released = false

    override fun durationMs(): Int = (totalFrames * 1000L / rate).toInt()

    override fun positionMs(): Int {
        val head = try { run?.track?.playbackHeadPosition ?: 0 } catch (_: Exception) { 0 }
        val frames = (baseFrame + (head.toLong() and 0xFFFFFFFFL)).coerceAtMost(totalFrames)
        return (frames * 1000L / rate).toInt()
    }

    override fun seekTo(ms: Int) {
        if (released) return
        val frame = (ms.toLong() * rate / 1000L).coerceIn(0L, totalFrames)
        stopRun()
        baseFrame = frame
        startRun(frame)
    }

    override fun release() {
        released = true
        stopRun()
        try { channel.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }

    private fun stopRun() {
        val r = run ?: return
        r.cancelled.set(true)
        try { r.thread?.join(800) } catch (_: Exception) {}
        run = null
    }

    private fun play() {
        baseFrame = 0L
        startRun(0L)
    }

    private fun startRun(fromFrame: Long) {
        val r = Run()
        run = r
        val t = Thread({ playLoop(r, fromFrame) }, "WavPlayer")
        r.thread = t
        t.start()
    }

    private fun playLoop(r: Run, fromFrame: Long) {
        var track: AudioTrack? = null
        try {
            val enc = if (bits == 8 && tag == 1) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT
            val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minBuf = AudioTrack.getMinBufferSize(rate, mask, enc)
            if (minBuf <= 0) throw IllegalStateException("bad min buffer $minBuf")
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(enc).setSampleRate(rate).setChannelMask(mask).build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            r.track = t
            t.play()

            val framesPerRead = 2048
            val inBuf = ByteBuffer.allocate(framesPerRead * frameSize)
            var pos = dataStart + fromFrame * frameSize
            val end = dataStart + totalFrames * frameSize
            var written = 0L
            while (!r.cancelled.get() && pos < end) {
                inBuf.clear()
                val want = minOf(inBuf.capacity().toLong(), end - pos).toInt()
                inBuf.limit(want)
                val n = channel.read(inBuf, pos)
                if (n <= 0) break
                val usable = n - (n % frameSize)
                if (usable <= 0) break
                val bytes = inBuf.array()
                val wrote: Int
                if (tag == 6 || tag == 7) {
                    val out = ByteArray(usable * 2)
                    for (i in 0 until usable) {
                        val s = if (tag == 7) muLaw(bytes[i]) else aLaw(bytes[i])
                        out[i * 2] = (s.toInt() and 0xFF).toByte()
                        out[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                    }
                    wrote = t.write(out, 0, out.size)
                } else {
                    wrote = t.write(bytes, 0, usable)
                }
                if (wrote < 0) throw IllegalStateException("AudioTrack.write error $wrote")
                pos += usable
                written += usable / frameSize
            }
            // বাফারে থাকা শেষ অংশ বাজা পর্যন্ত অপেক্ষা
            var waited = 0
            while (!r.cancelled.get() && waited < 100) {
                val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (head >= written) break
                Thread.sleep(50)
                waited++
            }
            if (!r.cancelled.get() && !released) ui.post { onCompletion() }
        } catch (e: Exception) {
            Log.e(TAG, "playback error: ${e.javaClass.simpleName}: ${e.message}")
            if (!r.cancelled.get() && !released) ui.post { onError() }
        } finally {
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            r.track = null
        }
    }

    companion object {
        private const val TAG = "WavPlayer"

        /** ফাইল খুলে হেডার পড়ে প্লে শুরু করে। সমর্থিত না হলে বা ব্যর্থ হলে null। */
        fun open(context: Context, path: String, onCompletion: () -> Unit, onError: () -> Unit): WavPlayer? {
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = if (path.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
                } else {
                    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                }
                if (pfd == null) return null
                val ch = FileInputStream(pfd.fileDescriptor).channel
                val fileLen = ch.size()
                val head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                if (ch.read(head, 0) < 12) {
                    pfd.close()
                    return null
                }
                head.flip()
                val riff = ByteArray(4)
                head.get(riff)
                head.getInt()
                val wave = ByteArray(4)
                head.get(wave)
                if (String(riff) != "RIFF" || String(wave) != "WAVE") {
                    pfd.close()
                    return null
                }

                var pos = 12L
                var tag = -1
                var chans = 0
                var rate = 0
                var bits = 0
                var dataStart = -1L
                var dataLen = 0L
                val chunkHead = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                while (pos + 8 <= fileLen) {
                    chunkHead.clear()
                    if (ch.read(chunkHead, pos) < 8) break
                    chunkHead.flip()
                    val id = ByteArray(4)
                    chunkHead.get(id)
                    val size = chunkHead.getInt().toLong() and 0xFFFFFFFFL
                    val body = pos + 8
                    val name = String(id)
                    if (name == "fmt ") {
                        val f = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                        ch.read(f, body)
                        tag = f.getShort(0).toInt() and 0xFFFF
                        chans = f.getShort(2).toInt() and 0xFFFF
                        rate = f.getInt(4)
                        bits = f.getShort(14).toInt() and 0xFFFF
                        if (tag == 0xFFFE && size >= 26) tag = f.getShort(24).toInt() and 0xFFFF
                    } else if (name == "data") {
                        dataStart = body
                        val remaining = fileLen - body
                        // আকার শূন্য/ভুল (যেমন কল হঠাৎ শেষ হলে) হলে ফাইলের শেষ পর্যন্ত
                        dataLen = if (size == 0L || size > remaining) remaining else size
                        break
                    }
                    pos = body + size + (size and 1L)
                }
                val supported = dataStart >= 0 && rate > 0 && (chans == 1 || chans == 2) &&
                    ((tag == 1 && (bits == 8 || bits == 16)) || ((tag == 6 || tag == 7) && bits == 8))
                if (!supported) {
                    Log.w(TAG, "unsupported wav: tag=$tag ch=$chans rate=$rate bits=$bits data=$dataStart")
                    pfd.close()
                    return null
                }
                val p = WavPlayer(pfd, ch, rate, chans, tag, bits, dataStart, dataLen, onCompletion, onError)
                if (p.totalFrames <= 0L) {
                    p.release()
                    return null
                }
                p.play()
                return p
            } catch (e: Exception) {
                Log.e(TAG, "open failed for $path: ${e.javaClass.simpleName}: ${e.message}")
                try { pfd?.close() } catch (_: Exception) {}
                return null
            }
        }

        private fun muLaw(b: Byte): Short {
            val u = b.toInt().inv() and 0xFF
            val sign = u and 0x80
            val exp = (u shr 4) and 0x07
            val mant = u and 0x0F
            val mag = (((mant shl 3) + 0x84) shl exp) - 0x84
            return (if (sign != 0) -mag else mag).toShort()
        }

        private fun aLaw(b: Byte): Short {
            val a = (b.toInt() and 0xFF) xor 0x55
            val sign = a and 0x80
            val exp = (a shr 4) and 0x07
            val mant = a and 0x0F
            var mag = (mant shl 4) + 8
            if (exp != 0) mag = (mag + 0x100) shl (exp - 1)
            return (if (sign != 0) mag else -mag).toShort()
        }
    }
}
