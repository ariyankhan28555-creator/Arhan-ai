package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AssistantPhase
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NovaOrbVisualizer(
    phase: AssistantPhase,
    rmsLevel: Float,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 188.dp
) {
    val infinite = rememberInfiniteTransition(label = "nova_orb_transition")

    val rotationSlow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotate_slow"
    )

    val rotationFast by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotate_fast"
    )

    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )

    val breathe by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )

    val smoothRms by animateFloatAsState(
        targetValue = rmsLevel.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 450f, dampingRatio = 0.7f),
        label = "rms_smooth"
    )

    val coreColor by animateColorAsState(
        targetValue = when (phase) {
            AssistantPhase.IDLE -> ElectricCyan
            AssistantPhase.LISTENING -> NeonCyan
            AssistantPhase.THINKING -> NeonViolet
            AssistantPhase.SPEAKING -> LuminousTeal
        },
        animationSpec = tween(350),
        label = "orb_core_color"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (phase) {
            AssistantPhase.IDLE -> NeonViolet
            AssistantPhase.LISTENING -> ElectricCyan
            AssistantPhase.THINKING -> ElectricCyan
            AssistantPhase.SPEAKING -> NeonViolet
        },
        animationSpec = tween(350),
        label = "orb_secondary_color"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(orbSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = NeonCyan),
                    onClick = onOrbClick
                )
                .semantics {
                    contentDescription = when (phase) {
                        AssistantPhase.IDLE -> "Tap to speak to NOVA"
                        AssistantPhase.LISTENING -> "NOVA is listening. Tap to stop."
                        AssistantPhase.THINKING -> "NOVA is thinking."
                        AssistantPhase.SPEAKING -> "NOVA is speaking. Tap to interrupt."
                    }
                }
                .testTag("nova_orb_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.31f * breathe
                val audioBoost = when (phase) {
                    AssistantPhase.LISTENING -> 1f + smoothRms * 0.28f
                    AssistantPhase.SPEAKING -> 1f + (sin(wavePhase) * 0.08f + 0.10f)
                    AssistantPhase.THINKING -> 1.05f
                    AssistantPhase.IDLE -> 1f
                }
                val r = baseRadius * audioBoost

                // Outer ambient radial halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.34f),
                            secondaryColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.50f
                    ),
                    radius = size.minDimension * 0.50f,
                    center = center
                )

                // Outer rotating cyber ring
                rotate(degrees = rotationSlow, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                coreColor.copy(alpha = 0.05f),
                                coreColor.copy(alpha = 0.85f),
                                secondaryColor.copy(alpha = 0.85f),
                                Color.Transparent
                            ),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = Offset(center.x - r * 1.42f, center.y - r * 1.42f),
                        size = androidx.compose.ui.geometry.Size(r * 2.84f, r * 2.84f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner counter-rotating orbital arc
                rotate(degrees = if (phase == AssistantPhase.THINKING) rotationFast * 2f else rotationFast, pivot = center) {
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.65f),
                        startAngle = 30f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - r * 1.20f, center.y - r * 1.20f),
                        size = androidx.compose.ui.geometry.Size(r * 2.40f, r * 2.40f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = coreColor.copy(alpha = 0.70f),
                        startAngle = 210f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(center.x - r * 1.20f, center.y - r * 1.20f),
                        size = androidx.compose.ui.geometry.Size(r * 2.40f, r * 2.40f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Voice waveform ring (36 radial harmonic spokes)
                val spokeCount = 36
                val amplitude = when (phase) {
                    AssistantPhase.LISTENING -> 6.dp.toPx() + smoothRms * 18.dp.toPx()
                    AssistantPhase.SPEAKING -> 11.dp.toPx()
                    AssistantPhase.THINKING -> 5.dp.toPx()
                    AssistantPhase.IDLE -> 2.dp.toPx()
                }
                val wavePath = Path()
                for (i in 0..spokeCount) {
                    val angle = (i.toFloat() / spokeCount) * (2f * PI.toFloat())
                    val harmonic = sin(angle * 6f + wavePhase) * cos(angle * 3f - wavePhase)
                    val radialOffset = r * 1.03f + amplitude * kotlin.math.abs(harmonic)
                    val x = center.x + radialOffset * cos(angle)
                    val y = center.y + radialOffset * sin(angle)
                    if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                }
                wavePath.close()
                drawPath(
                    path = wavePath,
                    brush = Brush.linearGradient(listOf(coreColor, secondaryColor)),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Luminous neural sphere core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.90f),
                            coreColor.copy(alpha = 0.88f),
                            secondaryColor.copy(alpha = 0.75f),
                            Color(0xFF0A1026)
                        ),
                        center = Offset(center.x - r * 0.22f, center.y - r * 0.22f),
                        radius = r * 1.15f
                    ),
                    radius = r * 0.86f,
                    center = center
                )
            }

            // Center state icon badge
            Surface(
                shape = CircleShape,
                color = Color(0xAA060913),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (phase) {
                            AssistantPhase.IDLE -> Icons.Rounded.Mic
                            AssistantPhase.LISTENING -> Icons.Rounded.GraphicEq
                            AssistantPhase.THINKING -> Icons.Rounded.Psychology
                            AssistantPhase.SPEAKING -> Icons.Rounded.VolumeUp
                        },
                        contentDescription = phase.labelEn,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (phase) {
                    AssistantPhase.IDLE -> "TAP ORB TO SPEAK"
                    AssistantPhase.LISTENING -> "LISTENING • सुन रही हूँ"
                    AssistantPhase.THINKING -> "THINKING • विश्लेषण जारी"
                    AssistantPhase.SPEAKING -> "SPEAKING • उत्तर दे रही हूँ"
                },
                style = MaterialTheme.typography.labelMedium,
                color = coreColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
