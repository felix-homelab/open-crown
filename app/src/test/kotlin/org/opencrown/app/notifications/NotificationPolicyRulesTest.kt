package org.opencrown.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyRulesTest {
    @Test
    fun mutedPackagesAreExcludedAndBlankNamesAreNeverCaptured() {
        val muted = setOf("com.example.chat")
        assertTrue(NotificationPolicyRules.shouldCapture(muted, "com.example.mail"))
        assertFalse(NotificationPolicyRules.shouldCapture(muted, "com.example.chat"))
        assertFalse(NotificationPolicyRules.shouldCapture(emptySet(), ""))
        assertFalse(NotificationPolicyRules.shouldCapture(emptySet(), "   "))
    }
}
