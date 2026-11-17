package com.noxsip.app.sip

import android.content.Context
import android.os.SystemClock
import com.noxsip.app.data.AutoCallReportDatabase
import com.noxsip.app.data.models.AutoCallReportRecord
import com.noxsip.app.util.AutoCallPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import org.linphone.core.Call

enum class AutoCallItemStatus {
    READY, CALLING, PLAYING, ANSWERED, NO_ANSWER, REJECTED, BUSY, FAILED, STOPPED
}

data class AutoCallItem(
    val id: Long,
    val batchId: String,
    val number: String,
    val status: AutoCallItemStatus,
    val detail: String,
    val answered: Boolean,
    val callDurationSeconds: Int,
    val voicePlayedMillis: Long,
    val sipCode: Int,
    val attempt: Int,
    val createdAtMillis: Long,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val endedBy: String,
    val terminationReason: String
)

data class AutoCallProgress(
    val running: Boolean = false,
    val items: List<AutoCallItem> = emptyList(),
    val currentId: Long? = null,
    val message: String = ""
) {
    val currentIndex: Int get() = items.indexOfFirst { it.id == currentId }
    val readyCount: Int get() = items.count { it.status == AutoCallItemStatus.READY }
    val answeredCount: Int get() = items.count { it.answered }
    val unansweredCount: Int get() = items.count {
        it.status in setOf(
            AutoCallItemStatus.ANSWERED,
            AutoCallItemStatus.NO_ANSWER,
            AutoCallItemStatus.REJECTED,
            AutoCallItemStatus.BUSY,
            AutoCallItemStatus.FAILED,
            AutoCallItemStatus.STOPPED
        ) && !it.answered
    }
    val completedCount: Int get() = items.count {
        it.status in setOf(
            AutoCallItemStatus.ANSWERED,
            AutoCallItemStatus.NO_ANSWER,
            AutoCallItemStatus.REJECTED,
            AutoCallItemStatus.BUSY,
            AutoCallItemStatus.FAILED,
            AutoCallItemStatus.STOPPED
        )
    }
}

/** One-at-a-time, explicitly started SIP queue with a durable per-call report. */
class AutoCallController private constructor(context: Context, private val engine: SipEngine) {
    companion object {
        private const val ANSWER_TIMEOUT_MS = 45_000L
        private const val END_WAIT_MS = 8_000L
        private const val GAP_BETWEEN_CALLS_MS = 1_500L

        @Volatile private var instance: AutoCallController? = null
        fun getInstance(context: Context, engine: SipEngine): AutoCallController = instance ?: synchronized(this) {
            instance ?: AutoCallController(context.applicationContext, engine).also { instance = it }
        }
    }

    private val database = AutoCallReportDatabase.getInstance(context)
    private val dao = database.reports()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initialized = CompletableDeferred<Unit>()
    private val _control = MutableStateFlow(ControlState())
    val progress: StateFlow<AutoCallProgress> = combine(_control, dao.observeAll()) { control, records ->
        val items = records.map(AutoCallReportRecord::toItem)
        AutoCallProgress(
            running = control.running,
            items = items,
            currentId = control.currentId,
            message = control.message
        )
    }.stateIn(scope, SharingStarted.Eagerly, AutoCallProgress(message = "Loading call report…"))

    private var queueJob: Job? = null
    private var priorMute = false
    @Volatile private var stopRequested = false

    init {
        scope.launch {
            withContext(Dispatchers.IO) { dao.markInterrupted() }
            initialized.complete(Unit)
        }
    }

    /** Starts a freshly scanned file list or a manually entered list. */
    fun start(numbers: List<String>, voicePath: String, importedFromFile: Boolean = false): String? {
        val clean = numbers.distinct()
        if (clean.isEmpty()) return "Add at least one valid Bangladesh mobile number."
        if (!importedFromFile && clean.size > AutoCallPrefs.MAX_MANUAL_RECIPIENTS) return "Manual entry is limited to ${AutoCallPrefs.MAX_MANUAL_RECIPIENTS} numbers. Import a file for a larger queue."
        val invalidPath = validateStart(voicePath)
        if (invalidPath != null) return invalidPath
        priorMute = engine.isMuted.value
        stopRequested = false
        _control.value = ControlState(running = true, message = "Preparing ${clean.size} calls…")
        queueJob = scope.launch {
            initialized.await()
            try {
                val batch = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val records = clean.map { number ->
                    AutoCallReportRecord(batchId = batch, number = number, createdAtMillis = now)
                }
                val insertedIds = withContext(Dispatchers.IO) { dao.insertAll(records) }
                runQueue(insertedIds, voicePath)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _control.value = ControlState(message = error.message ?: "Could not prepare the call queue")
            }
        }
        return null
    }

    /** Starts only retry entries already added to the saved queue. */
    fun startQueued(voicePath: String): String? {
        val invalidPath = validateStart(voicePath)
        if (invalidPath != null) return invalidPath
        if (progress.value.readyCount == 0) return "There are no queued calls to start."
        priorMute = engine.isMuted.value
        stopRequested = false
        _control.value = ControlState(running = true, message = "Preparing queued retries…")
        queueJob = scope.launch {
            initialized.await()
            val ids = withContext(Dispatchers.IO) { dao.getQueued().map { it.id } }
            if (ids.isEmpty()) {
                _control.value = ControlState(message = "There are no queued calls to start.")
            } else {
                runQueue(ids, voicePath)
            }
        }
        return null
    }

    fun queueRetry(itemId: Long): String? {
        if (_control.value.running) return "Stop the current queue before adding retries."
        val item = progress.value.items.firstOrNull { it.id == itemId } ?: return "Call report entry was not found."
        if (!item.status.isRetryable()) return "Only unanswered, rejected, busy, or failed calls can be retried."
        scope.launch {
            initialized.await()
            withContext(Dispatchers.IO) {
                dao.insert(
                    AutoCallReportRecord(
                        batchId = item.batchId,
                        number = item.number,
                        status = AutoCallItemStatus.READY.name,
                        detail = "Retry queued",
                        attempt = item.attempt + 1,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
            }
            _control.value = _control.value.copy(message = "Retry added to queue. Confirm consent and start queued retries.")
        }
        return null
    }

    fun clearReport(): String? {
        if (_control.value.running) return "Stop Auto Call before clearing the report."
        scope.launch {
            initialized.await()
            withContext(Dispatchers.IO) { dao.clearAll() }
            _control.value = ControlState(message = "Call report cleared.")
        }
        return null
    }

    fun stop() {
        if (!_control.value.running) return
        stopRequested = true
        engine.stopAutoCallAudio()
        engine.endAutoCall()
        _control.value = _control.value.copy(message = "Stopping current call and queue…")
    }

    private fun validateStart(voicePath: String): String? {
        if (_control.value.running) return "An Auto Call queue is already running."
        if (!File(voicePath).isFile) return "Choose a voice recording first."
        if (AutoCallPrefs.voiceDurationMs(voicePath) == null) return "The selected audio is not ready to play."
        if (engine.registrationState.value != SipRegistrationState.REGISTERED) return "Register a SIP account before starting."
        if (engine.hasActiveCall()) return "Finish the current call before starting Auto Call."
        if (engine.callState.value !is SipCallState.Idle && engine.callState.value !is SipCallState.Ended) {
            return "Finish the current call before starting Auto Call."
        }
        return null
    }

    private suspend fun runQueue(ids: List<Long>, voicePath: String) {
        engine.setAutoCallMicLocked(true)
        try {
            val voiceDurationMs = AutoCallPrefs.voiceDurationMs(voicePath) ?: error("Voice recording could not be read")
            for (id in ids) {
                if (stopRequested) break
                var idleWait = 0L
                while (engine.hasActiveCall() && idleWait < 5_000L && !stopRequested) {
                    delay(250L)
                    idleWait += 250L
                }
                if (stopRequested) break
                if (engine.hasActiveCall()) {
                    updateRecord(id) { it.copy(status = AutoCallItemStatus.FAILED.name, detail = "Another SIP call became active", endedBy = "SIP account busy", finishedAtMillis = System.currentTimeMillis()) }
                    continue
                }

                val record = withContext(Dispatchers.IO) { dao.getById(id) } ?: continue
                updateRecord(id) {
                    it.copy(
                        status = AutoCallItemStatus.CALLING.name,
                        detail = "Calling",
                        startedAtMillis = System.currentTimeMillis(),
                        finishedAtMillis = 0,
                        answered = false,
                        callDurationSeconds = 0,
                        voicePlayedMillis = 0,
                        sipCode = 0,
                        endedBy = ""
                    )
                }
                _control.value = _control.value.copy(currentId = id, message = "Calling ${record.number}")

                if (!engine.makeCall(record.number)) {
                    updateRecord(id) {
                        it.copy(status = AutoCallItemStatus.FAILED.name, detail = "Could not start the SIP call", endedBy = "App", finishedAtMillis = System.currentTimeMillis())
                    }
                    delay(GAP_BETWEEN_CALLS_MS)
                    continue
                }

                val callOutcome = withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
                    combine(engine.callState, engine.callMediaReady) { state, mediaReady -> state to mediaReady }
                        .first { (state, mediaReady) ->
                            (state is SipCallState.Ended && matchesTarget(state.remoteAddress, record.number)) ||
                                (state is SipCallState.Connected && state.call.dir == Call.Dir.Outgoing && matchesTarget(state.remoteAddress, record.number) && mediaReady)
                        }
                }

                if (stopRequested) {
                    val end = callOutcome?.first as? SipCallState.Ended
                    updateRecord(id) {
                        it.copy(
                            status = AutoCallItemStatus.STOPPED.name,
                            detail = "Stopped by user",
                            answered = end?.answered ?: false,
                            callDurationSeconds = end?.durationSeconds ?: 0,
                            sipCode = end?.protocolCode ?: 0,
                            endedBy = "User",
                            finishedAtMillis = System.currentTimeMillis()
                        )
                    }
                    break
                }

                when {
                    callOutcome == null -> {
                        engine.endAutoCall()
                        val ended = waitForEnd(record.number)
                        val classified = classifyUnanswered(ended?.protocolCode ?: 0, ended?.reason ?: "")
                        updateRecord(id) {
                            it.copy(
                                status = classified.first.name,
                                detail = classified.second,
                                callDurationSeconds = ended?.durationSeconds ?: 0,
                                answered = ended?.answered ?: false,
                                sipCode = ended?.protocolCode ?: 0,
                                endedBy = "Auto Call timeout",
                            terminationReason = ended?.terminationReason.orEmpty(),
                                finishedAtMillis = System.currentTimeMillis()
                            )
                        }
                    }
                    callOutcome.first is SipCallState.Ended -> {
                        val ended = callOutcome.first as SipCallState.Ended
                        val classified = classifyUnanswered(ended.protocolCode, ended.reason)
                        updateRecord(id) {
                            it.copy(
                                status = classified.first.name,
                                detail = classified.second,
                                answered = ended.answered,
                                callDurationSeconds = ended.durationSeconds,
                                sipCode = ended.protocolCode,
                                endedBy = classifyEndedBy(ended),
                                terminationReason = ended.terminationReason,
                                finishedAtMillis = System.currentTimeMillis()
                            )
                        }
                    }
                    else -> {
                        updateRecord(id) {
                            it.copy(status = AutoCallItemStatus.PLAYING.name, detail = "Answered; playing recorded voice", answered = true)
                        }
                        _control.value = _control.value.copy(message = "Answered ${record.number}; playing voice")
                        if (!engine.startAutoCallAudio(voicePath)) {
                            engine.endAutoCall()
                            val ended = waitForEnd(record.number)
                            updateRecord(id) {
                                it.copy(
                                    status = AutoCallItemStatus.FAILED.name,
                                    detail = "Answered, but voice playback failed",
                                    answered = true,
                                    callDurationSeconds = ended?.durationSeconds ?: 0,
                                    sipCode = ended?.protocolCode ?: 0,
                                    endedBy = "App playback error",
                                    terminationReason = ended?.terminationReason.orEmpty(),
                                    finishedAtMillis = System.currentTimeMillis()
                                )
                            }
                        } else {
                            val playbackStart = SystemClock.elapsedRealtime()
                            var remoteEndDuringPlayback: SipCallState.Ended? = null
                            while (!stopRequested && SystemClock.elapsedRealtime() - playbackStart < voiceDurationMs) {
                                val left = voiceDurationMs - (SystemClock.elapsedRealtime() - playbackStart)
                                val state = engine.callState.value
                                if (state is SipCallState.Ended && matchesTarget(state.remoteAddress, record.number)) {
                                    remoteEndDuringPlayback = state
                                    break
                                }
                                delay(minOf(left, 150L).coerceAtLeast(1L))
                            }
                            val playedMillis = (SystemClock.elapsedRealtime() - playbackStart).coerceAtLeast(0L)
                            engine.stopAutoCallAudio()
                            if (remoteEndDuringPlayback == null) engine.endAutoCall()
                            val ended = remoteEndDuringPlayback ?: waitForEnd(record.number)
                            val stopped = stopRequested
                            updateRecord(id) {
                                it.copy(
                                    status = if (stopped) AutoCallItemStatus.STOPPED.name else AutoCallItemStatus.ANSWERED.name,
                                    detail = when {
                                        stopped -> "Stopped during voice playback"
                                        remoteEndDuringPlayback != null -> "Answered; remote side ended before voice finished"
                                        else -> "Voice completed; call ended"
                                    },
                                    answered = true,
                                    callDurationSeconds = ended?.durationSeconds ?: ((playedMillis + 500L) / 1000L).toInt(),
                                    voicePlayedMillis = playedMillis,
                                    sipCode = ended?.protocolCode ?: 0,
                                    endedBy = when {
                                        stopped -> "User"
                                        remoteEndDuringPlayback != null -> classifyEndedBy(remoteEndDuringPlayback)
                                        else -> "Auto Call (voice completed)"
                                    },
                                    terminationReason = ended?.terminationReason.orEmpty(),
                                    finishedAtMillis = System.currentTimeMillis()
                                )
                            }
                            if (stopped) break
                        }
                    }
                }
                _control.value = _control.value.copy(currentId = null)
                if (!stopRequested) delay(GAP_BETWEEN_CALLS_MS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val id = _control.value.currentId
            if (id != null) {
                updateRecord(id) { it.copy(status = AutoCallItemStatus.FAILED.name, detail = error.message ?: "Call failed", endedBy = "App", finishedAtMillis = System.currentTimeMillis()) }
            }
            _control.value = _control.value.copy(message = error.message ?: "Auto Call stopped after an error")
        } finally {
            engine.stopAutoCallAudio()
            engine.setAutoCallMicLocked(false, priorMute)
            val wasStopped = stopRequested
            _control.value = ControlState(
                running = false,
                message = if (wasStopped) "Auto Call stopped. Report saved." else "Queue complete. Report saved."
            )
            queueJob = null
        }
    }

    private suspend fun waitForEnd(number: String): SipCallState.Ended? = withTimeoutOrNull(END_WAIT_MS) {
        engine.callState.first { state -> state is SipCallState.Ended && matchesTarget(state.remoteAddress, number) } as? SipCallState.Ended
    }

    private suspend fun updateRecord(id: Long, transform: (AutoCallReportRecord) -> AutoCallReportRecord) {
        withContext(Dispatchers.IO) {
            val old = dao.getById(id) ?: return@withContext
            dao.update(transform(old))
        }
    }

    private fun matchesTarget(remoteAddress: String, target: String): Boolean {
        val remoteUser = remoteAddress.substringAfter("sip:", remoteAddress).substringBefore('@')
        val remoteDigits = remoteUser.filter(Char::isDigit)
        val targetDigits = target.filter(Char::isDigit)
        val suffixLength = minOf(10, targetDigits.length)
        return suffixLength >= 7 && remoteDigits.takeLast(suffixLength) == targetDigits.takeLast(suffixLength)
    }

    private fun classifyUnanswered(code: Int, reason: String): Pair<AutoCallItemStatus, String> = when {
        code == 486 || code == 600 -> AutoCallItemStatus.BUSY to "Busy; not answered"
        code == 603 || code == 403 -> AutoCallItemStatus.REJECTED to "Call rejected"
        code == 487 -> AutoCallItemStatus.NO_ANSWER to "Call cancelled before answer"
        code == 408 || code == 480 -> AutoCallItemStatus.NO_ANSWER to "No answer"
        reason.contains("busy", ignoreCase = true) -> AutoCallItemStatus.BUSY to "Busy; not answered"
        reason.contains("declin", ignoreCase = true) || reason.contains("reject", ignoreCase = true) -> AutoCallItemStatus.REJECTED to "Call rejected"
        reason.startsWith("Call failed", ignoreCase = true) -> AutoCallItemStatus.FAILED to reason
        else -> AutoCallItemStatus.NO_ANSWER to "Not answered"
    }

    private fun classifyEndedBy(ended: SipCallState.Ended): String = when {
        ended.protocolCode == 603 || ended.terminationReason.contains("Declined", ignoreCase = true) -> "Recipient side (call declined)"
        ended.protocolCode == 486 || ended.protocolCode == 600 || ended.terminationReason.contains("Busy", ignoreCase = true) -> "Recipient side / network (busy)"
        ended.answered -> "SIP peer or network after answer"
        ended.protocolCode == 487 -> "Call cancelled; SIP does not identify who cancelled"
        else -> "SIP server / remote side"
    }

    private data class ControlState(
        val running: Boolean = false,
        val currentId: Long? = null,
        val message: String = ""
    )
}

private fun AutoCallReportRecord.toItem() = AutoCallItem(
    id = id,
    batchId = batchId,
    number = number,
    status = runCatching { AutoCallItemStatus.valueOf(status) }.getOrDefault(AutoCallItemStatus.FAILED),
    detail = detail,
    answered = answered,
    callDurationSeconds = callDurationSeconds,
    voicePlayedMillis = voicePlayedMillis,
    sipCode = sipCode,
    attempt = attempt,
    createdAtMillis = createdAtMillis,
    startedAtMillis = startedAtMillis,
    finishedAtMillis = finishedAtMillis,
    endedBy = endedBy,
    terminationReason = terminationReason
)

private fun AutoCallItemStatus.isRetryable(): Boolean = this in setOf(
    AutoCallItemStatus.NO_ANSWER,
    AutoCallItemStatus.REJECTED,
    AutoCallItemStatus.BUSY,
    AutoCallItemStatus.FAILED,
    AutoCallItemStatus.STOPPED
)
