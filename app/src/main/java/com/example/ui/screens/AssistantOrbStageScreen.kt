package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianVoid
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class OrbIndicatorState(
    val title: String,
    val subtitle: String
) {
    Idle(
        title = "Idle",
        subtitle = "Resting ambient core — tap indicator or select a state below"
    ),
    Listening(
        title = "Listening",
        subtitle = "Capturing live voice input with reactive waveform ripples"
    ),
    Thinking(
        title = "Thinking",
        subtitle = "Synthesizing context and neural response arcs"
    );

    fun next(): OrbIndicatorState = when (this) {
        Idle -> Listening
        Listening -> Thinking
        Thinking -> Idle
    }
}

@Composable
fun AssistantOrbStageScreen(
    modifier: Modifier = Modifier,
    initialState: OrbIndicatorState = OrbIndicatorState.Idle
) {
    var currentState by remember { mutableStateOf(initialState) }
    var simulatedMicLevel by remember { mutableFloatStateOf(0.55f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianVoid,
                        MidnightSurface,
                        ObsidianVoid
                    )
                )
            )
    ) {
        val indicatorSize = (minOf(maxWidth, maxHeight) * 0.48f).coerceIn(156.dp, 260.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "NOVA CORE STAGE",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                Text(
                    text = "Assistant State Visualizer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            // Central Animated Circular Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedCircularOrbIndicator(
                    state = currentState,
                    micAmplitude = simulatedMicLevel,
                    onClick = { currentState = currentState.next() },
                    modifier = Modifier
                        .size(indicatorSize)
                        .testTag("central_orb_indicator")
                )

                AnimatedContent(
                    targetState = currentState,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                    label = "state_caption_transition"
                ) { state ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = when (state) {
                                OrbIndicatorState.Idle -> Color(0xFF0E2A47)
                                OrbIndicatorState.Listening -> Color(0xFF09363B)
                                OrbIndicatorState.Thinking -> Color(0xFF2D1654)
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                when (state) {
                                    OrbIndicatorState.Idle -> ElectricCyan.copy(alpha = 0.5f)
                                    OrbIndicatorState.Listening -> LuminousTeal.copy(alpha = 0.7f)
                                    OrbIndicatorState.Thinking -> NeonViolet.copy(alpha = 0.7f)
                                }
                            )
                        ) {
                            Text(
                                text = state.title.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = when (state) {
                                    OrbIndicatorState.Idle -> ElectricCyan
                                    OrbIndicatorState.Listening -> LuminousTeal
                                    OrbIndicatorState.Thinking -> NeonViolet
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("orb_state_badge")
                            )
                        }
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            // Interactive State Transition Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TRANSITION STATE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OrbIndicatorState.entries.forEachIndexed { index, state ->
                            SegmentedButton(
                                selected = currentState == state,
                                onClick = { currentState = state },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = OrbIndicatorState.entries.size
                                ),
                                label = {
                                    Text(
                                        text = state.title,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                modifier = Modifier.testTag("state_btn_${state.title.lowercase()}")
                            )
                        }
                    }

                    if (currentState == OrbIndicatorState.Listening) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Voice Input Amplitude",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LuminousTeal
                                )
                                Text(
                                    text = "${(simulatedMicLevel * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LuminousTeal
                                )
                            }
                            Slider(
                                value = simulatedMicLevel,
                                onValueChange = { simulatedMicLevel = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = LuminousTeal,
                                    activeTrackColor = LuminousTeal
                                )
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
        }
    }
}

@Composable
fun AnimatedCircularOrbIndicator(
    state: OrbIndicatorState,
    micAmplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(targetState = state, label = "orb_state_transition")

    val primaryColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 450) },
        label = "primary_color"
    ) { s ->
        when (s) {
            OrbIndicatorState.Idle -> ElectricCyan
            OrbIndicatorState.Listening -> LuminousTeal
            OrbIndicatorState.Thinking -> NeonViolet
        }
    }

    val accentColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 450) },
        label = "accent_color"
    ) { s ->
        when (s) {
            OrbIndicatorState.Idle -> Color(0xFF4F46E5)
            OrbIndicatorState.Listening -> NeonCyan
            OrbIndicatorState.Thinking -> ElectricCyan
        }
    }

    val coreScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "core_scale"
    ) { s ->
        when (s) {
            OrbIndicatorState.Idle -> 0.88f
            OrbIndicatorState.Listening -> 1.02f + micAmplitude * 0.10f
            OrbIndicatorState.Thinking -> 0.94f
        }
    }

    val waveStrength by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400) },
        label = "wave_strength"
    ) { s ->
        when (s) {
            OrbIndicatorState.Idle -> 0.12f
            OrbIndicatorState.Listening -> 0.45f + micAmplitude * 0.55f
            OrbIndicatorState.Thinking -> 0.25f
        }
    }

    val satelliteAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 350) },
        label = "satellite_alpha"
    ) { s ->
        when (s) {
            OrbIndicatorState.Thinking -> 1f
            OrbIndicatorState.Listening -> 0.35f
            OrbIndicatorState.Idle -> 0.15f
        }
    }

    val infinite = rememberInfiniteTransition(label = "orb_infinite_loops")

    val breathe by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breathe"
    )

    val outerSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_spin"
    )

    val innerCounterSpin by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_counter_spin"
    )

    val rippleProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "listening_ripple"
    )

    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = primaryColor),
                onClick = onClick
            )
            .semantics {
                contentDescription = "Animated circular indicator in ${state.title} state. Tap to transition."
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.48f
            val coreR = size.minDimension * 0.25f * coreScale * breathe

            // 1. Ambient Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.36f),
                        accentColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // 2. Listening Expanding Concentric Ripples
            if (state == OrbIndicatorState.Listening) {
                for (ringIndex in 0..1) {
                    val p = (rippleProgress + ringIndex * 0.5f) % 1f
                    val ringRadius = coreR + (maxR - coreR) * p
                    val ringAlpha = (1f - p) * (0.55f + micAmplitude * 0.35f)
                    drawCircle(
                        color = primaryColor.copy(alpha = ringAlpha.coerceIn(0f, 1f)),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            // 3. Outer Rotating Sweep Ring
            val sweepSpeedFactor = if (state == OrbIndicatorState.Thinking) 2f else 1f
            rotate(degrees = (outerSpin * sweepSpeedFactor) % 360f, pivot = center) {
                val ringR = coreR * 1.48f
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.9f),
                            accentColor.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = if (state == OrbIndicatorState.Thinking) 220f else 280f,
                    useCenter = false,
                    topLeft = Offset(center.x - ringR, center.y - ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 4. Inner Counter-Rotating Neural Arcs + Orbiting Satellites (highlighted in Thinking)
            rotate(degrees = innerCounterSpin, pivot = center) {
                val innerRingR = coreR * 1.24f
                drawArc(
                    color = accentColor.copy(alpha = 0.75f),
                    startAngle = 20f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRingR, center.y - innerRingR),
                    size = Size(innerRingR * 2f, innerRingR * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = primaryColor.copy(alpha = 0.75f),
                    startAngle = 200f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRingR, center.y - innerRingR),
                    size = Size(innerRingR * 2f, innerRingR * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Orbiting synaptic nodes
                val nodeRadius = 4.5.dp.toPx()
                listOf(20f, 130f, 200f, 310f).forEach { deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val nx = center.x + innerRingR * cos(rad).toFloat()
                    val ny = center.y + innerRingR * sin(rad).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = satelliteAlpha),
                        radius = nodeRadius,
                        center = Offset(nx, ny)
                    )
                }
            }

            // 5. Harmonic Circular Waveform Path
            val points = 40
            val maxAmp = 18.dp.toPx() * waveStrength
            val wavePath = Path()
            for (i in 0..points) {
                val theta = (i.toFloat() / points) * (2f * PI.toFloat())
                val freq = if (state == OrbIndicatorState.Thinking) 4f else 6f
                val mod = abs(sin(theta * freq + wavePhase) * cos(theta * 2f - wavePhase))
                val rPoint = coreR * 1.04f + maxAmp * mod
                val px = center.x + rPoint * cos(theta)
                val py = center.y + rPoint * sin(theta)
                if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
            }
            wavePath.close()
            drawPath(
                path = wavePath,
                brush = Brush.linearGradient(listOf(primaryColor, accentColor)),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 6. Luminous Core Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryColor.copy(alpha = 0.88f),
                        accentColor.copy(alpha = 0.78f),
                        ObsidianVoid
                    ),
                    center = Offset(center.x - coreR * 0.2f, center.y - coreR * 0.2f),
                    radius = coreR * 1.1f
                ),
                radius = coreR * 0.86f,
                center = center
            )
        }

        // Center State Icon Badge
        Surface(
            shape = CircleShape,
            color = ObsidianVoid.copy(alpha = 0.72f),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (state) {
                        OrbIndicatorState.Idle -> Icons.Rounded.MicNone
                        OrbIndicatorState.Listening -> Icons.Rounded.GraphicEq
                        OrbIndicatorState.Thinking -> Icons.Rounded.Psychology
                    },
                    contentDescription = state.title,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
