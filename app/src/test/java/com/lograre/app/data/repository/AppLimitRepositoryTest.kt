package com.lograre.app.data.repository

import com.lograre.app.data.local.dao.TrackedAppDao
import com.lograre.app.data.local.entity.TrackedAppEntity
import com.lograre.app.domain.model.AppLimitStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLimitRepositoryTest {

    private class FakeTrackedAppDao : TrackedAppDao {
        private val items = MutableStateFlow<Map<String, TrackedAppEntity>>(emptyMap())

        override fun observeAll(): Flow<List<TrackedAppEntity>> =
            items.map { map -> map.values.sortedWith(compareBy({ it.addedAtMillis }, { it.packageName })) }

        override suspend fun getAll(): List<TrackedAppEntity> =
            items.value.values.sortedWith(compareBy({ it.addedAtMillis }, { it.packageName }))

        override suspend fun getByPackageName(packageName: String): TrackedAppEntity? =
            items.value[packageName]

        override fun observeByPackageName(packageName: String): Flow<TrackedAppEntity?> =
            items.map { it[packageName] }

        override suspend fun upsert(entity: TrackedAppEntity) {
            items.value = items.value + (entity.packageName to entity)
        }

        override suspend fun deleteByPackageName(packageName: String) {
            items.value = items.value - packageName
        }

        override suspend fun updateDailyLimit(packageName: String, dailyLimitMinutes: Int) {
            val current = items.value[packageName] ?: return
            items.value = items.value + (packageName to current.copy(dailyLimitMinutes = dailyLimitMinutes))
        }

        override suspend fun addUsageMillis(packageName: String, deltaMillis: Long, dayId: Long) {
            val current = items.value[packageName] ?: return
            items.value = items.value + (
                packageName to current.copy(
                    usedMillisToday = current.usedMillisToday + deltaMillis,
                    lastUpdatedDayId = dayId
                )
            )
        }

        override suspend fun addExtraMillis(packageName: String, extraMillis: Long, dayId: Long) {
            val current = items.value[packageName] ?: return
            items.value = items.value + (
                packageName to current.copy(
                    extraMillisToday = current.extraMillisToday + extraMillis,
                    lastUpdatedDayId = dayId
                )
            )
        }

        override suspend fun setIgnoredToday(packageName: String, ignored: Boolean, dayId: Long) {
            val current = items.value[packageName] ?: return
            items.value = items.value + (
                packageName to current.copy(
                    ignoredToday = ignored,
                    lastUpdatedDayId = dayId
                )
            )
        }

        override suspend fun setWarningSentToday(packageName: String, sent: Boolean, dayId: Long) {
            val current = items.value[packageName] ?: return
            items.value = items.value + (
                packageName to current.copy(
                    warningSentToday = sent,
                    lastUpdatedDayId = dayId
                )
            )
        }

        override suspend fun resetAllForNewDay(newDayId: Long) {
            items.value = items.value.mapValues { (_, entity) ->
                entity.copy(
                    usedMillisToday = 0L,
                    extraMillisToday = 0L,
                    ignoredToday = false,
                    warningSentToday = false,
                    lastUpdatedDayId = newDayId
                )
            }
        }
    }

    @Test
    fun `updating daily limit preserves usage accumulated today`() = runTest {
        val repository = AppLimitRepository(FakeTrackedAppDao())
        val pkg = "com.instagram.android"

        repository.addOrUpdateApp(pkg, dailyLimitMinutes = 20, currentDayId = 100L, nowMillis = 1_000L)
        repository.addUsageMillis(pkg, deltaMillis = 5 * 60_000L, dayId = 100L)
        repository.addOrUpdateApp(pkg, dailyLimitMinutes = 30, currentDayId = 100L, nowMillis = 2_000L)

        val updated = repository.getApp(pkg)!!
        assertEquals(30, updated.dailyLimitMinutes)
        assertEquals(5 * 60_000L, updated.usedMillisToday)
        assertEquals(25 * 60_000L, updated.remainingMillis)
    }

    @Test
    fun `extra time, ignore today, and daily reset update state properly`() = runTest {
        val repository = AppLimitRepository(FakeTrackedAppDao())
        val pkg = "com.google.android.youtube"

        repository.addOrUpdateApp(pkg, dailyLimitMinutes = 10, currentDayId = 100L, nowMillis = 1_000L)
        repository.addUsageMillis(pkg, deltaMillis = 10 * 60_000L, dayId = 100L)
        assertTrue(repository.getApp(pkg)!!.isBlocked)

        repository.addExtraMinutes(pkg, extraMinutes = 5, dayId = 100L)
        val afterExtra = repository.getApp(pkg)!!
        assertFalse(afterExtra.isBlocked)
        assertEquals(5 * 60_000L, afterExtra.remainingMillis)

        repository.ignoreForToday(pkg, dayId = 100L)
        repository.markWarningSent(pkg, dayId = 100L)
        assertEquals(AppLimitStatus.IGNORED_TODAY, repository.getApp(pkg)!!.status)

        repository.resetAllForNewDay(newDayId = 101L)
        val afterReset = repository.getApp(pkg)!!
        assertEquals(0L, afterReset.usedMillisToday)
        assertEquals(0L, afterReset.extraMillisToday)
        assertFalse(afterReset.ignoredToday)
        assertFalse(afterReset.warningSentToday)
        assertEquals(101L, afterReset.lastUpdatedDayId)
        assertEquals(AppLimitStatus.ACTIVE, afterReset.status)

        repository.removeApp(pkg)
        assertNull(repository.getApp(pkg))
    }
}
