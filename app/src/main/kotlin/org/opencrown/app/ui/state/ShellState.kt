package org.opencrown.app.ui.state

import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.data.FeatureStatus
import org.opencrown.app.model.AvailabilityReason
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.LinkPhase
import org.opencrown.app.model.Observation

/** One row of the home setup checklist. Never a spinner without a reason. */
data class CheckpointRow(
    val id: String,
    val title: String,
    val state: CheckpointState,
    val detail: String,
)

enum class CheckpointState { DONE, ACTIVE, PENDING, BLOCKED, FAILED }

/** A quick action on Home; disabled actions must carry a reason. */
data class QuickAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val reason: String?,
)

/** Derived home state. Pure function of the snapshot and the registry. */
data class HomeModel(
    val title: String,
    val subtitle: String,
    val watchName: Observation<String>,
    val link: Observation<LinkPhase>,
    val checkpoints: List<CheckpointRow>,
    val actions: List<QuickAction>,
    val registryCounts: Map<FeatureStatus, Int>,
)

object ShellState {
    fun home(snapshot: CrownSnapshot?, registry: FeatureRegistry?): HomeModel {
        if (snapshot == null) {
            return HomeModel(
                title = "Open Crown",
                subtitle = "Reading the phone",
                watchName = Observation.absent(
                    reason = AvailabilityReason.NEVER_OBSERVED,
                    detail = "The first probe has not completed.",
                    origin = org.opencrown.app.model.DataOrigin.DERIVED,
                ),
                link = Observation.absent(
                    reason = AvailabilityReason.NEVER_OBSERVED,
                    detail = "The first probe has not completed.",
                    origin = org.opencrown.app.model.DataOrigin.DERIVED,
                ),
                checkpoints = emptyList(),
                actions = emptyList(),
                registryCounts = emptyMap(),
            )
        }
        val pair = snapshot.activePair
        val pairedRecord = pair.valueOrNull
        val watchName = if (pairedRecord != null) {
            Observation.present(
                value = pairedRecord.watch.displayName,
                origin = pair.provenance.origin,
                verification = pair.provenance.verification,
                pairId = pair.provenance.pairId,
                epoch = pair.provenance.epoch,
                note = pair.provenance.note,
                nowMillis = pair.provenance.observedAtMillis,
            )
        } else {
            Observation.absent(
                reason = AvailabilityReason.NOT_PAIRED,
                detail = (pair as? Observation.Absent)?.detail ?: "No watch is paired.",
                origin = pair.provenance.origin,
                nowMillis = pair.provenance.observedAtMillis,
            )
        }

        val privilegeReady = snapshot.privilege.broker.availability.valueOrNull != null
        val priv = snapshot.privilege
        val checkpoints = listOf(
            CheckpointRow(
                id = "phone",
                title = "Phone checked",
                state = if (snapshot.phone.abi == "arm64-v8a") CheckpointState.DONE else CheckpointState.BLOCKED,
                detail = snapshot.phone.model + " · Android " + snapshot.phone.androidSdk +
                    " · " + snapshot.phone.abi,
            ),
            CheckpointRow(
                id = "payload",
                title = "Payload profile matched",
                state = when {
                    priv.payloadCompatible.valueOrNull == null -> CheckpointState.BLOCKED
                    priv.payloadCompatible.provenance.verification == org.opencrown.app.model.Verification.CONFIRMED -> CheckpointState.DONE
                    else -> CheckpointState.PENDING
                },
                detail = priv.payloadCompatible.valueOrNull ?: (priv.payloadCompatible as? Observation.Absent)?.detail ?: "Unknown",
            ),
            CheckpointRow(
                id = "relay",
                title = "Per-boot privilege",
                state = when {
                    priv.relayMarker.valueOrNull == null -> CheckpointState.PENDING
                    privilegeReady -> CheckpointState.ACTIVE
                    else -> CheckpointState.BLOCKED
                },
                detail = when {
                    priv.relayMarker.valueOrNull == null ->
                        (priv.relayMarker as? Observation.Absent)?.detail ?: "Not run on this boot."
                    privilegeReady ->
                        "Relay present and the agent socket accepts a connection; no session is established."
                    else ->
                        "The chain ran, but this app cannot use the relay. " +
                            ((priv.broker.availability as? Observation.Absent)?.detail ?: "")
                }.trim(),
            ),
            CheckpointRow(
                id = "agent",
                title = "Agent channel",
                state = if (privilegeReady) CheckpointState.DONE else CheckpointState.BLOCKED,
                detail = priv.broker.availability.valueOrNull
                    ?: (priv.broker.availability as? Observation.Absent)?.detail
                    ?: "Not probed",
            ),
            CheckpointRow(
                id = "pair",
                title = "Watch paired",
                state = if (pair.valueOrNull != null) CheckpointState.DONE else CheckpointState.PENDING,
                detail = pair.valueOrNull?.let { "Paired on this phone; live state not observed." }
                    ?: (pair as? Observation.Absent)?.detail ?: "Not paired",
            ),
            CheckpointRow(
                id = "link",
                title = "Secure link",
                state = when (snapshot.link.valueOrNull) {
                    LinkPhase.CONNECTED_SECURED -> CheckpointState.DONE
                    LinkPhase.CONNECTING, LinkPhase.RECONNECTING, LinkPhase.CONNECTED_UNSECURED -> CheckpointState.ACTIVE
                    LinkPhase.FAILED -> CheckpointState.FAILED
                    else -> CheckpointState.PENDING
                },
                detail = snapshot.link.valueOrNull?.name
                    ?: (snapshot.link as? Observation.Absent)?.detail
                    ?: "No link observation",
            ),
            CheckpointRow(
                id = "setup",
                title = "Setup complete",
                state = CheckpointState.PENDING,
                detail = "Requires activation and setup protocol, which is not implemented.",
            ),
            CheckpointRow(
                id = "face",
                title = "Face observed",
                state = CheckpointState.PENDING,
                detail = "Requires a live watch report after setup.",
            ),
        )

        val actions = listOf(
            QuickAction(
                id = "discover",
                title = "Pair a watch",
                subtitle = "Scan for a nearby watch in pairing mode",
                enabled = snapshot.bluetooth.scanPermission && snapshot.bluetooth.enabled.valueOrNull == true,
                reason = when {
                    !snapshot.bluetooth.scanPermission -> "Nearby devices permission is required."
                    snapshot.bluetooth.enabled.valueOrNull != true -> "Bluetooth is off."
                    else -> null
                },
            ),
            QuickAction(
                id = "faces",
                title = "Watch faces",
                subtitle = "Inventory, edit and select faces",
                enabled = snapshot.transportReady,
                reason = if (snapshot.transportReady) null else "Requires a secure watch link.",
            ),
            QuickAction(
                id = "notifications",
                title = "Notifications",
                subtitle = "Mirror alerts from selected Android apps",
                enabled = snapshot.transportReady,
                reason = if (snapshot.transportReady) null else "Requires a secure watch link.",
            ),
            QuickAction(
                id = "health",
                title = "Health and activity",
                subtitle = "Activity rings, heart rate and workouts",
                enabled = snapshot.transportReady,
                reason = if (snapshot.transportReady) null else "Requires a secure watch link.",
            ),
            QuickAction(
                id = "apps",
                title = "Apps and software",
                subtitle = "Installed watch apps and update state",
                enabled = snapshot.transportReady,
                reason = if (snapshot.transportReady) null else "Requires a secure watch link.",
            ),
        )

        return HomeModel(
            title = "Open Crown",
            subtitle = if (pair.valueOrNull != null) "Paired · not connected" else "No watch paired",
            watchName = watchName,
            link = snapshot.link,
            checkpoints = checkpoints,
            actions = actions,
            registryCounts = registry?.counts?.byStatus ?: emptyMap(),
        )
    }
}