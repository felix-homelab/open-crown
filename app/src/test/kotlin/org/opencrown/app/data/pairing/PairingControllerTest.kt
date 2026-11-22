package org.opencrown.app.data.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingControllerTest {
    @Test
    fun unavailableGatewayNeverReportsSecured() = runBlocking {
        val controller = PairingController(UnavailablePairingGateway())
        controller.select("internal-address", "Test Watch")
        controller.begin()
        assertTrue(controller.stage.value is PairingStage.Failed)
        assertEquals(
            PairingFailure.TransportUnavailable,
            (controller.stage.value as PairingStage.Failed).failure,
        )
    }

    @Test
    fun malformedPinIsRejectedBeforeTheGateway() = runBlocking {
        val controller = PairingController(UnavailablePairingGateway())
        controller.select("internal-address", null)
        assertFalse(controller.submitPin("12"))
        assertFalse(controller.submitPin("12ab56"))
        assertTrue(controller.stage.value is PairingStage.Selected)
    }

    @Test
    fun validPinWithoutSessionFailsInsteadOfPretending() = runBlocking {
        val controller = PairingController(UnavailablePairingGateway())
        controller.select("internal-address", null)
        assertTrue(controller.submitPin("123456"))
        val stage = controller.stage.value
        assertTrue(stage is PairingStage.Failed)
        assertTrue((stage as PairingStage.Failed).failure is PairingFailure.Protocol)
    }
}
