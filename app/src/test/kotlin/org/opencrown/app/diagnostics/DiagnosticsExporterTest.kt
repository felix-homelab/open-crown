package org.opencrown.app.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencrown.app.data.CoreStatus
import org.opencrown.app.model.AvailabilityReason
import org.opencrown.app.model.BluetoothSnapshot
import org.opencrown.app.model.BrokerSnapshot
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.DataOrigin
import org.opencrown.app.model.Observation
import org.opencrown.app.model.PhoneFacts
import org.opencrown.app.model.PrivilegeSnapshot

class DiagnosticsExporterTest {
    @Test
    fun reportIsBoundedToRedactedFacts() {
        val snapshot = CrownSnapshot(
            phone = PhoneFacts("SM-TEST", "test", "14", 34, "arm64-v8a", "5.4.0-test", 3),
            privilege = PrivilegeSnapshot(
                payloadCompatible = Observation.absent(
                    reason = AvailabilityReason.UNSUPPORTED,
                    detail = "no profile",
                    origin = DataOrigin.LOCAL_STORE,
                ),
                relayMarker = Observation.absent(
                    reason = AvailabilityReason.PRIVILEGE_UNAVAILABLE,
                    detail = "no relay",
                    origin = DataOrigin.ANDROID_PLATFORM,
                ),
                broker = BrokerSnapshot(
                    availability = Observation.absent(
                        reason = AvailabilityReason.AGENT_UNAVAILABLE,
                        detail = "no listener",
                        origin = DataOrigin.ANDROID_PLATFORM,
                    ),
                    socketName = "dfi_init",
                    lastAttemptMillis = 0,
                ),
            ),
            bluetooth = BluetoothSnapshot(
                adapterAvailable = true,
                enabled = Observation.present(true, DataOrigin.ANDROID_PLATFORM),
                scanPermission = false,
                connectPermission = false,
            ),
            pairRecords = emptyList(),
            activePair = Observation.absent(
                reason = AvailabilityReason.NOT_PAIRED,
                detail = "none",
                origin = DataOrigin.LOCAL_STORE,
            ),
            link = Observation.absent(
                reason = AvailabilityReason.NOT_PAIRED,
                detail = "none",
                origin = DataOrigin.DERIVED,
            ),
            generatedAtMillis = 1_000,
        )
        val report = DiagnosticsExporter.buildReport(
            nowMillis = 1_000,
            appVersion = "0.1.0",
            snapshot = snapshot,
            core = CoreStatus(false, null, null, "unavailable"),
            registry = null,
        )
        assertTrue(report.contains("SM-TEST"))
        assertTrue(report.contains("pair-records: 0"))
        assertTrue(report.contains(DiagnosticsExporter.REDACTION_NOTE))
        assertTrue(report.contains("registry"))
        assertFalse(report.contains("pairing-key:"))
        assertFalse(report.contains("notification-content:"))
    }
}
