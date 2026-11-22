package org.opencrown.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.opencrown.app.model.AvailabilityReason
import org.opencrown.app.model.BrokerSnapshot
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.DataOrigin
import org.opencrown.app.model.LinkPhase
import org.opencrown.app.model.Observation
import org.opencrown.app.model.PrivilegeSnapshot
import org.opencrown.app.model.Verification

/**
 * Builds the one honest snapshot the shell renders.
 *
 * Rules:
 * - every field comes from a probe or a sealed local record;
 * - no field is invented when a probe is unavailable;
 * - a socket connect is never promoted to a watch session;
 * - a stored pair is never promoted to a live link.
 */
class CrownRepository(
    context: Context,
    private val store: PairRecordStore,
    private val agentLinkProbe: AgentLinkProbe = AgentLinkProbe(),
) {
    private val appContext = context.applicationContext
    private val _snapshot = MutableStateFlow<CrownSnapshot?>(null)
    val snapshot: StateFlow<CrownSnapshot?> = _snapshot.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    suspend fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        try {
            val next = withContext(Dispatchers.IO) { buildSnapshot() }
            _snapshot.value = next
        } finally {
            _refreshing.value = false
        }
    }

    private fun buildSnapshot(): CrownSnapshot {
        val now = System.currentTimeMillis()
        val phone = PhoneFactsReader.read(appContext)
        val payload = PayloadProbe.probe(appContext)
        val relay = RelayMarkerProbe.probe()
        val broker = agentLinkProbe.probe()
        val bluetooth = BluetoothProbe.probe(appContext)
        val records = runCatching { store.load() }.getOrDefault(emptyList())
        val active = records.maxByOrNull { it.lastSeenAtMillis }
        val activeObservation: Observation<org.opencrown.app.model.PairRecord> =
            active?.toObservation() ?: Observation.absent(
                reason = AvailabilityReason.NOT_PAIRED,
                detail = "No watch pairing record exists on this phone.",
                origin = DataOrigin.LOCAL_STORE,
                nowMillis = now,
            )
        val link = deriveLink(activeObservation, broker, now)
        return CrownSnapshot(
            phone = phone,
            privilege = PrivilegeSnapshot(payloadCompatible = payload, relayMarker = relay, broker = broker),
            bluetooth = bluetooth,
            pairRecords = records,
            activePair = activeObservation,
            link = link,
            generatedAtMillis = now,
        )
    }

    /**
     * A pair record plus a reachable agent socket is still not a link. Until a
     * protocol session reports its state, the link is absent with a reason.
     */
    private fun deriveLink(
        pair: Observation<org.opencrown.app.model.PairRecord>,
        broker: BrokerSnapshot,
        now: Long,
    ): Observation<LinkPhase> {
        if (pair.valueOrNull == null) {
            return Observation.absent(
                reason = AvailabilityReason.NOT_PAIRED,
                detail = "No watch is paired.",
                origin = DataOrigin.DERIVED,
                nowMillis = now,
            )
        }
        if (broker.availability.valueOrNull == null) {
            return Observation.absent(
                reason = AvailabilityReason.BROKER_UNAVAILABLE,
                detail = "The privileged agent is not reachable, so no live link can be observed.",
                origin = DataOrigin.DERIVED,
                pairId = pair.valueOrNull?.id,
                epoch = pair.valueOrNull?.epoch,
                nowMillis = now,
            )
        }
        return Observation.absent(
            reason = AvailabilityReason.NEVER_OBSERVED,
            detail = "The agent socket is reachable, but no watch session has reported a link state.",
            origin = DataOrigin.DERIVED,
            verification = Verification.UNKNOWN,
            pairId = pair.valueOrNull?.id,
            epoch = pair.valueOrNull?.epoch,
            nowMillis = now,
        )
    }
}

/** Process-wide graph. Deliberately small; no service locator framework. */
class AppGraph(context: Context) {
    val pairStore: PairRecordStore = KeystorePairRecordStore(context)
    val crown = CrownRepository(context, pairStore)
    val chain = ChainController(context)
    val onboarding = OnboardingStore(context)
    val featureRegistry: FeatureRegistry.LoadResult = FeatureRegistry.load(context)
    val core: CoreStatus = RustCore.probe()
    private val discoveryCore = runCatching {
        org.opencrown.ffi.DiscoveryBridge(core.rules?.discoveryTtlMs ?: 10_000UL)
    }.getOrNull()
    val discovery = BleDiscoveryRepository(context, discoveryCore)
}