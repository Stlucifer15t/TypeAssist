package com.typeassist.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.LoadingIndicatorStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * ============================== LOADING INDICATOR STYLES ==============================
 *
 * The seven animations the floating overlay can show while the model is working, drawn here in
 * Compose for the Settings preview. They mirror `OverlayManager` exactly - same shapes, same
 * timings - because the preview is how people choose, and a preview that lies about the result is
 * worse than no preview at all.
 */
data class LoadingStyleOption(
    val id: String,
    val name: String,
    val description: String
)

val LOADING_STYLES: List<LoadingStyleOption> = listOf(
    LoadingStyleOption("classic", "Classic", "Simple spinner in a dark bubble"),
    LoadingStyleOption("dots", "Bouncing dots", "Three dots that bounce and fade"),
    LoadingStyleOption("pulse", "Pulse", "A dot with a pulsing halo"),
    LoadingStyleOption("bars", "Bars", "Equaliser bars in three steps"),
    LoadingStyleOption("typing", "Typing", "Message-style typing indicator"),
    LoadingStyleOption("pill", "Pill", "Rounded pill with a short label"),
    LoadingStyleOption("neon", "Neon ring", "Rotating spectrum ring, fixed palette")
)

@Composable
fun LoadingStylePreview(
    styleId: String,
    color: Color = Color.White,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val primary = color
    val white = color
    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        when (styleId) {
            "classic" -> {
                Box(modifier = Modifier.padding(14.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = white,
                        strokeWidth = 2.5.dp
                    )
                }
            }
            "dots" -> {
                val infinite = rememberInfiniteTransition(label = "dots")
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val dotScale by infinite.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = index * 180, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotScale$index"
                        )
                        val dotAlpha by infinite.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = index * 180),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotAlpha$index"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(dotScale)
                                .alpha(dotAlpha)
                                .clip(CircleShape)
                                .background(white)
                        )
                    }
                }
            }
            "pulse" -> {
                val infinite = rememberInfiniteTransition(label = "pulse")
                val coreScale by infinite.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.35f,
                    animationSpec = infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                val haloScale by infinite.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.6f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                    label = "outerScale"
                )
                val haloAlpha by infinite.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                    label = "outerAlpha"
                )
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .scale(haloScale)
                            .alpha(haloAlpha)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .scale(coreScale)
                            .clip(CircleShape)
                            .background(white)
                    )
                }
            }
            "bars" -> {
                val infinite = rememberInfiniteTransition(label = "bars")
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(3) { index ->
                        val barScale by infinite.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.7f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, delayMillis = index * 130, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bar$index"
                        )
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(16.dp)
                                .scale(scaleX = 1f, scaleY = barScale)
                                .clip(RoundedCornerShape(3.dp))
                                .background(white)
                        )
                    }
                }
            }
            "typing" -> {
                val infinite = rememberInfiniteTransition(label = "typing")
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val offset by infinite.animateFloat(
                            initialValue = 0f,
                            targetValue = -8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(380, delayMillis = index * 110, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "typingOffset$index"
                        )
                        Box(
                            modifier = Modifier
                                .offset(y = offset.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(white)
                        )
                    }
                }
            }
            "pill" -> {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = white,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Thinking",
                        color = white,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            "neon" -> {
                val infinite = rememberInfiniteTransition(label = "neon")
                val spin by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                    label = "neonSpin"
                )
                val glow by infinite.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "neonGlow"
                )
                val corePulse by infinite.animateFloat(
                    initialValue = 0.75f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "neonCore"
                )
                val neonColors = LoadingIndicatorStyle.NEON_PALETTE.map { Color(it) }
                val coreColor = Color.White
                val sweepAngle = 300f
                Canvas(modifier = Modifier.size(34.dp)) {
                    val stroke = size.minDimension / 12f
                    val inset = stroke * 3f
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)
                    val neonBrush = Brush.sweepGradient(
                        0f to neonColors[0].copy(alpha = 0f),
                        0.08f to neonColors[0].copy(alpha = 0.95f),
                        0.25f to neonColors[1],
                        0.45f to neonColors[2],
                        0.65f to neonColors[3],
                        0.85f to neonColors[4],
                        1f to neonColors[0],
                        center = center
                    )
                    val hotCoreBrush = Brush.sweepGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.3f to Color.White.copy(alpha = 0.25f),
                        0.85f to Color.White.copy(alpha = 0.75f),
                        1f to Color.White,
                        center = center
                    )

                    rotate(degrees = spin) {
                        // Layered spectral blooms are what make the locked palette read as neon.
                        drawArc(
                            brush = neonBrush,
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.08f * glow,
                            style = Stroke(width = stroke * 5f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = neonBrush,
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.16f * glow,
                            style = Stroke(width = stroke * 3.5f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = neonBrush,
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.28f * glow,
                            style = Stroke(width = stroke * 2f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = neonColors[0].copy(alpha = 0.16f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                        drawArc(
                            brush = neonBrush,
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = hotCoreBrush,
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke * 0.45f, cap = StrokeCap.Round)
                        )
                        val radians = Math.toRadians(sweepAngle.toDouble())
                        val radius = arcSize.width / 2f
                        drawCircle(
                            color = coreColor,
                            radius = stroke * 0.8f,
                            center = Offset(
                                center.x + radius * cos(radians).toFloat(),
                                center.y + radius * sin(radians).toFloat()
                            )
                        )
                    }
                    drawCircle(color = coreColor, radius = stroke * 1.1f * corePulse, center = center)
                }
            }
        }
    }
}
