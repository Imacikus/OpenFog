package com.openfog.online.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openfog.online.ui.map.MapScreen
import com.openfog.online.ui.onboarding.OnboardingScreen
import com.openfog.online.ui.profile.ProfileScreen
import com.openfog.online.ui.settings.SettingsScreen

private enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    MAP("map", "Karte", Icons.Default.Map),
    PROFILE("profile", "Profil", Icons.Default.Person),
    SETTINGS("settings", "Einstellungen", Icons.Default.Settings)
}

private val tabOrder = listOf(BottomTab.MAP, BottomTab.PROFILE, BottomTab.SETTINGS)

@Composable
fun OpenFogApp(viewModel: OpenFogViewModel) {
    var selectedTab by remember { mutableStateOf(BottomTab.MAP) }
    val fabState by viewModel.fabState.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()

    if (showOnboarding == true) {
        OnboardingScreen(onFinish = viewModel::dismissOnboarding)
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabOrder.forEachIndexed { index, tab ->
                    val selected = selectedTab == tab
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabIcon"
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (tabOrder.indexOf(targetState) > tabOrder.indexOf(initialState)) 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { direction * it / 12 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -direction * it / 12 })
            },
            modifier = Modifier.padding(padding),
            label = "tabContent"
        ) { tab ->
            when (tab) {
                BottomTab.MAP -> MapScreen(viewModel)
                BottomTab.PROFILE -> ProfileScreen(viewModel)
                BottomTab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}
