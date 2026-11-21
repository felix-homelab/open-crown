package org.opencrown.app.consent

/**
 * What the user is being asked to approve.
 *
 * Mirrors the manifest fields the UI must show before a payload runs
 * (docs/04-payload-system.md section 9).
 */
data class ConsentRequest(
    val payloadId: String,
    val version: String,
    val publisher: String,
    val family: String,
    val capabilities: List<String>,
    val persistence: String,
    val rebootRisk: String,
    val deviceStatus: String,
)

/** What gets recorded once consent is given. */
data class ConsentRecord(
    val payloadId: String,
    val version: String,
    val capabilities: List<String>,
    val acceptedAtEpochMs: Long,
    val ownershipConfirmation: String,
)

/**
 * Consent is a gate, not a checkbox. Ownership is affirmed by typing the phrase
 * (docs/09-legal-ethics.md section 1), and the resident daemon is acknowledged
 * explicitly because it is the part users will notice.
 */
data class ConsentState(
    val request: ConsentRequest,
    val ownershipConfirmation: String = "",
    val bootDaemonAcknowledged: Boolean = false,
) {
    fun blockers(): List<String> = buildList {
        if (request.deviceStatus != "supported") {
            add("This device is not on the supported list (" + request.deviceStatus + ")")
        }
        if ("controller" !in request.capabilities) {
            add("The payload does not grant the controller capability")
        }
        if (request.persistence != "boot") {
            add("Payload persistence must be boot")
        }
        if (ownershipConfirmation.trim() != CONFIRMATION_PHRASE) {
            add("Type " + CONFIRMATION_PHRASE + " to confirm ownership")
        }
        if (!bootDaemonAcknowledged) {
            add("Acknowledge that a root daemon runs until reboot")
        }
    }

    fun canProceed(): Boolean = blockers().isEmpty()

    fun accept(epochMs: Long): ConsentRecord? {
        if (!canProceed()) {
            return null
        }
        return ConsentRecord(
            payloadId = request.payloadId,
            version = request.version,
            capabilities = request.capabilities,
            acceptedAtEpochMs = epochMs,
            ownershipConfirmation = ownershipConfirmation.trim(),
        )
    }

    companion object {
        const val CONFIRMATION_PHRASE = "I OWN THIS DEVICE"
    }
}
