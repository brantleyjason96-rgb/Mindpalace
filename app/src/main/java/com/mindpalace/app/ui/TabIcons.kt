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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn bottom-nav glyphs, same convention as [MicIcon]/[CameraIcon] (D21): no icon
 * library in the project — ask before adding one (CLAUDE.md).
 */
@Composable
fun NoteTabIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.09f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.1f),
            size = Size(w * 0.64f, h * 0.8f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = strokeWidth),
        )
        listOf(0.38f, 0.55f, 0.72f).forEach { y ->
            drawLine(
                color = tint,
                start = Offset(w * 0.3f, h * y),
                end = Offset(w * 0.7f, h * y),
                strokeWidth = strokeWidth * 0.7f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun ClockTabIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.09f
        val radius = w * 0.4f
        val center = Offset(w / 2f, h / 2f)
        drawCircle(color = tint, radius = radius, center = center, style = Stroke(width = strokeWidth))
        drawLine(color = tint, start = center, end = Offset(center.x, center.y - radius * 0.55f), strokeWidth = strokeWidth * 0.8f, cap = StrokeCap.Round)
        drawLine(color = tint, start = center, end = Offset(center.x + radius * 0.4f, center.y), strokeWidth = strokeWidth * 0.8f, cap = StrokeCap.Round)
    }
}

@Composable
fun VaultTabIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.09f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.1f, h * 0.38f),
            size = Size(w * 0.8f, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.08f, h * 0.38f),
            end = Offset(w * 0.92f, h * 0.38f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.4f, h * 0.62f),
            end = Offset(w * 0.6f, h * 0.62f),
            strokeWidth = strokeWidth * 0.8f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun ShieldTabIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.09f
        val path = Path().apply {
            moveTo(w / 2f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.52f)
            quadraticTo(w * 0.86f, h * 0.82f, w / 2f, h * 0.94f)
            quadraticTo(w * 0.14f, h * 0.82f, w * 0.14f, h * 0.52f)
            lineTo(w * 0.14f, h * 0.22f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = strokeWidth))
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.5f),
            end = Offset(w * 0.46f, h * 0.64f),
            strokeWidth = strokeWidth * 0.8f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.46f, h * 0.64f),
            end = Offset(w * 0.68f, h * 0.38f),
            strokeWidth = strokeWidth * 0.8f,
            cap = StrokeCap.Round,
        )
    }
}
