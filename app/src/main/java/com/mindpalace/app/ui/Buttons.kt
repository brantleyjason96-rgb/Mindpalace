package com.mindpalace.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A tactile "press and settle" scale shared by every custom button in this file — the squish
 * gives taps weight without a full ripple-only Material default (D21 polish pass).
 */
@Composable
fun rememberPressScale(pressedScale: Float = 0.90f): Pair<MutableInteractionSource, Modifier> {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale",
    )
    return interactionSource to Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * The circular capture buttons (mic, camera): a soft color-tinted shadow (not flat black — reads
 * as "lifted paper" against the warm theme), press-scale, and a haptic tick on tap.
 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    hapticType: HapticFeedbackType = HapticFeedbackType.LongPress,
    icon: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val (interactionSource, scaleModifier) = rememberPressScale()
    Surface(
        onClick = {
            haptics.performHapticFeedback(hapticType)
            onClick()
        },
        modifier = modifier
            .size(size)
            .then(scaleModifier)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = containerColor,
                spotColor = containerColor,
            )
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { icon() }
    }
}
