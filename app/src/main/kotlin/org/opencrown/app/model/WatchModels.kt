package org.opencrown.app.model

/** Connection health as a state, not a boolean. */
enum class LinkPhase {
    UNKNOWN,
    BLUETOOTH_OFF,
    NO_PERMISSION,
    IDLE,
    ADVERTISING,
    CONNECTING,
    CONNECTED_UNSECURED,
    CONNECTED_SECURED,
    RECONNECTING,
    FAILED,
}

/** Pairing/setup lifecycle. Mirrors the persisted checkpoints, not UI theatre. */
enum class PairPhase {
    UNPAIRED,
    DISCOVERED,
    SELECTED,
    PAIRING,
    SECURING,
    ACTIVATING,
    SYNCING,
    READY,
    RECONNECTING,
    ERROR,
}

/** A watch identity. The serial is never displayed or logged; [stableId] is local. */
data class WatchIdentity(
    val stableId: String,
    val displayName: String,
    val model: String?,
    val osVersion: String?,
    val advertisedName: String?,
)

/** A locally sealed pairing record. There is no secret material in this type. */
data class PairRecord(
    val id: String,
    val watch: WatchIdentity,
    val epoch: Long,
    val createdAtMillis: Long,
    val lastSeenAtMillis: Long,
    val phase: PairPhase,
) {
    fun toObservation(nowMillis: Long = System.currentTimeMillis()): Observation<PairRecord> =
        Observation.present(
            value = this,
            origin = DataOrigin.LOCAL_STORE,
            verification = Verification.STALE,
            pairId = id,
            epoch = epoch,
            note = "Restored from the sealed store; not a live link.",
            nowMillis = nowMillis,
        )
}

/** Phone facts that determine whether privilege is even possible. */
data class PhoneFacts(
    val model: String,
    val device: String,
    val androidRelease: String,
    val androidSdk: Int,
    val abi: String,
    val kernel: String,
    val bootCount: Int,
)

/** State of the per-boot privilege path. */
data class PrivilegeSnapshot(
    val payloadCompatible: Observation<String>,
    val relayMarker: Observation<String>,
    val broker: BrokerSnapshot,
)

/** Result of trying to reach the privileged agent. */
data class BrokerSnapshot(
    val availability: Observation<String>,
    val socketName: String,
    val lastAttemptMillis: Long,
) {
    companion object {
        fun unknown(nowMillis: Long = System.currentTimeMillis()): BrokerSnapshot =
            BrokerSnapshot(
                availability = Observation.absent(
                    reason = AvailabilityReason.NEVER_OBSERVED,
                    detail = "The agent channel has not been probed in this process.",
                    origin = DataOrigin.DERIVED,
                    nowMillis = nowMillis,
                ),
                socketName = DEFAULT_SOCKET,
                lastAttemptMillis = 0L,
            )

        const val DEFAULT_SOCKET: String = "dfi_init"
    }
}

/** Android Bluetooth state as the app can observe it without privilege. */
data class BluetoothSnapshot(
    val adapterAvailable: Boolean,
    val enabled: Observation<Boolean>,
    val scanPermission: Boolean,
    val connectPermission: Boolean,
)

/** Everything the shell needs for one render. */
data class CrownSnapshot(
    val phone: PhoneFacts,
    val privilege: PrivilegeSnapshot,
    val bluetooth: BluetoothSnapshot,
    val pairRecords: List<PairRecord>,
    val activePair: Observation<PairRecord>,
    val link: Observation<LinkPhase>,
    val generatedAtMillis: Long,
) {
    val hasPairedWatch: Boolean get() = pairRecords.isNotEmpty()
    val transportReady: Boolean get() = link.valueOrNull == LinkPhase.CONNECTED_SECURED
}
