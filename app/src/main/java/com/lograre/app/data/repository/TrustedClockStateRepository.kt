package com.lograre.app.data.repository

import com.lograre.app.data.local.dao.TrustedClockDao
import com.lograre.app.data.local.entity.toDomain
import com.lograre.app.data.local.entity.toEntity
import com.lograre.app.domain.model.TrustedClockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrustedClockStateRepository(
    private val trustedClockDao: TrustedClockDao
) {
    suspend fun getState(): TrustedClockState? =
        trustedClockDao.getState()?.toDomain()

    fun observeState(): Flow<TrustedClockState?> =
        trustedClockDao.observeState().map { it?.toDomain() }

    suspend fun saveState(state: TrustedClockState) {
        trustedClockDao.saveState(state.toEntity())
    }
}
