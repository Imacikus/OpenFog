package com.openfog.online.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openfog.online.data.db.AchievementEntity
import com.openfog.online.model.OpenFogConstants
import com.openfog.online.ui.OpenFogViewModel

@Composable
fun ProfileScreen(viewModel: OpenFogViewModel, modifier: Modifier = Modifier) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Profil", style = MaterialTheme.typography.headlineSmall)
        val s = stats
        val l = level
        StatCard("Enthüllte Fläche", "${s?.totalRevealedArea?.let { "%.2f km²".format(it) } ?: "0.00 km²"}", Icons.Default.Map)
        StatCard("der Welt", "${s?.totalRevealedPercent?.let { "%.6f %".format(it) } ?: "0.000000 %"}", Icons.Default.Map)
        StatCard("Strecke", "${s?.totalDistance?.let { "%.2f km".format(it) } ?: "0.00 km"}", Icons.AutoMirrored.Filled.TrendingUp)
        StatCard("Tracks", "${s?.trackCount ?: 0}", Icons.Default.Route)

        // Level + XP bar.
        if (l != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Level ${l.currentLevel}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${l.xp.toInt()} XP", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    val xpInLevel = l.xp % OpenFogConstants.XP_PER_LEVEL
                    val pct = (xpInLevel / OpenFogConstants.XP_PER_LEVEL * 100).toFloat()
                    LinearProgressIndicator(progress = pct, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${xpInLevel.toInt()} / ${OpenFogConstants.XP_PER_LEVEL.toInt()} XP bis Level ${l.currentLevel + 1}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Achievements.
        Text("Erfolge", style = MaterialTheme.typography.titleMedium)
        achievements.forEach { a ->
            AchievementCard(a)
        }
        if (achievements.isEmpty()) {
            Text("Noch keine Erfolge.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AchievementCard(a: AchievementEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = if (a.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    a.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!a.unlocked) {
                    Text("Gesperrt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
