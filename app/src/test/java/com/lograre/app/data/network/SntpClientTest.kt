package com.lograre.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SntpClientTest {

    @Test
    fun `readTimeStamp decodes NTP 64-bit seconds and fractional milliseconds accurately`() {
        val client = SntpClient(elapsedRealtimeProvider = { 0L })
        val epochSeconds = 1_795_348_800L // 2026-11-22T12:00:00Z
        val ntpSeconds = epochSeconds + 2_208_988_800L
        val halfSecondFraction = 0x80000000L // 0.5 seconds = 500 ms

        val buffer = ByteArray(48)
        write32(buffer, 32, ntpSeconds)
        write32(buffer, 36, halfSecondFraction)

        val decodedMillis = client.readTimeStamp(buffer, 32)
        assertEquals(epochSeconds * 1000L + 500L, decodedMillis)
    }

    @Test
    fun `readTimeStamp returns zero for empty NTP timestamp`() {
        val client = SntpClient(elapsedRealtimeProvider = { 0L })
        val buffer = ByteArray(48)
        assertEquals(0L, client.readTimeStamp(buffer, 40))
    }

    private fun write32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value ushr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }
}
