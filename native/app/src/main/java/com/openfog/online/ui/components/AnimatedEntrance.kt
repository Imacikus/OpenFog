package com.openfog.online.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Subtle fade + rise-in entrance animation, applied the first time a node is
 * composed. Keep it gentle (spring-based, short) so it never feels playful.
 *
 * @param delayMs stagger delay in ms so multiple cards rise in sequence.
 */
@Composable
fun AnimatedEntrance(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) {
            delay(delayMs.toLong())
        }
        visible = true
    }
    val target by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "entrance"
    )
    Box(
        modifier
            .graphicsLayer { translationY = (1f - target) * 18f }
            .alpha(target)
    ) {
        content()
    }
}
