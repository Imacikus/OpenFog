package com.openfog.online.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle

/** Seed color for the MD3 scheme (matches the legacy web app #6c63ff). */
const val SEED_COLOR: Long = 0xFF6C63FF

/**
 * Material 3 dark theme derived from the seed color (same as the legacy web
 * app's `SchemeTonalSpot(source, isDark=true)`). Dark-only.
 */
@Composable
fun OpenFogTheme(content: @Composable () -> Unit) {
    DynamicMaterialTheme(
        seedColor = Color(SEED_COLOR),
        useDarkTheme = true,
        style = PaletteStyle.TonalSpot,
        typography = Typography(),
        content = content
    )
}
