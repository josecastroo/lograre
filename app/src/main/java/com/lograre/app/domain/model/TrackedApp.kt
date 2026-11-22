package com.lograre.app.domain.model

data class TrackedApp(
    val packageName: String,
    val dailyLimitMinutes: Int,
    val usedMillisToday: Long = 0L,
    val extraMillisToday: Long = 0L,
    val ignoredToday: Boolean = false,
    val warningSentToday: Boolean = false,
    val lastUpdatedDayId: Long = 0L,
    val addedAtMillis: Long = 0L
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(dailyLimitMinutes >= 0) { "dailyLimitMinutes must be >= 0" }
        require(usedMillisToday >= 0L) { "usedMillisToday must be >= 0" }
        require(extraMillisToday >= 0L) { "extraMillisToday must be >= 0" }
    }

    val baseLimitMillis: Long
        get() = dailyLimitMinutes * MILLIS_PER_MINUTE

    val effectiveLimitMillis: Long
        get() = baseLimitMillis + extraMillisToday

    val remainingMillis: Long
        get() = (effectiveLimitMillis - usedMillisToday).coerceAtLeast(0L)

    val status: AppLimitStatus
        get() = when {
            ignoredToday -> AppLimitStatus.IGNORED_TODAY
            effectiveLimitMillis == 0L || usedMillisToday >= effectiveLimitMillis -> AppLimitStatus.BLOCKED
            else -> AppLimitStatus.ACTIVE
        }

    val isBlocked: Boolean
        get() = status == AppLimitStatus.BLOCKED

    companion object {
        const val MILLIS_PER_MINUTE: Long = 60_000L
    }
}
