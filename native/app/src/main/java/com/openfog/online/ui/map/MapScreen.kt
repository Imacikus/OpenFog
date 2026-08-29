package com.openfog.online.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openfog.online.data.AppJson
import com.openfog.online.ui.FabState
import com.openfog.online.ui.OpenFogViewModel

@Composable
fun MapScreen(viewModel: OpenFogViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { OsmMapController(context) }

    val fogColor = MaterialTheme.colorScheme.surface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val errorColor = MaterialTheme.colorScheme.error.toArgb()
    val fogColors = FogColors(
        fog = fogColor,
        track = primaryColor,
        live = errorColor,
        accent = primaryColor,
        border = Color.White.toArgb(),
    )
    LaunchedEffect(fogColors) { controller.setColors(fogColors) }

    val fog by viewModel.fog.collectAsStateWithLifecycle()
    val fabState by viewModel.fabState.collectAsStateWithLifecycle()
    val livePoints by viewModel.livePoints.collectAsStateWithLifecycle()
    val blueDot by viewModel.locateMePoint.collectAsStateWithLifecycle()
    val center by viewModel.centerRequest.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val tracksVisible by viewModel.tracksVisible.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        controller.setListener { w, s, e, n, z ->
            viewModel.onViewportChanged(w, s, e, n, z)
        }
        onDispose { controller.dispose() }
    }
    LaunchedEffect(fog) { if (fog != null) controller.setFog(fog) }
    LaunchedEffect(livePoints) { controller.setLivePolyline(livePoints) }
    LaunchedEffect(blueDot) { controller.setBlueDot(blueDot) }
    LaunchedEffect(center) { controller.applyCenterRequest(center) }
    LaunchedEffect(tracks, tracksVisible) {
        val polylines = tracks.map { tv ->
            AppJson.decodeTrack(tv.id, tv.name, tv.color, tv.width, tv.pointsJson, tv.metadataJson)
                .points.map { org.osmdroid.util.GeoPoint(it.lat, it.lng) }
        }
        controller.setTracks(polylines, tracksVisible)
    }

    Box(modifier = modifier.fillMaxSize()) {
        OsmMapHost(controller = controller, modifier = Modifier.fillMaxSize())

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            IconButton(onClick = { viewModel.locateMe() }) {
                Icon(Icons.Default.MyLocation, contentDescription = "Position")
            }
        }

        if (fabState == FabState.TRACKING) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = "%.2f km".format(com.openfog.online.importexport.TrackParser.calculateTrackDistanceKm(livePoints)),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        FloatingActionButton(
            onClick = { viewModel.toggleTracking() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-16).dp),
            containerColor = when (fabState) {
                FabState.TRACKING -> MaterialTheme.colorScheme.error
                FabState.SEARCHING -> MaterialTheme.colorScheme.tertiaryContainer
                FabState.IDLE -> MaterialTheme.colorScheme.primary
            },
            contentColor = when (fabState) {
                FabState.TRACKING -> MaterialTheme.colorScheme.onError
                else -> MaterialTheme.colorScheme.onPrimary
            }
        ) {
            when (fabState) {
                FabState.IDLE -> Text("Aufzeichnen", fontSize = 16.sp)
                FabState.SEARCHING -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                FabState.TRACKING -> Icon(Icons.Default.Stop, contentDescription = "Stopp")
            }
        }
    }
}
