package com.openfog.online.importexport

import com.openfog.online.data.db.AppDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON backup export/import, shaped like the legacy web backup
 * (5 stores + _version=1) for round-trip and forward compatibility.
 */
class BackupManager(private val db: AppDatabase) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportJson(): String {
        val backup = BackupEnvelope(
            fogPolygons = db.fogPolygonDao().getAll().map {
                Record(it.id, it.geometryJson, it.areaKm2, it.createdAt)
            },
            tracks = db.trackDao().getAll().map {
                TrackRecord(it.id, it.name, it.color, it.width, it.pointsJson, it.metadataJson)
            },
            achievements = db.achievementDao().getAll().map {
                AchievementRecord(it.id, it.name, it.description, it.icon, it.conditionType, it.conditionTarget, it.unlocked, it.unlockedAt)
            },
            userLevel = db.userLevelDao().get()?.let {
                LevelRecord(it.id, it.currentLevel, it.xp, it.xpForNextLevel)
            },
            stats = db.statsDao().get()?.let {
                StatsRecord(it.id, it.totalRevealedArea, it.totalRevealedPercent, it.totalDistance, it.trackCount, it.lastUpdated)
            },
            _version = 1
        )
        return json.encodeToString(backup)
    }

    suspend fun importJson(content: String) {
        val backup = json.decodeFromString<BackupEnvelope>(content)
        if (backup._version != 1) error("Keine gültige Backup-Datei")
        backup.fogPolygons?.forEach {
            db.fogPolygonDao().insertAll(
                listOf(com.openfog.online.data.db.FogPolygonEntity(it.id, it.geometry, it.area, "track", it.createdAt))
            )
        }
        backup.tracks?.forEach {
            db.trackDao().insert(
                com.openfog.online.data.db.TrackEntity(it.id, it.name, it.color, it.width, it.points, it.metadata)
            )
        }
        backup.achievements?.forEach {
            db.achievementDao().upsert(
                com.openfog.online.data.db.AchievementEntity(it.id, it.name, it.description, it.icon, it.conditionType, it.conditionTarget, it.unlocked, it.unlockedAt)
            )
        }
        backup.userLevel?.let {
            db.userLevelDao().put(
                com.openfog.online.data.db.UserLevelEntity(it.id, it.currentLevel, it.xp, it.xpForNextLevel)
            )
        }
        backup.stats?.let {
            db.statsDao().put(
                com.openfog.online.data.db.StatsEntity(it.id, it.totalRevealedArea, it.totalRevealedPercent, it.totalDistance, it.trackCount, it.lastUpdated)
            )
        }
    }
}

@Serializable
private data class BackupEnvelope(
    val fogPolygons: List<Record>? = null,
    val tracks: List<TrackRecord>? = null,
    val achievements: List<AchievementRecord>? = null,
    val userLevel: LevelRecord? = null,
    val stats: StatsRecord? = null,
    val _version: Int
)

@Serializable
private data class Record(val id: String, val geometry: String, val area: Double, val createdAt: Long)

@Serializable
private data class TrackRecord(val id: String, val name: String, val color: String, val width: Int, val points: String, val metadata: String)

@Serializable
private data class AchievementRecord(
    val id: String, val name: String, val description: String, val icon: String,
    val conditionType: String, val conditionTarget: Double, val unlocked: Boolean, val unlockedAt: Long?
)

@Serializable
private data class LevelRecord(val id: String, val currentLevel: Int, val xp: Double, val xpForNextLevel: Double)

@Serializable
private data class StatsRecord(
    val id: String, val totalRevealedArea: Double, val totalRevealedPercent: Double,
    val totalDistance: Double, val trackCount: Int, val lastUpdated: Long
)
