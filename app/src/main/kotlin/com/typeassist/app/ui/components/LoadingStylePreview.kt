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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.typeassist.app.data.LoadingIndicatorStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ============================== LOADING INDICATOR STYLES ==============================
 *
 * The animations the floating overlay can show while the model is working, drawn here in
 * Compose for the Settings preview. They mirror `OverlayManager` exactly - same shapes, same
 * timings - because the preview is how people choose, and a preview that lies about the result is
 * worse than no preview at all.
 *
 * Every style can wear the neon look: the body is drawn in the neon colour and sits in a coloured
 * halo. The spectrum ring is always neon, and cycles through hues derived from that colour.
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
    LoadingStyleOption("orbit", "Orbit", "A dot circling a glowing core"),
    LoadingStyleOption("dotsring", "Dot ring", "A ring of dots turning together"),
    LoadingStyleOption("wave", "Wave", "Bars rolling through a wave"),
    LoadingStyleOption("pulsering", "Pulse ring", "Rings expanding outwards"),
    LoadingStyleOption(
        LoadingIndicatorStyle.STYLE_SPECTRUM,
        "Spectrum ring",
        "Rotating spectrum, built from your neon colour"
    )
)

/** How long one turn of the orbiting and ring animations takes, in milliseconds. */
private const val SPIN_DURATION_MS = 1400

/** How long one pass of the wave and pulse-ring animations takes, in milliseconds. */
private const val WAVE_DURATION_MS = 1400

@Composable
fun LoadingStylePreview(
    styleId: String,
    pickedColor: Int = LoadingIndicatorStyle.DEFAULT_COLOR,
    neonColor: Int = LoadingIndicatorStyle.DEFAULT_NEON_COLOR,
    neon: Boolean = false,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val body = Color(
        LoadingIndicatorStyle.effectiveColor(styleId, pickedColor, neon, neonColor)
    )
    val isSpectrum = LoadingIndicatorStyle.isSpectrumStyle(styleId)
    // The spectrum ring brings its own glow, so it never gets the halo on top.
    val halo = neon && !isSpectrum
    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (halo) {
                Modifier.drawBehind {
                    val radius = size.maxDimension * 0.85f + 14.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.55f to body.copy(alpha = 0.42f),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }
            } else {
                Modifier
            }
        ) {
            when (styleId) {
                "classic" -> {
                    Box(modifier = Modifier.padding(14.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = body,
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
                                    .background(body)
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
                                .background(body.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .scale(coreScale)
                                .clip(CircleShape)
                                .background(body)
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
                                    .background(body)
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
                                    .background(body)
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
                            color = body,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Thinking",
                            color = body,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                "orbit" -> {
                    val infinite = rememberInfiniteTransition(label = "orbit")
                    val spin by infinite.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            tween(SPIN_DURATION_MS, easing = LinearEasing)
                        ),
                        label = "orbitSpin"
                    )
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(body)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer { rotationZ = spin }
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = 15.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(body)
                            )
                        }
                    }
                }
                "dotsring" -> {
                    val infinite = rememberInfiniteTransition(label = "dotsring")
                    val spin by infinite.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            tween(SPIN_DURATION_MS, easing = LinearEasing)
                        ),
                        label = "dotsRingSpin"
                    )
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { rotationZ = spin }
                        ) {
                            repeat(5) { index ->
                                val angle = 2.0 * PI * index / 5.0
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset(
                                            x = 16.dp * cos(angle).toFloat(),
                                            y = 16.dp * sin(angle).toFloat()
                                        )
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(body)
                                )
                            }
                        }
                    }
                }
                "wave" -> {
                    val infinite = rememberInfiniteTransition(label = "wave")
                    val phase by infinite.animateFloat(
                        initialValue = 0f,
                        targetValue = (2.0 * PI).toFloat(),
                        animationSpec = infiniteRepeatable(
                            tween(WAVE_DURATION_MS, easing = LinearEasing)
                        ),
                        label = "wavePhase"
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        repeat(5) { index ->
                            val height = waveBarScale(phase, index)
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height(18.dp)
                                    .scale(scaleX = 1f, scaleY = height)
                                    .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f) }
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(body)
                            )
                        }
                    }
                }
                "pulsering" -> {
                    val infinite = rememberInfiniteTransition(label = "pulsering")
                    val phase by infinite.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(WAVE_DURATION_MS, easing = LinearEasing)
                        ),
                        label = "pulseRingPhase"
                    )
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        repeat(2) { index ->
                            val t = (phase + index * 0.5f) % 1f
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        scaleX = 0.45f + 0.55f * t
                                        scaleY = 0.45f + 0.55f * t
                                        alpha = 0.95f * (1f - t)
                                    }
                                    .border(2.dp, body, CircleShape)
                            )
                        }
                    }
                }
                LoadingIndicatorStyle.STYLE_SPECTRUM -> {
                    val infinite = rememberInfiniteTransition(label = "spectrum")
                    val spin by infinite.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(SPIN_DURATION_MS, easing = LinearEasing)),
                        label = "spectrumSpin"
                    )
                    val glow by infinite.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(900, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse
                        ),
                        label = "spectrumGlow"
                    )
                    val corePulse by infinite.animateFloat(
                        initialValue = 0.75f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            tween(900, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse
                        ),
                        label = "spectrumCore"
                    )
                    val neonColors = LoadingIndicatorStyle.neonSpectrum(neonColor).map { Color(it) }
                    val coreColor = Color.White
                    val sweepAngle = 300f
                    Canvas(modifier = Modifier.size(34.dp)) {
                        val stroke = size.minDimension / 12f
                        val topLeft = Offset(stroke, stroke)
                        val arcSize = Size(size.width - stroke * 2, size.height - stroke * 2)
                        val neonBrush = Brush.sweepGradient(
                            0f to neonColors[0],
                            0.25f to neonColors[1],
                            0.5f to neonColors[2],
                            0.75f to neonColors[3],
                            1f to neonColors[4],
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
                            // Layered spectral blooms are what make the spectrum read as neon.
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
                else -> {
                    Box(modifier = Modifier.padding(14.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = body,
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * The height of one bar of the wave, as a fraction of its height. The phase travels through the
 * wave so the crest rolls along the bars instead of all of them pulsing together.
 */
private fun waveBarScale(phase: Float, index: Int): Float {
    val angle = phase - index * 0.9f
    return 0.3f + 0.7f * ((sin(angle.toDouble()).toFloat() + 1f) / 2f)
}
