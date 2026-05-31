package com.thalos.cinephile.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.thalos.cinephile.ui.theme.RichBlack
import com.thalos.cinephile.ui.theme.UltraViolet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun CinematicBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val offset1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bg1"
    )
    val offset2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(35000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bg2"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = RichBlack)

        // Animated orb 1
        val radius1 = size.width * 0.6f
        val x1 = center.x + cos(Math.toRadians(offset1.value.toDouble())).toFloat() * radius1 * 0.3f
        val y1 = center.y + sin(Math.toRadians(offset1.value.toDouble())).toFloat() * radius1 * 0.3f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(UltraViolet.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(x1, y1),
                radius = radius1
            ),
            radius = radius1,
            center = Offset(x1, y1)
        )

        // Animated orb 2
        val radius2 = size.width * 0.5f
        val x2 = center.x + cos(Math.toRadians(offset2.value.toDouble() + 180)).toFloat() * radius2 * 0.4f
        val y2 = center.y + sin(Math.toRadians(offset2.value.toDouble() + 180)).toFloat() * radius2 * 0.4f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.1f), Color.Transparent),
                center = Offset(x2, y2),
                radius = radius2
            ),
            radius = radius2,
            center = Offset(x2, y2)
        )
    }
}
