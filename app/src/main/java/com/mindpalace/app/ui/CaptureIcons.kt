package com.mindpalace.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn glyphs instead of emoji (D21): emoji don't tint to the theme or respect dark mode
 * consistently across devices/font stacks, and no icon library is in the project — ask before
 * adding one (CLAUDE.md). A few Canvas draw calls cover exactly the two icons this app needs.
 */
@Composable
fun MicIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val capsuleWidth = w * 0.34f
        val capsuleHeight = h * 0.5f
        val capsuleLeft = (w - capsuleWidth) / 2f
        val capsuleTop = h * 0.06f
        drawRoundRect(
            color = tint,
            topLeft = Offset(capsuleLeft, capsuleTop),
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f),
        )
        val strokeWidth = w * 0.09f
        val cradleTop = capsuleTop + capsuleHeight * 0.55f
        val cradleRadius = w * 0.28f
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w / 2f - cradleRadius, cradleTop),
            size = Size(cradleRadius * 2f, cradleRadius * 2f),
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        val stemTop = cradleTop + cradleRadius * 1.55f
        val stemBottom = h * 0.92f
        drawLine(
            color = tint,
            start = Offset(w / 2f, stemTop),
            end = Offset(w / 2f, stemBottom),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val baseWidth = w * 0.34f
        drawLine(
            color = tint,
            start = Offset(w / 2f - baseWidth / 2f, stemBottom),
            end = Offset(w / 2f + baseWidth / 2f, stemBottom),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
fun CameraIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.08f
        val bodyTop = h * 0.3f
        val bodyHeight = h * 0.56f
        // Outline only, so the filled lens below reads clearly against it (no same-color overlap).
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.06f, bodyTop),
            size = Size(w * 0.88f, bodyHeight),
            cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
            style = Stroke(width = strokeWidth),
        )
        val humpWidth = w * 0.3f
        val humpHeight = h * 0.14f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w / 2f - humpWidth / 2f, bodyTop - humpHeight * 0.65f),
            size = Size(humpWidth, humpHeight),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
        )
        val lensRadius = h * 0.17f
        val lensCenter = Offset(w / 2f, bodyTop + bodyHeight / 2f + h * 0.02f)
        drawCircle(color = tint, radius = lensRadius, center = lensCenter, style = Stroke(width = strokeWidth))
        drawCircle(color = tint, radius = lensRadius * 0.4f, center = lensCenter)
    }
}
