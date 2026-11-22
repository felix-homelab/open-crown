package org.opencrown.app.data.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why pairing cannot advance. Typed, so the UI cannot invent a reason. */
sealed interface PairingFailure {
    data object TransportUnavailable : PairingFailure
    data object PrivilegeUnavailable : PairingFailure
    data object PermissionDenied : PairingFailure
    data object BluetoothOff : PairingFailure
    data class Rejected(val detail: String) : PairingFailure
    data class Protocol(val code: String, val detail: String) : PairingFailure
}

sealed interface PairingStage {
    data object Idle : PairingStage
    data object Scanning : PairingStage
    data class Selected(val address: String, val displayName: String?) : PairingStage
    data class AwaitingPin(val sessionToken: String, val attempt: Int) : PairingStage
    data class Securing(val detail: String) : PairingStage
    data object Secured : PairingStage
    data class Failed(val failure: PairingFailure) : PairingStage
}

/**
 * The seam between pairing UI and the future protocol implementation.
 *
 * There is intentionally no implementation that returns success. Until a Lane A
 * spec and a real transport exist, [UnavailableGateway] is the only gateway the
 * app wires, and every operation ends in [PairingFailure.TransportUnavailable].
 */
interface PairingGateway {
    suspend fun begin(address: String): PairingStage
    suspend fun submitPin(sessionToken: String, pin: String): PairingStage
}

class UnavailablePairingGateway(private val reason: PairingFailure = PairingFailure.TransportUnavailable) : PairingGateway {
    override suspend fun begin(address: String): PairingStage = PairingStage.Failed(reason)
    override suspend fun submitPin(sessionToken: String, pin: String): PairingStage = PairingStage.Failed(reason)
}

/**
 * Pairing UI state machine.
 *
 * A PIN is validated as six digits before it is handed to the gateway. A
 * syntactically valid PIN is not treated as accepted; only the gateway's
 * [PairingStage.Secured] is.
 */
class PairingController(private val gateway: PairingGateway) {
    private val _stage = MutableStateFlow<PairingStage>(PairingStage.Idle)
    val stage: StateFlow<PairingStage> = _stage.asStateFlow()

    private var sessionToken: String? = null

    fun select(address: String, displayName: String?) {
        _stage.value = PairingStage.Selected(address, displayName)
    }

    fun cancel() {
        sessionToken = null
        _stage.value = PairingStage.Idle
    }

    suspend fun begin() {
        val selected = _stage.value as? PairingStage.Selected ?: return
        _stage.value = PairingStage.Securing("Opening the pairing transport")
        when (val result = gateway.begin(selected.address)) {
            is PairingStage.Failed -> _stage.value = result
            PairingStage.Secured -> _stage.value = PairingStage.Secured
            is PairingStage.AwaitingPin -> {
                sessionToken = result.sessionToken
                _stage.value = result
            }
            else -> _stage.value = PairingStage.Failed(
                PairingFailure.Protocol("unexpected", "Gateway returned an unexpected stage.")
            )
        }
    }

    /** Returns false when the PIN shape is wrong; does not claim a pairing. */
    suspend fun submitPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        val token = sessionToken ?: run {
            _stage.value = PairingStage.Failed(PairingFailure.Protocol("no-session", "No pairing session is open."))
            return true
        }
        _stage.value = PairingStage.Securing("Verifying the pairing code")
        when (val result = gateway.submitPin(token, pin)) {
            is PairingStage.Failed -> _stage.value = result
            PairingStage.Secured -> _stage.value = PairingStage.Secured
            is PairingStage.AwaitingPin -> {
                sessionToken = result.sessionToken
                _stage.value = result
            }
            else -> _stage.value = PairingStage.Failed(
                PairingFailure.Protocol("unexpected", "Gateway returned an unexpected stage.")
            )
        }
        return true
    }
}