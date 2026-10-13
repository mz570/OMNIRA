package com.omnira.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a neumorphic effect to the modifier.
 */
fun Modifier.neumorphic(
    shape: Shape = RoundedCornerShape(28.dp),
    elevation: Dp = 6.dp,
    lightShadowColor: Color = Color.White,
    darkShadowColor: Color = Color(0xFFD1D9E6),
    isPressed: Boolean = false
) = this.drawBehind {
    val shadowRadius = elevation.toPx()
    val shadowOffset = elevation.toPx() / 2

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        
        if (isPressed) {
            // Inner shadow logic would go here
        } else {
            // Dark Shadow (Bottom-Right)
            paint.color = NeumorphicBackground 
            frameworkPaint.setShadowLayer(
                shadowRadius, 
                shadowOffset, 
                shadowOffset, 
                darkShadowColor.toArgb()
            )
            canvas.drawOutline(
                outline = shape.createOutline(size, layoutDirection, this),
                paint = paint
            )

            // Light Shadow (Top-Left)
            frameworkPaint.setShadowLayer(
                shadowRadius, 
                -shadowOffset, 
                -shadowOffset, 
                lightShadowColor.toArgb()
            )
            canvas.drawOutline(
                outline = shape.createOutline(size, layoutDirection, this),
                paint = paint
            )
        }
    }
}

/**
 * Returns a TextStyle with layered shadows to create a neumorphic "embossed" look.
 * This makes the text look like it is physically part of the background.
 */
fun getNeumorphicTextStyle(
    baseStyle: TextStyle,
    color: Color = Color(0xFF333333)
): TextStyle {
    return baseStyle.copy(
        color = color,
        shadow = Shadow(
            color = Color.White.copy(alpha = 0.9f),
            offset = Offset(-1.5f, -1.5f),
            blurRadius = 1.5f
        )
    )
}

/**
 * Creates a "carved" text effect where the text appears sunken into the surface.
 */
fun getCarvedTextStyle(
    baseStyle: TextStyle,
    color: Color = Color(0xFF555555)
): TextStyle {
    return baseStyle.copy(
        color = color,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.15f),
            offset = Offset(1.5f, 1.5f),
            blurRadius = 1.5f
        )
    )
}

/**
 * A glowing text style specifically for OMNIRA's green accents.
 */
fun getGlowTextStyle(
    baseStyle: TextStyle,
    color: Color = GreenPrimary
): TextStyle {
    return baseStyle.copy(
        color = color,
        shadow = Shadow(
            color = GreenPrimary.copy(alpha = 0.4f),
            offset = Offset(0f, 0f),
            blurRadius = 8f
        )
    )
}
