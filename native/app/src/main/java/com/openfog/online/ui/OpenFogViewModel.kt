package com.openfog.online.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openfog.online.AppContainer
import com.openfog.online.data.AppJson
import com.openfog.online.data.db.AchievementEntity
import com.openfog.online.data.db.StatsEntity
import com.openfog.online.data.db.TrackEntity
import com.openfog.online.data.db.UserLevelEntity
import com.openfog.online.geo.FogState
import com.openfog.online.geo.RevealShapeBuilder
import com.openfog.online.geo.RevealShapeBuilder.LatLng
import com.openfog.online.importexport.TrackParser
import com.openfog.online.model.GpsPoint
import com.openfog.online.model.OpenFogConstants
import com.openfog.online.model.Track
import com.openfog.online.model.TrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Geometry
import java.util.UUID

enum class FabState { IDLE, SEARCHING, TRACKING }

data class FogFrame(val geometry: Geometry, val revision: Long)

data class CenterRequest(val lat: Double?, val lng: Double?, val zoom: Double) {
    companion object {
        val None = CenterRequest(null, null, OpenFogConstants.DEFAULT_ZOOM)
    }
}

class OpenFogViewModel(private val container: AppContainer) : ViewModel() {

    private val fogState = FogState(container.fogEngine)
    private var fogRevision = 0L
    private var lastViewport: DoubleArray? = null

    // ---- Map / fog ----
    private val _fog = MutableStateFlow<FogFrame?>(null)
    val fog: StateFlow<FogFrame?> = _fog.asStateFlow()

    private val _centerRequest = MutableStateFlow(CenterRequest.None)
    val centerRequest: StateFlow<CenterRequest> = _centerRequest.asStateFlow()

    // ---- Live tracking ----
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    private val _fabState = MutableStateFlow(FabState.IDLE)
    val fabState: StateFlow<FabState> = _fabState.asStateFlow()
    private val _livePoints = MutableStateFlow<List<GpsPoint>>(emptyList())
    val livePoints: StateFlow<List<GpsPoint>> = _livePoints.asStateFlow()
    private val _liveDistance = MutableStateFlow(0.0)
    val liveDistance: StateFlow<Double> = _liveDistance.asStateFlow()
    private var trackingJob: Job? = null

    // ---- Blue dot ----
    private val _locateMePoint = MutableStateFlow<GpsPoint?>(null)
    val locateMePoint: StateFlow<GpsPoint?> = _locateMePoint.asStateFlow()
    private var locateJob: Job? = null

    // ---- Stats / level / achievements / tracks ----
    private val _stats = MutableStateFlow<StatsEntity?>(null)
    val stats: StateFlow<StatsEntity?> = _stats.asStateFlow()
    private val _level = MutableStateFlow<UserLevelEntity?>(null)
    val level: StateFlow<UserLevelEntity?> = _level.asStateFlow()
    private val _achievements = MutableStateFlow<List<AchievementEntity>>(emptyList())
    val achievements: StateFlow<List<AchievementEntity>> = _achievements.asStateFlow()
    private val _tracksVisible = MutableStateFlow(false)
    val tracksVisible: StateFlow<Boolean> = _tracksVisible.asStateFlow()
    private val _tracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val tracks: StateFlow<List<TrackEntity>> = _tracks.asStateFlow()
    private val _importProgress = MutableStateFlow<Pair<Int, String>?>(null)
    val importProgress: StateFlow<Pair<Int, String>?> = _importProgress.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ---- Onboarding / preferences ----
    private val _showOnboarding = MutableStateFlow<Boolean?>(null)
    val showOnboarding: StateFlow<Boolean?> = _showOnboarding.asStateFlow()
    private val _followDuringTracking =
        MutableStateFlow(container.prefs.followDuringTracking)
    val followDuringTracking: StateFlow<Boolean> = _followDuringTracking.asStateFlow()

    init {
        _showOnboarding.value = !container.prefs.onboardingShown
        viewModelScope.launch {
            container.progressService.seedIfNeeded()
            refreshStats()
            val revealed = container.fogRepository.getAllGeometries()
            withContext(Dispatchers.Default) { fogState.loadFrom(revealed) }
        }
        viewModelScope.launch { refreshTracks() }
    }

    // ================= VIEWPORT =================
    fun onViewportChanged(west: Double, south: Double, east: Double, north: Double, zoom: Double) {
        lastViewport = doubleArrayOf(west, south, east, north, zoom)
        viewModelScope.launch(Dispatchers.Default) {
            val geometry = fogState.computeFog(west, south, east, north, zoom)
            _fog.value = FogFrame(geometry, ++fogRevision)
        }
    }

    // ================= TRACKING =================
    fun toggleTracking() {
        if (_isTracking.value) stopTracking() else startTracking()
    }

    fun startTracking() {
        if (_isTracking.value) return
        _isTracking.value = true
        _fabState.value = FabState.SEARCHING
        _livePoints.value = emptyList()
        _liveDistance.value = 0.0
        trackingJob = viewModelScope.launch {
            // Seed with the last known location so tracking starts instantly and
            // the map centers, even during a GPS cold start / indoors. Fresh
            // fixes then refine the position.
            var hasStarted = false
            container.locationProvider.lastKnown()?.let {
                onTrackingFix(it, true)
                hasStarted = true
            }
            container.locationProvider.watch(OpenFogConstants.TRACKING_INTERVAL_MS)
                .catch { onTrackingError(it) }
                .collect { fix ->
                    onTrackingFix(fix, !hasStarted)
                    hasStarted = true
                }
        }
    }

    private suspend fun onTrackingFix(fix: GpsPoint, first: Boolean) {
        val prevLatLng = _livePoints.value.lastOrNull()?.let { LatLng(it.lat, it.lng) }
        val curLatLng = LatLng(fix.lat, fix.lng)

        val points = _livePoints.value + fix
        _livePoints.value = points
        _liveDistance.value = TrackParser.calculateTrackDistanceKm(points)

        val shape = RevealShapeBuilder.buildRevealPolygon(prevLatLng, curLatLng)
        val areaKm2 = container.fogEngine.areaKm2(shape)
        container.fogRepository.saveRevealShape(shape, areaKm2)

        // Apply the reveal shape to the in-memory union immediately (not via a
        // cancellable throttle that drops it), then refresh the visible fog so
        // the haze clears along the track in real time.
        withContext(Dispatchers.Default) {
            fogState.addShapes(listOf(shape))
            lastViewport?.let { b ->
                val geometry = fogState.computeFog(b[0], b[1], b[2], b[3], b[4])
                _fog.value = FogFrame(geometry, ++fogRevision)
            }
        }

        if (first) {
            _fabState.value = FabState.TRACKING
            _centerRequest.value = CenterRequest(fix.lat, fix.lng, 16.0)
            _message.value = "Tracking aktiv"
        } else if (_followDuringTracking.value) {
            // Follow the user so revealed areas scroll into view while tracking.
            val zoom = lastViewport?.get(4) ?: 16.0
            _centerRequest.value = CenterRequest(fix.lat, fix.lng, zoom)
        }
    }

    private fun onTrackingError(t: Throwable) {
        if (t.message.orEmpty().contains("denied", ignoreCase = true)) {
            _message.value = "GPS-Berechtigung verweigert"
            stopTracking()
        } else {
            _message.value = "GPS nicht verfügbar"
        }
    }

    fun stopTracking() {
        _isTracking.value = false
        _fabState.value = FabState.IDLE
        trackingJob?.cancel()
        trackingJob = null

        val points = _livePoints.value
        if (points.isNotEmpty()) {
            viewModelScope.launch {
                val track = Track(
                    id = UUID.randomUUID().toString(),
                    name = "Track ${System.currentTimeMillis()}",
                    points = points,
                    color = listOf("#3498db", "#e74c3c", "#2ecc71", "#f39c12", "#9b59b6", "#1abc9c").random(),
                    width = 3,
                    metadata = TrackMetadata(
                        points.first().timestamp,
                        points.last().timestamp,
                        _liveDistance.value * 1000.0,
                        "live"
                    )
                )
                container.trackRepository.insert(track)
                container.progressService.updateTrackCount()
                refreshTracks()
            }
        }
        _livePoints.value = emptyList()
        _liveDistance.value = 0.0
    }

    // ================= LOCATE ME =================
    fun locateMe() {
        viewModelScope.launch {
            val current = _locateMePoint.value
            if (current != null) {
                _centerRequest.value = CenterRequest(current.lat, current.lng, 16.0)
                return@launch
            }
            val fix = container.locationProvider.getCurrentLocation(15000)
            if (fix == null) {
                _message.value = "GPS nicht verfügbar"
                return@launch
            }
            _locateMePoint.value = fix
            _centerRequest.value = CenterRequest(fix.lat, fix.lng, 16.0)
            locateJob?.cancel()
            locateJob = viewModelScope.launch {
                container.locationProvider.watch(OpenFogConstants.LOCATE_ME_INTERVAL_MS)
                    .catch { _message.value = "GPS nicht verfügbar" }
                    .collect { _locateMePoint.value = it }
            }
        }
    }

    // ================= TRACKS TOGGLE =================
    fun toggleTracks() {
        _tracksVisible.value = !_tracksVisible.value
    }

    // ================= IMPORT =================
    fun importFile(name: String, readBytes: suspend () -> ByteArray) {
        viewModelScope.launch {
            _importProgress.value = 5 to "Datei wird gelesen…"
            val bytes = withContext(Dispatchers.IO) { readBytes() }
            _importProgress.value = 10 to "Track wird gespeichert…"
            val track = withContext(Dispatchers.IO) { TrackParser.parse(bytes, name) }
            container.trackRepository.insert(track)

            val shapes = ArrayList<Geometry>()
            var prev: LatLng? = null
            withContext(Dispatchers.Default) {
                track.points.forEachIndexed { i, pt ->
                    val cur = LatLng(pt.lat, pt.lng)
                    shapes.add(RevealShapeBuilder.buildRevealPolygon(prev, cur))
                    prev = cur
                    val pct = 5 + ((i.toDouble() / track.points.size) * 85).toInt()
                    _importProgress.value = pct.coerceAtMost(93) to "Nebel wird angewendet…"
                }
            }

            // Persist and reveal in batches, then add progress once.
            shapes.chunked(50).forEach { chunk ->
                chunk.forEach { container.fogRepository.saveRevealShape(it, container.fogEngine.areaKm2(it)) }
            }
            fogState.addShapes(shapes)
            val unionArea = container.fogEngine.areaKm2(
                container.fogEngine.buildRevealedUnion(shapes) ?: throw IllegalStateException("no shapes")
            )
            container.progressService.addProgress(unionArea, track.metadata.distanceMeters / 1000.0)
            container.progressService.updateTrackCount()
            refreshStats()
            refreshTracks()

            _importProgress.value = null
            _message.value = "Track importiert: ${track.name}"
        }
    }

    // ================= BACKUP =================
    suspend fun buildBackupJson(): String = container.backupManager.exportJson()

    fun importBackup(content: String) {
        viewModelScope.launch {
            try {
                container.backupManager.importJson(content)
                withContext(Dispatchers.Default) {
                    val revealed = container.fogRepository.getAllGeometries()
                    fogState.clear()
                    fogState.loadFrom(revealed)
                }
                refreshStats()
                refreshTracks()
                _message.value = "Backup wiederhergestellt"
            } catch (e: Exception) {
                _message.value = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    // ================= RESETS =================
    fun resetAllData() {
        viewModelScope.launch {
            container.database.clearAllTables()
            container.progressService.seedIfNeeded()
            withContext(Dispatchers.Default) {
                fogState.clear()
                fogState.loadFrom(emptyList())
            }
            refreshStats()
            refreshTracks()
            _livePoints.value = emptyList()
            _message.value = "Alle Daten zurückgesetzt"
        }
    }

    fun resetFogOnly() {
        viewModelScope.launch {
            container.fogRepository.clear()
            withContext(Dispatchers.Default) { fogState.clear() }
            _message.value = "Nebel zurückgesetzt"
        }
    }

    fun resetTracksAndStats() {
        viewModelScope.launch {
            container.trackRepository.clear()
            container.database.statsDao().clear()
            container.database.userLevelDao().clear()
            container.database.achievementDao().clear()
            container.progressService.seedIfNeeded()
            refreshStats()
            refreshTracks()
            _message.value = "Tracks + Statistik zurückgesetzt"
        }
    }

    // ================= INTERNALS =================
    private suspend fun refreshStats() {
        _stats.value = container.progressService.currentStats()
        _level.value = container.progressService.currentLevel()
        _achievements.value = container.progressService.achievements().sortedByDescending { it.unlocked }
    }

    private suspend fun refreshTracks() {
        _tracks.value = container.trackRepository.getAll().map { trackEntity(it) }
    }

    private fun trackEntity(t: Track): TrackEntity {
        val (points, meta) = AppJson.encodeTrack(t)
        return TrackEntity(t.id, t.name, t.color, t.width, points, meta)
    }

    fun consumeMessage(): String? = _message.value

    // ================= ONBOARDING / PREFERENCES =================
    fun dismissOnboarding() {
        if (_showOnboarding.value == true) {
            container.prefs.onboardingShown = true
        }
        _showOnboarding.value = false
    }

    fun openOnboarding() {
        _showOnboarding.value = true
    }

    fun setFollowDuringTracking(enabled: Boolean) {
        container.prefs.followDuringTracking = enabled
        _followDuringTracking.value = enabled
    }
}
