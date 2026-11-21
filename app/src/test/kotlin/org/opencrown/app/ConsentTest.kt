package org.opencrown.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencrown.app.consent.ConsentRequest
import org.opencrown.app.consent.ConsentState

class ConsentTest {
    private fun request(
        deviceStatus: String = "supported",
        capabilities: List<String> = listOf("root", "domain", "controller"),
        persistence: String = "boot",
    ) = ConsentRequest(
        payloadId = "org.opencrown.payload.example",
        version = "1.0.0",
        publisher = "Open Crown",
        family = "page-cache",
        capabilities = capabilities,
        persistence = persistence,
        rebootRisk = "low",
        deviceStatus = deviceStatus,
    )

    private fun ready(state: ConsentState = ConsentState(request())) = state.copy(
        ownershipConfirmation = ConsentState.CONFIRMATION_PHRASE,
        bootDaemonAcknowledged = true,
    )

    @Test
    fun clean_request_has_no_blockers() {
        assertTrue(ready().blockers().isEmpty())
        assertTrue(ready().canProceed())
    }

    @Test
    fun unsupported_device_is_a_blocker() {
        assertTrue(ready(ConsentState(request(deviceStatus = "example"))).blockers().isNotEmpty())
    }

    @Test
    fun missing_controller_is_a_blocker() {
        val state = ready(ConsentState(request(capabilities = listOf("root", "domain"))))
        assertTrue(state.blockers().any { it.contains("controller") })
    }

    @Test
    fun non_boot_persistence_is_a_blocker() {
        val state = ready(ConsentState(request(persistence = "session")))
        assertTrue(state.blockers().any { it.contains("boot") })
    }

    @Test
    fun ownership_phrase_is_required() {
        val state = ready().copy(ownershipConfirmation = "yes")
        assertTrue(state.blockers().any { it.contains("I OWN THIS DEVICE") })
        assertNull(state.accept(0))
    }

    @Test
    fun daemon_acknowledgement_is_required() {
        val state = ready().copy(bootDaemonAcknowledged = false)
        assertTrue(state.blockers().any { it.contains("daemon") })
    }

    @Test
    fun accept_records_what_was_approved() {
        val record = ready().accept(1_700_000_000_000)!!
        assertEquals("org.opencrown.payload.example", record.payloadId)
        assertEquals("1.0.0", record.version)
        assertEquals(listOf("root", "domain", "controller"), record.capabilities)
        assertEquals(1_700_000_000_000, record.acceptedAtEpochMs)
        assertEquals(ConsentState.CONFIRMATION_PHRASE, record.ownershipConfirmation)
    }
}
