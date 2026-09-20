package com.ilyro.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
internal fun IlyroShieldIcon(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.07f)
            cubicTo(w * 0.63f, h * 0.15f, w * 0.76f, h * 0.18f, w * 0.88f, h * 0.20f)
            lineTo(w * 0.88f, h * 0.47f)
            cubicTo(w * 0.88f, h * 0.70f, w * 0.73f, h * 0.88f, w * 0.50f, h * 0.96f)
            cubicTo(w * 0.27f, h * 0.88f, w * 0.12f, h * 0.70f, w * 0.12f, h * 0.47f)
            lineTo(w * 0.12f, h * 0.20f)
            cubicTo(w * 0.24f, h * 0.18f, w * 0.37f, h * 0.15f, w * 0.50f, h * 0.07f)
            close()
        }

        drawPath(
            path = shield,
            color = color,
            style = Stroke(
                width = w * 0.095f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        if (active) {
            drawLine(
                color = color,
                start = Offset(w * 0.31f, h * 0.50f),
                end = Offset(w * 0.45f, h * 0.64f),
                strokeWidth = w * 0.10f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.45f, h * 0.64f),
                end = Offset(w * 0.70f, h * 0.37f),
                strokeWidth = w * 0.10f,
                cap = StrokeCap.Round
            )
        }
    }
}
