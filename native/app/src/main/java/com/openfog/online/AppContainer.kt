package com.openfog.online

import android.content.Context
import com.openfog.online.achievements.ProgressService
import com.openfog.online.data.db.AppDatabase
import com.openfog.online.data.repo.FogRepository
import com.openfog.online.data.repo.TrackRepository
import com.openfog.online.geo.FogOverlayEngine
import com.openfog.online.gps.LocationProvider
import com.openfog.online.importexport.BackupManager

/** Simple manual dependency container. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val fogEngine = FogOverlayEngine()
    val fogRepository = FogRepository(database)
    val trackRepository = TrackRepository(database)
    val progressService = ProgressService(database, trackRepository, fogEngine)
    val locationProvider = LocationProvider(context)
    val backupManager = BackupManager(database)
}
