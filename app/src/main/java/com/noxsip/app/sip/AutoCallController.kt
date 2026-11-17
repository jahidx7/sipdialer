package com.noxsip.app.sip

import android.content.Context
import com.noxsip.app.util.AutoCallPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import org.linphone.core.Call

enum class AutoCallItemStatus { READY, CALLING, PLAYING, NO_ANSWER, FAILED, DONE, STOPPED }

data class AutoCallItem(val number: String, val status: AutoCallItemStatus = AutoCallItemStatus.READY, val detail: String = "")

data class AutoCallProgress(
    val running: Boolean = false,
    val items: List<AutoCallItem> = emptyList(),
    val currentIndex: Int = -1,
    val message: String = ""
) {
    val completedCount: Int get() = items.count {
        it.status in setOf(AutoCallItemStatus.NO_ANSWER, AutoCallItemStatus.FAILED, AutoCallItemStatus.DONE, AutoCallItemStatus.STOPPED)
    }
}

/** One-at-a-time, explicit user-started outgoing SIP queue. */
class AutoCallController private constructor(private val engine: SipEngine) {
    companion object {
        @Volatile private var instance: AutoCallController? = null
        fun getInstance(context: Context, engine: SipEngine): AutoCallController = instance ?: synchronized(this) {
            instance ?: AutoCallController(engine).also { instance = it }
        }
        private const val ANSWER_TIMEOUT_MS = 45_000L
        private const val GAP_BETWEEN_CALLS_MS = 1_500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _progress = MutableStateFlow(AutoCallProgress())
    val progress: StateFlow<AutoCallProgress> = _progress.asStateFlow()
    private var queueJob: Job? = null
    private var priorMute = false
    private var stopRequested = false

    /** Returns null when accepted, otherwise a user-readable reason. */
    fun start(numbers: List<String>, voicePath: String): String? {
        if (_progress.value.running) return "An Auto Call queue is already running."
        val cleanNumbers = AutoCallPrefs.parseNumbers(numbers.joinToString("\n"))
        if (cleanNumbers.isEmpty()) return "Add at least one valid phone number."
        if (cleanNumbers.size > AutoCallPrefs.MAX_RECIPIENTS) return "A queue can contain up to 50 numbers."
        if (!File(voicePath).isFile) return "Choose a voice recording first."
        if (AutoCallPrefs.voiceDurationMs(voicePath) == null) return "The selected audio is not ready to play."
        if (engine.registrationState.value != SipRegistrationState.REGISTERED) return "Register a SIP account before starting."
        if (engine.hasActiveCall()) return "Finish the current call before starting Auto Call."
        if (engine.callState.value !is SipCallState.Idle && engine.callState.value !is SipCallState.Ended) {
            return "Finish the current call before starting Auto Call."
        }

        priorMute = engine.isMuted.value
        stopRequested = false
        _progress.value = AutoCallProgress(
            running = true,
            items = cleanNumbers.map { AutoCallItem(it) },
            message = "Auto Call is running over the active SIP account."
        )
        queueJob = scope.launch { runQueue(voicePath) }
        return null
    }

    fun stop() {
        val job = queueJob ?: return
        stopRequested = true
        engine.stopAutoCallAudio()
        engine.endAutoCall()
        scope.launch {
            job.cancelAndJoin()
            finalizeQueue(stopped = true)
        }
    }

    private suspend fun runQueue(voicePath: String) {
        engine.setAutoCallMicLocked(true)
        try {
            val durationMs = AutoCallPrefs.voiceDurationMs(voicePath) ?: error("Voice recording could not be read")
            for (index in _progress.value.items.indices) {
                if (stopRequested) break
                var idleWait = 0L
                while (engine.hasActiveCall() && idleWait < 5_000L) {
                    delay(250L)
                    idleWait += 250L
                }
                if (engine.hasActiveCall()) {
                    _progress.value = _progress.value.copy(message = "Queue stopped because another SIP call became active.")
                    stopRequested = true
                    break
                }
                setItem(index, AutoCallItemStatus.CALLING, "Calling")
                _progress.value = _progress.value.copy(currentIndex = index, message = "Calling ${_progress.value.items[index].number}")
                if (!engine.makeCall(_progress.value.items[index].number)) {
                    setItem(index, AutoCallItemStatus.FAILED, "Could not start SIP call")
                    delay(GAP_BETWEEN_CALLS_MS)
                    continue
                }

                val outcome = withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
                    combine(engine.callState, engine.callMediaReady) { state, mediaReady -> state to mediaReady }
                        .first { (state, mediaReady) ->
                            (state is SipCallState.Ended && matchesTarget(state.remoteAddress, _progress.value.items[index].number)) ||
                                (state is SipCallState.Connected && state.call.dir == Call.Dir.Outgoing && matchesTarget(state.remoteAddress, _progress.value.items[index].number) && mediaReady)
                        }
                }
                if (stopRequested) break
                when {
                    outcome == null -> {
                        engine.endAutoCall()
                        setItem(index, AutoCallItemStatus.NO_ANSWER, "No answer; skipped after 45 seconds")
                    }
                    outcome.first is SipCallState.Ended -> {
                        val reason = (outcome.first as SipCallState.Ended).reason
                        setItem(index, AutoCallItemStatus.NO_ANSWER, reason.ifBlank { "Not answered; skipped" })
                    }
                    else -> {
                        setItem(index, AutoCallItemStatus.PLAYING, "Playing voice; microphone is off")
                        _progress.value = _progress.value.copy(message = "Playing voice to ${_progress.value.items[index].number}")
                        if (engine.startAutoCallAudio(voicePath)) {
                            delay(durationMs + 500L)
                            engine.stopAutoCallAudio()
                            engine.endAutoCall()
                            setItem(index, AutoCallItemStatus.DONE, "Voice played; call ended")
                        } else {
                            engine.endAutoCall()
                            setItem(index, AutoCallItemStatus.FAILED, "Could not play the selected voice")
                        }
                    }
                }
                if (index < _progress.value.items.lastIndex && !stopRequested) delay(GAP_BETWEEN_CALLS_MS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _progress.value = _progress.value.copy(message = error.message ?: "Auto Call stopped after an error")
        } finally {
            engine.stopAutoCallAudio()
            engine.setAutoCallMicLocked(false, priorMute)
            finalizeQueue(stopRequested)
            queueJob = null
        }
    }

    private fun setItem(index: Int, status: AutoCallItemStatus, detail: String) {
        val current = _progress.value
        if (index !in current.items.indices) return
        _progress.value = current.copy(items = current.items.toMutableList().also {
            it[index] = it[index].copy(status = status, detail = detail)
        })
    }

    private fun matchesTarget(remoteAddress: String, target: String): Boolean {
        val remoteUser = remoteAddress.substringAfter("sip:", remoteAddress).substringBefore('@')
        val remoteDigits = remoteUser.filter(Char::isDigit)
        val targetDigits = target.filter(Char::isDigit)
        val suffixLength = minOf(10, targetDigits.length)
        return suffixLength >= 7 && remoteDigits.takeLast(suffixLength) == targetDigits.takeLast(suffixLength)
    }

    private fun finalizeQueue(stopped: Boolean) {
        val current = _progress.value
        val items = if (stopped) current.items.map {
            if (it.status == AutoCallItemStatus.READY || it.status == AutoCallItemStatus.CALLING || it.status == AutoCallItemStatus.PLAYING)
                it.copy(status = AutoCallItemStatus.STOPPED, detail = "Stopped by user") else it
        } else current.items
        _progress.value = current.copy(
            running = false,
            items = items,
            currentIndex = -1,
            message = if (!stopped) "Queue complete." else current.message.takeIf { it.startsWith("Queue stopped") } ?: "Auto Call stopped."
        )
    }
}
