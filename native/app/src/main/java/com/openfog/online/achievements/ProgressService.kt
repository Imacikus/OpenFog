package com.openfog.online.achievements

import com.openfog.online.data.db.AchievementEntity
import com.openfog.online.data.db.AppDatabase
import com.openfog.online.data.db.StatsEntity
import com.openfog.online.data.db.UserLevelEntity
import com.openfog.online.data.repo.TrackRepository
import com.openfog.online.geo.FogOverlayEngine
import com.openfog.online.model.OpenFogConstants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.locationtech.jts.geom.Geometry

/**
 * Updates stats / level / achievements. All writes are serialized through a
 * mutex (mirrors the legacy promise-chain mutex) so concurrent track merges and
 * live stops never double-count.
 */
class ProgressService(
    private val db: AppDatabase,
    private val trackRepo: TrackRepository,
    private val engine: FogOverlayEngine
) {
    private val mutex = Mutex()

    private val statsDao = db.statsDao()
    private val levelDao = db.userLevelDao()
    private val achievementDao = db.achievementDao()

    /** Initialize seed records if missing (idempotent). */
    suspend fun seedIfNeeded() = mutex.withLock {
        if (levelDao.get() == null) {
            levelDao.put(UserLevelEntity("main", 1, 0.0, OpenFogConstants.XP_PER_LEVEL))
        }
        if (statsDao.get() == null) {
            putStats(StatsEntity("main", 0.0, 0.0, 0.0, 0, System.currentTimeMillis()))
        }
        val existing = achievementDao.getAll().map { it.id }.toSet()
        val toSeed = DefaultAchievements.all().filter { it.id !in existing }
            .map { a -> AchievementEntity(a.id, a.name, a.description, a.icon, a.condition.type, a.condition.target, false, null) }
        if (toSeed.isNotEmpty()) achievementDao.insertIgnore(toSeed)
    }

    /**
     * Add newly revealed area (km2) and distance (km). Updates stats, then
     * re-derives level XP and checks achievements.
     */
    suspend fun addProgress(newAreaKm2: Double, newDistanceKm: Double) {
        if (newAreaKm2 <= 0.0 && newDistanceKm <= 0.0) return
        mutex.withLock {
            val stats = statsDao.get() ?: StatsEntity("main", 0.0, 0.0, 0.0, 0, 0)
            val newTotal = stats.totalRevealedArea + newAreaKm2
            val newDistance = stats.totalDistance + newDistanceKm
            val newPercent = newTotal / OpenFogConstants.WORLD_TOTAL_AREA * 100.0
            putStats(stats.copy(
                totalRevealedArea = newTotal,
                totalRevealedPercent = newPercent,
                totalDistance = newDistance,
                lastUpdated = System.currentTimeMillis()
            ))

            updateLevel(newAreaKm2)
            checkAchievements()
        }
    }

    suspend fun updateTrackCount() = mutex.withLock {
        val stats = statsDao.get() ?: return@withLock
        val imported = trackRepo.importedCount()
        putStats(stats.copy(trackCount = imported, lastUpdated = System.currentTimeMillis()))
    }

    suspend fun currentStats(): StatsEntity? = statsDao.get()
    suspend fun currentLevel(): UserLevelEntity? = levelDao.get()
    suspend fun achievements(): List<AchievementEntity> = achievementDao.getAll()

    private suspend fun updateLevel(newAreaKm2: Double) {
        val lvl = levelDao.get() ?: return
        val newXp = lvl.xp + newAreaKm2 * OpenFogConstants.XP_PER_KM2
        val newLevel = Math.floor(newXp / OpenFogConstants.XP_PER_LEVEL).toInt() + 1
        levelDao.put(
            lvl.copy(
                xp = newXp,
                currentLevel = newLevel,
                xpForNextLevel = newLevel * OpenFogConstants.XP_PER_LEVEL
            )
        )
    }

    private suspend fun checkAchievements() {
        val stats = statsDao.get() ?: return
        val lvl = levelDao.get() ?: return
        val achievements = achievementDao.getAll().filter { !it.unlocked }
        val importedCount = trackRepo.importedCount()

        for (a in achievements) {
            val satisfied = when (a.conditionType) {
                "area" -> stats.totalRevealedArea >= a.conditionTarget
                "percent" -> stats.totalRevealedPercent >= a.conditionTarget
                "distance" -> stats.totalDistance >= a.conditionTarget
                "level" -> lvl.currentLevel.toDouble() >= a.conditionTarget
                "trackCount" -> importedCount.toDouble() >= a.conditionTarget
                else -> false
            }
            if (satisfied) {
                achievementDao.setUnlocked(a.id, true, System.currentTimeMillis())
            }
        }
    }

    /** Union area of newly revealed shapes, matching legacy on-stop computation. */
    fun unionAreaKm2(shapes: List<Geometry>): Double {
        val union = engine.buildRevealedUnion(shapes) ?: return 0.0
        return engine.areaKm2(union)
    }

    private suspend fun putStats(s: StatsEntity) = statsDao.put(s)
}
