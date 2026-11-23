package com.lograre.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningMessageFormatterTest {

    @Test
    fun `formats 5 minutes remaining cleanly`() {
        val content = WarningMessageFormatter.format("Instagram", 5 * 60_000L)
        assertEquals("Quedan 5 min en Instagram", content.title)
        assertTrue(content.body.contains("Instagram"))
    }

    @Test
    fun `rounds fractional minutes up cleanly and handles sub-minute values`() {
        val fourAndHalf = WarningMessageFormatter.format("YouTube", 270_000L)
        assertEquals("Quedan 5 min en YouTube", fourAndHalf.title)

        val oneMinute = WarningMessageFormatter.format("TikTok", 60_000L)
        assertEquals("Quedan 1 min en TikTok", oneMinute.title)

        val thirtySeconds = WarningMessageFormatter.format("TikTok", 30_000L)
        assertEquals("Quedan 30 s en TikTok", thirtySeconds.title)
    }

    @Test
    fun `generates distinct positive notification ids per package`() {
        val idA = WarningMessageFormatter.notificationIdFor("com.instagram.android")
        val idB = WarningMessageFormatter.notificationIdFor("com.google.android.youtube")

        assertTrue(idA > 0)
        assertTrue(idB > 0)
        assertNotEquals(idA, idB)
    }
}
