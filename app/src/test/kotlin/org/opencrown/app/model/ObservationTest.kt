package org.opencrown.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationTest {
    @Test
    fun absentHasNoValueAndKeepsReason() {
        val absent = Observation.absent(
            reason = AvailabilityReason.BROKER_UNAVAILABLE,
            detail = "socket denied",
            origin = DataOrigin.ANDROID_PLATFORM,
            nowMillis = 1_000L,
        )
        assertNull(absent.valueOrNull)
        assertEquals(AvailabilityReason.BROKER_UNAVAILABLE, (absent as Observation.Absent).reason)
        assertEquals(1_000L, absent.provenance.observedAtMillis)
    }

    @Test
    fun presentBecomesStaleAfterAge() {
        val value = Observation.present(
            value = 42,
            origin = DataOrigin.WATCH_REPORT,
            verification = Verification.CONFIRMED,
            nowMillis = 1_000L,
        )
        assertFalse(value.isStale(nowMillis = 2_000L, maxAgeMillis = 5_000L))
        assertTrue(value.isStale(nowMillis = 10_000L, maxAgeMillis = 5_000L))
        assertEquals(42, value.valueOrNull)
    }

    @Test
    fun storedPairIsNeverPresentedAsLive() {
        val record = PairRecord(
            id = "pair-1",
            watch = WatchIdentity("w1", "Test Watch", "Model", "10.0", null),
            epoch = 1,
            createdAtMillis = 1_000L,
            lastSeenAtMillis = 2_000L,
            phase = PairPhase.READY,
        )
        val observation = record.toObservation(nowMillis = 3_000L)
        assertEquals(Verification.STALE, observation.provenance.verification)
        assertEquals(DataOrigin.LOCAL_STORE, observation.provenance.origin)
    }

    @Test
    fun onlyObservedAndFailuresAreTerminal() {
        val p = Provenance(DataOrigin.AGENT_REPORT, Verification.REPORTED, 0L)
        assertFalse(CommandStatus("1", "set", CommandState.ACKNOWLEDGED, "", p).isTerminal)
        assertTrue(CommandStatus("2", "set", CommandState.OBSERVED, "", p).isTerminal)
        assertTrue(CommandStatus("3", "set", CommandState.FAILED, "", p).isTerminal)
    }

    @Test
    fun presentConfidenceIsIndependentOfAge() {
        val observation = Observation.present(
            value = "wrist=left",
            origin = DataOrigin.WATCH_REPORT,
            verification = Verification.REPORTED,
            nowMillis = 5_000L,
        )
        assertEquals(0L, observation.ageMillis(nowMillis = 5_000L))
        assertEquals(2_000L, observation.ageMillis(nowMillis = 7_000L))
    }
}
