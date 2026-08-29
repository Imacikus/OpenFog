package com.openfog.online.ui.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.graphicsLayer
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

    val fogColor = Color(
        red = 0.05f, green = 0.05f, blue = 0.08f, alpha = 0.80f
    ).toArgb()
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

        AnimatedVisibility(
            visible = fabState == FabState.TRACKING,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 },
        ) {
            Surface(
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

        TrackingFab(
            fabState = fabState,
            onClick = { viewModel.toggleTracking() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-16).dp)
        )
    }
}

@Composable
private fun BoxScope.TrackingFab(
    fabState: FabState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (fabState) {
        FabState.TRACKING -> MaterialTheme.colorScheme.error
        FabState.SEARCHING -> MaterialTheme.colorScheme.tertiaryContainer
        FabState.IDLE -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when (fabState) {
        FabState.TRACKING -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }

    val infinite = rememberInfiniteTransition(label = "trackingPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "halo"
    )

    Box(modifier = modifier) {
        if (fabState == FabState.TRACKING) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = (0.35f - (pulse - 0.7f)) * 0.7f
                    }
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.45f), CircleShape)
            )
        }
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = CircleShape
        ) {
            AnimatedContent(
                targetState = fabState,
                transitionSpec = {
                    fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 } togetherWith
                        fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 2 }
                },
                label = "fabContent"
            ) { state ->
                when (state) {
                    FabState.IDLE -> Text("Aufzeichnen", fontSize = 16.sp)
                    FabState.SEARCHING ->
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = contentColor)
                    FabState.TRACKING -> Icon(Icons.Default.Stop, contentDescription = "Stopp")
                }
            }
        }
    }
}
