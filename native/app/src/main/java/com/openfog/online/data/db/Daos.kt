package com.openfog.online.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FogPolygonDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(polygons: List<FogPolygonEntity>)

    @Query("SELECT * FROM fog_polygons")
    fun observeAll(): Flow<List<FogPolygonEntity>>

    @Query("SELECT * FROM fog_polygons")
    suspend fun getAll(): List<FogPolygonEntity>

    @Query("SELECT COUNT(*) FROM fog_polygons")
    suspend fun count(): Int

    @Query("DELETE FROM fog_polygons")
    suspend fun clear()
}

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(track: TrackEntity)

    @Query("SELECT * FROM tracks ORDER BY metadataJson ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("DELETE FROM tracks")
    suspend fun clear()
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(achievement: AchievementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(achievements: List<AchievementEntity>)

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getById(id: String): AchievementEntity?

    @Query("UPDATE achievements SET unlocked = :unlocked, unlockedAt = :unlockedAt WHERE id = :id")
    suspend fun setUnlocked(id: String, unlocked: Boolean, unlockedAt: Long?)

    @Query("DELETE FROM achievements")
    suspend fun clear()
}

@Dao
interface UserLevelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(level: UserLevelEntity)

    @Query("SELECT * FROM user_level WHERE id = 'main'")
    suspend fun get(): UserLevelEntity?

    @Query("SELECT * FROM user_level WHERE id = 'main'")
    fun observe(): Flow<UserLevelEntity?>

    @Query("DELETE FROM user_level")
    suspend fun clear()
}

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(stats: StatsEntity)

    @Query("SELECT * FROM stats WHERE id = 'main'")
    suspend fun get(): StatsEntity?

    @Query("SELECT * FROM stats WHERE id = 'main'")
    fun observe(): Flow<StatsEntity?>

    @Query("DELETE FROM stats")
    suspend fun clear()
}
