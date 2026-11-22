package com.lograre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lograre.app.data.local.entity.TrustedClockStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedClockDao {

    @Query("SELECT * FROM trusted_clock_state WHERE id = 1 LIMIT 1")
    suspend fun getState(): TrustedClockStateEntity?

    @Query("SELECT * FROM trusted_clock_state WHERE id = 1 LIMIT 1")
    fun observeState(): Flow<TrustedClockStateEntity?>

    @Upsert
    suspend fun saveState(state: TrustedClockStateEntity)
}
