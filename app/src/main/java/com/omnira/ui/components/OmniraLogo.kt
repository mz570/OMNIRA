package com.omnira.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnira.ui.theme.GreenPrimary

/**
 * Official Branded Loading Animation for OMNIRA.
 * Features a pulsing Mint Green center and high-contrast rotating orbits.
 */
@Composable
fun OmniraLoadingLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    mintColor: Color = GreenPrimary,
    orbitColor: Color = Color.Gray.copy(alpha = 0.6f) // Increased contrast
) {
    val infiniteTransition = rememberInfiniteTransition(label = "omniraLoader")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(size.toPx() / 2, size.toPx() / 2)
        val radius = size.toPx() * 0.42f
        
        // 1. Central O Shape (Mint Green)
        val leafPath = Path().apply {
            fillType = PathFillType.EvenOdd
            val w = radius * 0.42f * pulse
            val h = radius * 0.78f * pulse
            moveTo(center.x, center.y - h)
            cubicTo(center.x + w, center.y - h*0.5f, center.x + w, center.y + h*0.5f, center.x, center.y + h)
            cubicTo(center.x - w, center.y + h*0.5f, center.x - w, center.y - h*0.5f, center.x, center.y - h)
            
            val hw = radius * 0.2f * pulse
            val hh = radius * 0.45f * pulse
            addOval(Rect(center.x - hw, center.y - hh, center.x + hw, center.y + hh))
        }
        drawPath(leafPath, color = mintColor)

        // 2. Animated Orbits
        val strokeWidth = 2.5.dp.toPx()
        val nodeRadius = 3.5.dp.toPx()
        
        rotate(rotation) {
            drawCircle(orbitColor, radius = radius, style = Stroke(strokeWidth))
            drawCircle(mintColor, radius = nodeRadius, center = Offset(center.x - radius, center.y))
        }
        rotate(rotation + 120f) {
            drawOval(orbitColor, topLeft = Offset(center.x - radius, center.y - radius * 0.65f), size = Size(radius * 2, radius * 1.3f), style = Stroke(strokeWidth))
            drawCircle(mintColor, radius = nodeRadius, center = Offset(center.x + radius, center.y))
        }
        rotate(-rotation * 0.8f + 240f) {
            drawOval(orbitColor, topLeft = Offset(center.x - radius, center.y - radius * 0.65f), size = Size(radius * 2, radius * 1.3f), style = Stroke(strokeWidth))
            drawCircle(mintColor, radius = nodeRadius, center = Offset(center.x, center.y - radius * 0.65f))
        }
    }
}

@Composable
fun FullScreenLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFECF0F3)),
        contentAlignment = Alignment.Center
    ) {
        OmniraLoadingLogo(size = 120.dp)
    }
}
