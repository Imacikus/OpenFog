package com.openfog.online.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val pages = listOf(
    OnboardingPage(
        Icons.Default.Public,
        "Willkommen bei OpenFog",
        "Deine Weltkarte startet in Nebel gehüllt. Bewege dich mit einer aufgezeichneten Route, um den Nebel zu lüften und Bereiche der Welt sichtbar zu machen."
    ),
    OnboardingPage(
        Icons.Default.MyLocation,
        "Aufzeichnen",
        "Tippe unten auf den Button, um eine Route aufzuzeichnen. Starte und stoppe jederzeit — jede Bewegung lüftet den Nebel entlang deines Wegs."
    ),
    OnboardingPage(
        Icons.Default.FileUpload,
        "Tracks importieren",
        "Importiere vorhandene GPX-, KML- oder KMZ-Dateien in den Einstellungen, um deinem Nebel weitere Bereiche hinzuzufügen."
    ),
    OnboardingPage(
        Icons.Default.Save,
        "Sichern & Wiederherstellen",
        "Exportiere deine Daten als Backup-Datei oder stelle sie bei Bedarf wieder her — alles über die Einstellungen."
    ),
    OnboardingPage(
        Icons.Default.EmojiEvents,
        "Profil & Erfolge",
        "Verfolge deine enthüllte Fläche, deine Strecken und dein Level. Schalte Erfolge frei, während du die Welt erkundest."
    )
)

/**
 * Full-screen first-launch onboarding flow. Also re-openable from the settings.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.surfaceContainerLow, colors.surface)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            // Skip button.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text("Überspringen") }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page: Int ->
                val p = pages[page]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedIcon(p.icon)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        p.title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        p.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Page indicator dots.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                pages.indices.forEach { i ->
                    val selected = pagerState.currentPage == i
                    val width by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dot"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .width(width)
                            .height(8.dp)
                            .background(
                                if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.35f),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val isLast = pagerState.currentPage == pages.lastIndex
            Button(
                onClick = {
                    if (isLast) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isLast) "Los geht's" else "Weiter", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AnimatedIcon(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(120.dp),
        shape = RoundedCornerShape(32.dp),
        color = colors.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = colors.onPrimaryContainer
            )
        }
    }
}
