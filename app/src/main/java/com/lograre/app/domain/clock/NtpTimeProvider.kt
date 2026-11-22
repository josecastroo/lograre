package com.lograre.app.domain.clock

interface NtpTimeProvider {
    /**
     * Fetches the current UTC epoch time in milliseconds from an NTP server,
     * or returns null if offline or unreachable.
     */
    suspend fun fetchCurrentUtcMillis(): Long?
}
