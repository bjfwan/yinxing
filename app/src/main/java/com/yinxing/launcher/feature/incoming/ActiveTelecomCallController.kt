package com.yinxing.launcher.feature.incoming

import java.util.concurrent.CopyOnWriteArraySet

internal enum class ManagedTelecomCallState {
    None,
    Ringing,
    Connecting,
    Active,
    Held,
    Disconnected
}

internal interface ManagedTelecomCall {
    val id: String

    fun answerAudioOnly()

    fun reject()

    fun disconnect()
}

internal data class ActiveTelecomCallSnapshot(
    val callId: String? = null,
    val callerName: String? = null,
    val incomingNumber: String? = null,
    val state: ManagedTelecomCallState = ManagedTelecomCallState.None,
    val answerRequested: Boolean = false,
    val answerConfirmed: Boolean = false,
    val endRequested: Boolean = false
) {
    val hasCall: Boolean
        get() = callId != null && state !in setOf(
            ManagedTelecomCallState.None,
            ManagedTelecomCallState.Disconnected
        )
}

internal data class ManagedTelecomCallCommandResult(
    val dispatched: Boolean,
    val error: Throwable? = null
)

internal class ActiveTelecomCallController {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<(ActiveTelecomCallSnapshot) -> Unit>()
    private var activeCall: ManagedTelecomCall? = null
    private var current = ActiveTelecomCallSnapshot()

    fun attach(
        call: ManagedTelecomCall,
        state: ManagedTelecomCallState,
        callerName: String? = null,
        incomingNumber: String? = null
    ) {
        val snapshot = synchronized(lock) {
            activeCall = call
            current = ActiveTelecomCallSnapshot(
                callId = call.id,
                callerName = callerName,
                incomingNumber = incomingNumber,
                state = state
            )
            current
        }
        notifyListeners(snapshot)
    }

    fun updateCaller(callId: String, callerName: String?, incomingNumber: String?) {
        val snapshot = synchronized(lock) {
            if (current.callId != callId) return
            current = current.copy(callerName = callerName, incomingNumber = incomingNumber)
            current
        }
        notifyListeners(snapshot)
    }

    fun updateState(callId: String, state: ManagedTelecomCallState): Boolean {
        val snapshot = synchronized(lock) {
            if (current.callId != callId) return false
            current = current.copy(
                state = state,
                answerConfirmed = current.answerConfirmed ||
                    (current.answerRequested && state == ManagedTelecomCallState.Active),
                endRequested = current.endRequested && state != ManagedTelecomCallState.Disconnected
            )
            current
        }
        notifyListeners(snapshot)
        return true
    }

    fun expireAnswerRequest(callId: String): Boolean {
        val snapshot = synchronized(lock) {
            if (current.callId != callId ||
                !current.answerRequested ||
                current.answerConfirmed ||
                current.state != ManagedTelecomCallState.Ringing
            ) {
                return false
            }
            current = current.copy(answerRequested = false)
            current
        }
        notifyListeners(snapshot)
        return true
    }

    fun answer(): ManagedTelecomCallCommandResult {
        val call = synchronized(lock) {
            if (current.state != ManagedTelecomCallState.Ringing || current.answerRequested) {
                return ManagedTelecomCallCommandResult(dispatched = false)
            }
            current = current.copy(answerRequested = true)
            activeCall
        } ?: return ManagedTelecomCallCommandResult(dispatched = false)

        val result = runCatching { call.answerAudioOnly() }
        if (result.isFailure) {
            synchronized(lock) {
                if (current.callId == call.id) current = current.copy(answerRequested = false)
            }
        }
        notifyListeners(snapshot())
        return ManagedTelecomCallCommandResult(
            dispatched = result.isSuccess,
            error = result.exceptionOrNull()
        )
    }

    fun end(): ManagedTelecomCallCommandResult {
        val pair = synchronized(lock) {
            val call = activeCall
            if (call == null || current.endRequested || !current.hasCall) {
                return ManagedTelecomCallCommandResult(dispatched = false)
            }
            current = current.copy(endRequested = true)
            call to current.state
        }

        val result = runCatching {
            if (pair.second == ManagedTelecomCallState.Ringing) pair.first.reject()
            else pair.first.disconnect()
        }
        if (result.isFailure) {
            synchronized(lock) {
                if (current.callId == pair.first.id) current = current.copy(endRequested = false)
            }
        }
        notifyListeners(snapshot())
        return ManagedTelecomCallCommandResult(
            dispatched = result.isSuccess,
            error = result.exceptionOrNull()
        )
    }

    fun detach(callId: String) {
        val snapshot = synchronized(lock) {
            if (current.callId != callId) return
            activeCall = null
            current = ActiveTelecomCallSnapshot(state = ManagedTelecomCallState.Disconnected)
            current
        }
        notifyListeners(snapshot)
    }

    fun snapshot(): ActiveTelecomCallSnapshot = synchronized(lock) { current }

    fun addListener(listener: (ActiveTelecomCallSnapshot) -> Unit) {
        listeners += listener
        listener(snapshot())
    }

    fun removeListener(listener: (ActiveTelecomCallSnapshot) -> Unit) {
        listeners -= listener
    }

    private fun notifyListeners(snapshot: ActiveTelecomCallSnapshot) {
        listeners.forEach { listener -> runCatching { listener(snapshot) } }
    }
}

internal object ActiveTelecomCallSession {
    private val controller = ActiveTelecomCallController()

    fun attach(
        call: ManagedTelecomCall,
        state: ManagedTelecomCallState,
        callerName: String? = null,
        incomingNumber: String? = null
    ) = controller.attach(call, state, callerName, incomingNumber)

    fun updateCaller(callId: String, callerName: String?, incomingNumber: String?) =
        controller.updateCaller(callId, callerName, incomingNumber)

    fun updateState(callId: String, state: ManagedTelecomCallState): Boolean =
        controller.updateState(callId, state)

    fun expireAnswerRequest(callId: String): Boolean = controller.expireAnswerRequest(callId)

    fun answer(): ManagedTelecomCallCommandResult = controller.answer()

    fun end(): ManagedTelecomCallCommandResult = controller.end()

    fun detach(callId: String) = controller.detach(callId)

    fun snapshot(): ActiveTelecomCallSnapshot = controller.snapshot()

    fun addListener(listener: (ActiveTelecomCallSnapshot) -> Unit) = controller.addListener(listener)

    fun removeListener(listener: (ActiveTelecomCallSnapshot) -> Unit) = controller.removeListener(listener)
}

internal data class ManagedCallCandidate(
    val callId: String,
    val state: ManagedTelecomCallState
)

internal object ManagedCallSelectionPolicy {
    fun select(
        candidates: List<ManagedCallCandidate>,
        currentCallId: String?,
        preferredCallId: String? = null
    ): ManagedCallCandidate? {
        val eligible = candidates.filter { priority(it.state) > 0 }
        val highestPriority = eligible.maxOfOrNull { priority(it.state) } ?: return null
        return eligible.firstOrNull {
            it.callId == preferredCallId && priority(it.state) == highestPriority
        } ?: eligible.firstOrNull {
            it.callId == currentCallId && priority(it.state) == highestPriority
        } ?: eligible.firstOrNull { priority(it.state) == highestPriority }
    }

    private fun priority(state: ManagedTelecomCallState): Int = when (state) {
        ManagedTelecomCallState.Ringing -> 5
        ManagedTelecomCallState.Active -> 4
        ManagedTelecomCallState.Connecting -> 3
        ManagedTelecomCallState.Held -> 2
        ManagedTelecomCallState.None,
        ManagedTelecomCallState.Disconnected -> 0
    }
}
