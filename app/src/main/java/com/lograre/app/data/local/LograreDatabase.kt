package com.lograre.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lograre.app.data.local.dao.TrackedAppDao
import com.lograre.app.data.local.dao.TrustedClockDao
import com.lograre.app.data.local.entity.TrackedAppEntity
import com.lograre.app.data.local.entity.TrustedClockStateEntity

@Database(
    entities = [
        TrackedAppEntity::class,
        TrustedClockStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LograreDatabase : RoomDatabase() {

    abstract fun trackedAppDao(): TrackedAppDao

    abstract fun trustedClockDao(): TrustedClockDao

    companion object {
        private const val DATABASE_NAME = "lograre.db"

        fun create(context: Context): LograreDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LograreDatabase::class.java,
                DATABASE_NAME
            ).build()
    }
}
