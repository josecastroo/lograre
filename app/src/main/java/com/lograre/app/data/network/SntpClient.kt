package com.lograre.app.data.network

import android.os.SystemClock
import com.lograre.app.domain.clock.NtpTimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Lightweight SNTP (RFC 4330) client over UDP port 123.
 *
 * Computes UTC time from server receive/transmit timestamps and local monotonic
 * [SystemClock.elapsedRealtime] round-trip delay, so it remains accurate even if
 * the device's system wall clock has been tampered with.
 */
class SntpClient(
    private val servers: List<String> = DEFAULT_SERVERS,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) : NtpTimeProvider {

    override suspend fun fetchCurrentUtcMillis(): Long? = withContext(ioDispatcher) {
        for (server in servers) {
            val result = queryServer(server)
            if (result != null) {
                return@withContext result
            }
        }
        null
    }

    private fun queryServer(host: String): Long? = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMillis
            val address = InetAddress.getByName(host)
            val buffer = ByteArray(NTP_PACKET_SIZE)
            buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

            val requestPacket = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
            val requestTicks = elapsedRealtimeProvider()
            socket.send(requestPacket)

            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            val responseTicks = elapsedRealtimeProvider()

            val receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET)
            val transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET)
            if (receiveTime <= 0L || transmitTime <= 0L) {
                return null
            }

            val roundTripTicks = (responseTicks - requestTicks).coerceAtLeast(0L)
            val serverProcessingTime = (transmitTime - receiveTime).coerceAtLeast(0L)
            val networkDelay = (roundTripTicks - serverProcessingTime).coerceAtLeast(0L)
            transmitTime + (networkDelay / 2L)
        }
    } catch (_: Exception) {
        null
    }

    internal fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        val seconds = read32(buffer, offset)
        val fraction = read32(buffer, offset + 4)
        if (seconds == 0L && fraction == 0L) {
            return 0L
        }
        return ((seconds - OFFSET_1900_TO_1970) * 1000L) + ((fraction * 1000L) / 0x100000000L)
    }

    private fun read32(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toLong() and 0xFF
        val b1 = buffer[offset + 1].toLong() and 0xFF
        val b2 = buffer[offset + 2].toLong() and 0xFF
        val b3 = buffer[offset + 3].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    companion object {
        val DEFAULT_SERVERS = listOf(
            "time.google.com",
            "time.cloudflare.com",
            "pool.ntp.org"
        )
        const val DEFAULT_TIMEOUT_MILLIS = 3_000
        private const val NTP_PORT = 123
        private const val NTP_PACKET_SIZE = 48
        private const val NTP_MODE_CLIENT = 3
        private const val NTP_VERSION = 3
        private const val RECEIVE_TIME_OFFSET = 32
        private const val TRANSMIT_TIME_OFFSET = 40
        private const val OFFSET_1900_TO_1970 = 2_208_988_800L
    }
}
