package com.openfog.online.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.openfog.online.ui.map.MapScreen
import com.openfog.online.ui.profile.ProfileScreen
import com.openfog.online.ui.settings.SettingsScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    MAP("map", "Karte", Icons.Default.Map),
    PROFILE("profile", "Profil", Icons.Default.Person),
    SETTINGS("settings", "Einstellungen", Icons.Default.Settings)
}

@Composable
fun OpenFogApp(viewModel: OpenFogViewModel) {
    var selectedTab by remember { mutableStateOf(BottomTab.MAP) }
    val fabState by viewModel.fabState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            BottomTab.MAP -> MapScreen(viewModel, Modifier.padding(padding))
            BottomTab.PROFILE -> ProfileScreen(viewModel, Modifier.padding(padding))
            BottomTab.SETTINGS -> SettingsScreen(viewModel, Modifier.padding(padding))
        }
    }
}
