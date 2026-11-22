package org.opencrown.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSanitizerTest {
    @Test
    fun blankAndControlOnlyContentIsDropped() {
        assertNull(
            NotificationSanitizer.from(
                key = "k", packageName = "p", appLabel = null,
                title = " \n\t ", text = null,
                postedAtMillis = 0, category = null, ongoing = false,
            )
        )
    }

    @Test
    fun controlCharactersAreReplacedAndTextIsBounded() {
        val captured = NotificationSanitizer.from(
            key = "k", packageName = "p", appLabel = "App",
            title = "Hello\u0000 world",
            text = "x".repeat(NotificationSanitizer.MAX_TEXT + 50),
            postedAtMillis = 1, category = "msg", ongoing = false,
        ) ?: error("expected a capture")
        assertEquals("Hello world", captured.title)
        assertEquals(NotificationSanitizer.MAX_TEXT, captured.text?.length)
    }

    @Test
    fun whitespaceRunsCollapse() {
        assertEquals("a b c", NotificationSanitizer.clean("  a\n\nb\t c  "))
    }

    @Test
    fun titleAloneIsStillRecorded() {
        val captured = NotificationSanitizer.from(
            key = "k", packageName = "p", appLabel = null,
            title = "Only a title", text = null,
            postedAtMillis = 1, category = null, ongoing = false,
        )
        assertTrue(captured != null && captured.text == null)
    }
}
