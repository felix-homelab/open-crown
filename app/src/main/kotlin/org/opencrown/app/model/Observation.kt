package org.opencrown.app.model

/**
 * Where a value came from.
 *
 * The distinction matters more than the value itself: a Bluetooth advertisement
 * is not a connection, a queued write is not an applied setting, and a cached
 * record is not a live observation. The UI is required to render the origin.
 */
enum class DataOrigin {
    /** Reported by the watch over an established protocol channel. */
    WATCH_REPORT,

    /** Reported by the privileged agent about its own state. */
    AGENT_REPORT,

    /** Decoded from an over-the-air advertisement. Never treated as a connection. */
    ADVERTISEMENT,

    /** Read from the Android platform (adapter, permissions, build). */
    ANDROID_PLATFORM,

    /** Read from the app's sealed local store. */
    LOCAL_STORE,

    /** Entered or chosen by the user in this app. */
    USER_INPUT,

    /** Computed by the app from other observations. */
    DERIVED,
}

/**
 * How strongly the value is established.
 *
 * [CONFIRMED] is reserved for a value the watch itself acknowledged *and* that
 * was subsequently observed. A write that was merely sent is [REPORTED] at
 * best, and a record restored from disk after a restart is [STALE] until a live
 * observation replaces it.
 */
enum class Verification {
    CONFIRMED,
    REPORTED,
    INFERRED,
    STALE,
    UNKNOWN,
}

/**
 * Provenance for a single UI value.
 *
 * @param pairId the pairing this value belongs to, when it is watch-scoped.
 * @param epoch the pairing epoch, so a reset watch can never be confused with
 *   its previous identity.
 */
data class Provenance(
    val origin: DataOrigin,
    val verification: Verification,
    val observedAtMillis: Long,
    val pairId: String? = null,
    val epoch: Long? = null,
    val note: String? = null,
)

/** Why a value is not currently available. */
enum class AvailabilityReason {
    NOT_PAIRED,
    TRANSPORT_DOWN,
    PRIVILEGE_UNAVAILABLE,
    AGENT_UNAVAILABLE,
    BROKER_UNAVAILABLE,
    PERMISSION_DENIED,
    BLUETOOTH_OFF,
    BLOCKED,
    UNSUPPORTED,
    NEVER_OBSERVED,
    TIMEOUT,
    ERROR,
}

/**
 * A value that may or may not be known.
 *
 * This is deliberately not "T with null". Null forces every call site to guess
 * between "no watch", "watch did not report", and "request failed", and the
 * usual result is a UI that shows a plausible default. An [Absent] value has a
 * reason and provenance and is rendered as unavailable.
 */
sealed interface Observation<out T> {
    val provenance: Provenance

    data class Present<T>(
        val value: T,
        override val provenance: Provenance,
    ) : Observation<T>

    data class Absent(
        val reason: AvailabilityReason,
        val detail: String,
        override val provenance: Provenance,
    ) : Observation<Nothing>

    val valueOrNull: T?
        get() = (this as? Present)?.value

    fun ageMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (nowMillis - provenance.observedAtMillis).coerceAtLeast(0L)

    fun isStale(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = DEFAULT_STALE_MS): Boolean =
        provenance.verification == Verification.STALE || ageMillis(nowMillis) > maxAgeMillis

    companion object {
        const val DEFAULT_STALE_MS: Long = 30_000L

        fun <T> present(
            value: T,
            origin: DataOrigin,
            verification: Verification = Verification.REPORTED,
            pairId: String? = null,
            epoch: Long? = null,
            note: String? = null,
            nowMillis: Long = System.currentTimeMillis(),
        ): Observation<T> = Present(
            value = value,
            provenance = Provenance(origin, verification, nowMillis, pairId, epoch, note),
        )

        fun absent(
            reason: AvailabilityReason,
            detail: String,
            origin: DataOrigin,
            verification: Verification = Verification.UNKNOWN,
            pairId: String? = null,
            epoch: Long? = null,
            nowMillis: Long = System.currentTimeMillis(),
        ): Observation<Nothing> = Absent(
            reason = reason,
            detail = detail,
            provenance = Provenance(origin, verification, nowMillis, pairId, epoch),
        )
    }
}

/**
 * State of a command sent toward the watch.
 *
 * A command moves forward only on evidence. [QUEUED] and [SENT] are never
 * rendered as "done"; [ACKNOWLEDGED] means the transport accepted it; only a
 * later watch observation moves the command to [OBSERVED].
 */
enum class CommandState {
    QUEUED,
    SENT,
    ACKNOWLEDGED,
    OBSERVED,
    FAILED,
    EXPIRED,
}

data class CommandStatus(
    val id: String,
    val label: String,
    val state: CommandState,
    val detail: String,
    val provenance: Provenance,
) {
    val isTerminal: Boolean
        get() = state == CommandState.OBSERVED || state == CommandState.FAILED || state == CommandState.EXPIRED
}
