package com.openfog.online.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fog_polygons")
data class FogPolygonEntity(
    @PrimaryKey val id: String,
    val geometryJson: String,
    val areaKm2: Double,
    val source: String = "track",
    val createdAt: Long
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val width: Int = 3,
    val pointsJson: String,
    val metadataJson: String
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val conditionType: String,
    val conditionTarget: Double,
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null
)

@Entity(tableName = "user_level")
data class UserLevelEntity(
    @PrimaryKey val id: String = "main",
    val currentLevel: Int,
    val xp: Double,
    val xpForNextLevel: Double
)

@Entity(tableName = "stats")
data class StatsEntity(
    @PrimaryKey val id: String = "main",
    val totalRevealedArea: Double,
    val totalRevealedPercent: Double,
    val totalDistance: Double,
    val trackCount: Int,
    val lastUpdated: Long
)
