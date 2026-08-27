package com.openfog.online.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FogPolygonEntity::class,
        TrackEntity::class,
        AchievementEntity::class,
        UserLevelEntity::class,
        StatsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fogPolygonDao(): FogPolygonDao
    abstract fun trackDao(): TrackDao
    abstract fun achievementDao(): AchievementDao
    abstract fun userLevelDao(): UserLevelDao
    abstract fun statsDao(): StatsDao

    companion object {
        private const val NAME = "openfog.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
        }
    }
}
