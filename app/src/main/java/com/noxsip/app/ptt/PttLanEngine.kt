package com.noxsip.app.ptt

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Same-Wi-Fi push-to-talk. Audio is streamed live and never written to storage. */
class PttLanEngine(context: Context) {
    data class Peer(val id: String, val name: String, val lastSeenMs: Long)

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localId = UUID.randomUUID().toString()
    private val localName = (Build.MODEL ?: "Android phone").take(32)
    private val peers = ConcurrentHashMap<String, Peer>()
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peersState: StateFlow<List<Peer>> = _peers.asStateFlow()
    private val _status = MutableStateFlow("Waiting to start")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _speaker = MutableStateFlow<String?>(null)
    val speaker: StateFlow<String?> = _speaker.asStateFlow()
    private val _transmitting = MutableStateFlow(false)
    val transmitting: StateFlow<Boolean> = _transmitting.asStateFlow()

    private val transmittingFlag = AtomicBoolean(false)
    private val startingFlag = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    private var group: InetAddress? = null
    private var wifiInterface: NetworkInterface? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var pttWakeLock: PowerManager.WakeLock? = null
    @Volatile private var backgroundListening = false
    @Volatile private var screenUsers = 0
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var captureJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private val audioWriteLock = Any()
    private var previousAudioMode: Int? = null
    @Volatile private var remoteSpeakerId: String? = null

    fun start() {
        if (socket != null || !startingFlag.compareAndSet(false, true)) return
        scope.launch {
            try {
                val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork
                val caps = network?.let(cm::getNetworkCapabilities)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                    _status.value = "Connect to Wi-Fi to use local PTT"
                    return@launch
                }
                val ifaceName = network?.let(cm::getLinkProperties)?.interfaceName
                val ni = NetworkInterface.getNetworkInterfaces().toList()
                    .firstOrNull { it.name == ifaceName && it.isUp && it.supportsMulticast() }
                    ?: throw IllegalStateException("Could not find the active Wi-Fi interface")
                wifiInterface = ni
                val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifi.createMulticastLock("NoxSIP:PTT").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                pttWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoxSIP:PTTReceive").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                val s = MulticastSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 1200
                    bind(InetSocketAddress(PORT))
                    networkInterface = ni
                    timeToLive = 1
                    joinGroup(InetSocketAddress(GROUP_ADDRESS, PORT), ni)
                }
                socket = s
                group = GROUP_ADDRESS
                _status.value = "Searching this Wi-Fi network"
                sendControl(TYPE_HELLO, localName)
                receiveJob = scope.launch { receiveLoop(s) }
                heartbeatJob = scope.launch {
                    while (isActive) {
                        sendControl(TYPE_HELLO, localName)
                        expirePeers()
                        delay(HEARTBEAT_MS)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "PTT start failed", e)
                _status.value = e.message?.let { "PTT unavailable: $it" } ?: "PTT unavailable on this Wi-Fi"
                closeNetwork()
            } finally {
                startingFlag.set(false)
            }
        }
    }

    fun acquireScreenUse() {
        screenUsers++
        start()
    }

    fun releaseScreenUse() {
        screenUsers = (screenUsers - 1).coerceAtLeast(0)
        if (screenUsers == 0 && !backgroundListening) stopNetwork()
    }

    /** Keeps the listener alive while the SIP foreground service is running, including off-screen. */
    fun setBackgroundListening(enabled: Boolean) {
        backgroundListening = enabled
        if (enabled) start() else if (screenUsers == 0) stopNetwork()
    }

    /** Refresh socket binding after Android changes the default network. */
    fun onNetworkChanged() {
        if (backgroundListening || screenUsers > 0) {
            stopNetwork()
            scope.launch {
                delay(300)
                if (socket == null) start()
            }
        }
    }

    fun beginTransmit() {
        if (socket == null || !transmittingFlag.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (_speaker.value != null && _speaker.value != localName) {
                    transmittingFlag.set(false)
                    return@launch
                }
                setAudioMode(true)
                val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
                if (min <= 0) throw IllegalStateException("Microphone is unavailable")
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    ENCODING,
                    maxOf(min * 2, FRAME_BYTES * 4)
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release()
                    throw IllegalStateException("Microphone could not start")
                }
                audioRecord = rec
                sendControl(TYPE_START, localName)
                _speaker.value = localName
                _transmitting.value = true
                rec.startRecording()
                captureJob = scope.launch {
                    val frame = ByteArray(FRAME_BYTES)
                    var seq = 0
                    while (isActive && transmittingFlag.get()) {
                        val n = rec.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                        if (n > 0) sendAudio(seq++, frame, n)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "PTT microphone permission missing", e)
                _status.value = "Allow microphone access to talk"
                endTransmit()
            } catch (e: Exception) {
                Log.w(TAG, "PTT capture failed", e)
                _status.value = e.message?.let { "Microphone error: $it" } ?: "Microphone error"
                endTransmit()
            }
        }
    }

    fun endTransmit() {
        if (!transmittingFlag.getAndSet(false)) return
        scope.launch {
            captureJob?.cancel()
            captureJob = null
            try { audioRecord?.stop() } catch (_: Exception) { }
            audioRecord?.release()
            audioRecord = null
            sendControl(TYPE_STOP, localName)
            _transmitting.value = false
            if (_speaker.value == localName) _speaker.value = null
            setAudioMode(false)
        }
    }

    fun stop() {
        backgroundListening = false
        screenUsers = 0
        stopNetwork()
    }

    private fun stopNetwork() {
        val wasTalking = transmittingFlag.getAndSet(false)
        if (wasTalking) sendControl(TYPE_STOP, localName)
        captureJob?.cancel(); captureJob = null
        try { audioRecord?.stop() } catch (_: Exception) { }
        audioRecord?.release(); audioRecord = null
        heartbeatJob?.cancel(); heartbeatJob = null
        receiveJob?.cancel(); receiveJob = null
        closeNetwork()
    }

    private suspend fun receiveLoop(s: MulticastSocket) {
        val data = ByteArray(MAX_PACKET)
        while (scope.isActive && !s.isClosed) {
            try {
                val packet = DatagramPacket(data, data.size)
                s.receive(packet)
                val input = DataInputStream(ByteArrayInputStream(packet.data, packet.offset, packet.length))
                if (input.readInt() != MAGIC) continue
                val type = input.readUnsignedByte()
                val sender = ByteArray(ID_BYTES).also(input::readFully).toString(Charsets.US_ASCII)
                val seq = input.readInt()
                val nameLen = input.readUnsignedByte().coerceAtMost(MAX_NAME_BYTES)
                val name = ByteArray(nameLen).also(input::readFully).toString(Charsets.UTF_8)
                    .ifBlank { "NoxSIP device" }
                if (sender == localId) continue
                when (type) {
                    TYPE_HELLO -> {
                        peers[sender] = Peer(sender, name, System.currentTimeMillis())
                        publishPeers()
                        if (_status.value.startsWith("Searching")) _status.value = "Local network ready"
                    }
                    TYPE_START -> {
                        if (!transmittingFlag.get()) {
                            remoteSpeakerId = sender
                            _speaker.value = name
                            ensureAudioTrack()
                        }
                    }
                    TYPE_STOP -> if (remoteSpeakerId == sender) {
                        remoteSpeakerId = null
                        _speaker.value = null
                        if (!transmittingFlag.get()) setAudioMode(false)
                    }
                    TYPE_AUDIO -> {
                        val audio = ByteArray(input.available()).also(input::readFully)
                        if (!transmittingFlag.get() && (remoteSpeakerId == null || remoteSpeakerId == sender)) {
                            remoteSpeakerId = sender
                            _speaker.value = name
                            ensureAudioTrack()
                            synchronized(audioWriteLock) { audioTrack?.write(audio, 0, audio.size, AudioTrack.WRITE_NON_BLOCKING) }
                        }
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
                expirePeers()
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                if (!s.isClosed) Log.w(TAG, "PTT receive error", e)
            }
        }
    }

    private fun ensureAudioTrack() {
        if (audioTrack != null) return
        try {
            val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            val attrs = AudioAttributes.Builder()
                // Media usage continues to play in Android silent mode; it respects media volume.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(ENCODING)
                .setChannelMask(CHANNEL_OUT)
                .build()
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min, FRAME_BYTES * 8))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().apply { play() }
        } catch (e: Exception) { Log.w(TAG, "Could not initialize PTT playback", e) }
    }

    private fun sendAudio(seq: Int, bytes: ByteArray, count: Int) {
        try {
            val out = ByteArrayOutputStream(HEADER_BYTES + count)
            DataOutputStream(out).use { d ->
                d.writeInt(MAGIC); d.writeByte(TYPE_AUDIO); d.write(localId.toByteArray(Charsets.US_ASCII))
                d.writeInt(seq); d.writeByte(0); d.write(bytes, 0, count)
            }
            sendBytes(out.toByteArray())
        } catch (e: Exception) { Log.w(TAG, "PTT audio send failed", e) }
    }

    private fun sendControl(type: Int, name: String) {
        try {
            val bytes = name.toByteArray(Charsets.UTF_8).take(MAX_NAME_BYTES).toByteArray()
            val out = ByteArrayOutputStream(HEADER_BYTES + bytes.size)
            DataOutputStream(out).use { d ->
                d.writeInt(MAGIC); d.writeByte(type); d.write(localId.toByteArray(Charsets.US_ASCII))
                d.writeInt(0); d.writeByte(bytes.size); d.write(bytes)
            }
            sendBytes(out.toByteArray())
        } catch (e: Exception) { Log.w(TAG, "PTT control send failed", e) }
    }

    private fun sendBytes(bytes: ByteArray) {
        val s = socket ?: return
        val address = group ?: return
        try { s.send(DatagramPacket(bytes, bytes.size, address, PORT)) }
        catch (e: Exception) { if (!s.isClosed) Log.w(TAG, "PTT network send failed", e) }
    }

    private fun expirePeers() {
        val cutoff = System.currentTimeMillis() - PEER_TTL_MS
        peers.entries.removeAll { it.value.lastSeenMs < cutoff }
        publishPeers()
    }

    private fun publishPeers() { _peers.value = peers.values.sortedBy { it.name.lowercase() } }

    private fun setAudioMode(active: Boolean) {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (active) {
            if (previousAudioMode == null) previousAudioMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
            audioTrack = null
            previousAudioMode?.let { am.mode = it }
            previousAudioMode = null
        }
    }

    private fun closeNetwork() {
        try {
            socket?.let { s ->
                try { wifiInterface?.let { s.leaveGroup(InetSocketAddress(GROUP_ADDRESS, PORT), it) } } catch (_: Exception) { }
                s.close()
            }
        } catch (_: Exception) { }
        socket = null; group = null; wifiInterface = null
        try { multicastLock?.release() } catch (_: Exception) { }
        multicastLock = null
        try { pttWakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        pttWakeLock = null
        setAudioMode(false)
        remoteSpeakerId = null
        _transmitting.value = false
        _speaker.value = null
        _status.value = "Stopped"
    }

    companion object {
        @Volatile private var instance: PttLanEngine? = null
        fun getInstance(context: Context): PttLanEngine = instance ?: synchronized(this) {
            instance ?: PttLanEngine(context.applicationContext).also { instance = it }
        }

        private const val TAG = "NoxSIP-PTT"
        private const val PORT = 45777
        private const val GROUP = "239.255.77.77"
        private val GROUP_ADDRESS: InetAddress = InetAddress.getByName(GROUP)
        private const val MAGIC = 0x4E505454 // NPTT
        private const val ID_BYTES = 36
        private const val MAX_NAME_BYTES = 48
        private const val HEADER_BYTES = 50
        private const val MAX_PACKET = 1500
        private const val TYPE_HELLO = 1
        private const val TYPE_START = 2
        private const val TYPE_STOP = 3
        private const val TYPE_AUDIO = 4
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_BYTES = 640 // 20 ms, mono PCM16
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val HEARTBEAT_MS = 1_000L
        private const val PEER_TTL_MS = 4_000L
    }
}
