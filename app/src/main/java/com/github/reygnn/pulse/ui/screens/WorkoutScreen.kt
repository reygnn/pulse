package com.github.reygnn.pulse.ui.screens


import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch
import com.github.reygnn.pulse.viewmodel.WorkoutState
import com.github.reygnn.pulse.workout.HrZone
import com.github.reygnn.pulse.workout.WorkoutSession

// --- Workout Controls (Start/Stop Button) ---

@Composable
fun WorkoutControls(
    workoutState: WorkoutState,
    onStartWorkout: (longterm: Boolean) -> Unit,
    onStopWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (workoutState.isActive) {
        Button(
            onClick = onStopWorkout,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            modifier = modifier
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Training stoppen", fontWeight = FontWeight.SemiBold)
        }
    } else {
        var longterm by remember { mutableStateOf(false) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            Button(
                onClick = { onStartWorkout(longterm) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (longterm) Color(0xFF42A5F5) else Color(0xFF66BB6A)
                ),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (longterm) "Langzeit starten" else "Training starten",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { longterm = !longterm }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = longterm,
                    onCheckedChange = { longterm = it },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Langzeit-Modus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Workout Active Panel ---

@Composable
fun WorkoutPanel(
    workoutState: WorkoutState,
    currentHr: Int,
    onToggleScreenOn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = workoutState.currentZone
    val zoneColor = Color(zone.colorHex)
    val session = workoutState.currentSession

    // Elapsed timer that ticks every second
    var displayElapsed by remember { mutableLongStateOf(workoutState.elapsedMs) }
    LaunchedEffect(workoutState.isActive) {
        if (workoutState.isActive) {
            while (true) {
                displayElapsed = workoutState.elapsedMs
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    // Also update when new samples arrive
    LaunchedEffect(workoutState.elapsedMs) {
        displayElapsed = workoutState.elapsedMs
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = zoneColor.copy(alpha = 0.12f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zone label with pulsing dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = zoneColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = zone.label.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = zoneColor,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timer
            Text(
                text = formatDuration(displayElapsed),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Zone bar
            ZoneBar(currentHr = currentHr, maxHr = workoutState.maxHr)

            Spacer(modifier = Modifier.height(16.dp))

            // Workout stats row
            if (session != null && session.samples.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WorkoutStatItem(
                        value = "${session.avgHr}",
                        label = "Avg",
                        unit = "bpm"
                    )
                    WorkoutStatItem(
                        value = "${session.peakHr}",
                        label = "Peak",
                        unit = "bpm"
                    )
                    WorkoutStatItem(
                        value = "${session.caloriesEstimate}",
                        label = "Kalorien",
                        unit = "kcal"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleScreenOn() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = workoutState.keepScreenOn,
                    onCheckedChange = { onToggleScreenOn() },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Display dimmen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val alpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun WorkoutStatItem(value: String, label: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Zone Bar ---

@Composable
private fun ZoneBar(currentHr: Int, maxHr: Int) {
    val zones = HrZone.entries.filter { it != HrZone.REST }
    val pct = if (maxHr > 0) (currentHr.toFloat() / maxHr).coerceIn(0f, 1.05f) else 0f

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val barHeight = size.height
            val totalWidth = size.width
            val cornerRadius = CornerRadius(barHeight / 2, barHeight / 2)

            // Draw zone segments
            zones.forEachIndexed { index, zone ->
                val startPct = zone.minPct
                val endPct = zone.maxPct.coerceAtMost(1f)
                val x = startPct * totalWidth
                val w = (endPct - startPct) * totalWidth

                drawRoundRect(
                    color = Color(zone.colorHex).copy(alpha = 0.4f),
                    topLeft = Offset(x, 0f),
                    size = Size(w, barHeight),
                    cornerRadius = if (index == 0 || index == zones.lastIndex) cornerRadius else CornerRadius.Zero
                )
            }

            // Draw position indicator
            if (pct > 0) {
                val indicatorX = (pct.coerceAtMost(1f) * totalWidth).coerceIn(6f, totalWidth - 6f)
                drawCircle(
                    color = Color.White,
                    radius = barHeight / 2 + 2,
                    center = Offset(indicatorX, barHeight / 2)
                )
                val currentZone = HrZone.fromHeartRate(currentHr, maxHr)
                drawCircle(
                    color = Color(currentZone.colorHex),
                    radius = barHeight / 2 - 1,
                    center = Offset(indicatorX, barHeight / 2)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Zone labels
        Row(modifier = Modifier.fillMaxWidth()) {
            zones.forEach { zone ->
                val weight = zone.maxPct.coerceAtMost(1f) - zone.minPct
                Text(
                    text = "${(zone.minPct * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    modifier = Modifier.weight(weight)
                )
            }
        }
    }
}

// --- Workout Summary Dialog ---

@Composable
fun WorkoutSummaryDialog(
    session: WorkoutSession,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(2000)
            showCopied = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schliessen")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Training beendet")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Duration
                SummaryRow(label = "Dauer", value = formatDuration(session.durationMs))
                CopyableSummaryRow(
                    label = "Durchschnitt",
                    value = "${session.avgHr} bpm",
                    copyValue = "${session.avgHr}"
                )
                SummaryRow(label = "Minimum", value = "${session.minHr} bpm")
                SummaryRow(label = "Maximum", value = "${session.peakHr} bpm")
                SummaryRow(label = "Kalorien", value = "~${session.caloriesEstimate} kcal")
                SummaryRow(label = "Messpunkte", value = "${session.samples.size}")

                Spacer(modifier = Modifier.height(4.dp))

                // Zone distribution
                Text(
                    text = "Zonen-Verteilung",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                val distribution = session.zoneDistribution()
                HrZone.entries.filter { it != HrZone.REST }.forEach { zone ->
                    val pct = distribution[zone] ?: 0f
                    if (pct > 0.001f) {
                        ZoneDistributionRow(zone = zone, percentage = pct)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Workout HR chart
                if (session.samples.size > 1) {
                    Text(
                        text = "Herzfrequenz-Verlauf",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    WorkoutChart(
                        session = session,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Copy all button
                OutlinedButton(
                    onClick = {
                        val text = buildSummaryText(session, distribution)
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipData.newPlainText("workout", text).toClipEntry()
                            )
                        }
                        showCopied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (showCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (showCopied) Color(0xFF66BB6A) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showCopied) "Kopiert!" else "Ergebnis kopieren",
                        color = if (showCopied) Color(0xFF66BB6A) else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

private fun buildSummaryText(session: WorkoutSession, distribution: Map<HrZone, Float>): String {
    val profile = session.userProfile
    val durationMin = session.durationMs / 60_000
    val gender = if (profile.isMale) "m" else "w"

    val warmup = ((distribution[HrZone.WARMUP] ?: 0f) * 100).toInt()
    val fatBurn = ((distribution[HrZone.FAT_BURN] ?: 0f) * 100).toInt()
    val cardio = ((distribution[HrZone.CARDIO] ?: 0f) * 100).toInt()
    val peak = ((distribution[HrZone.PEAK] ?: 0f) * 100).toInt()

    return "avg${session.avgHr}bpm|min${session.minHr}bpm|max${session.peakHr}bpm|${session.caloriesEstimate}kcal|warm${warmup}%|fett${fatBurn}%|cardio${cardio}%|peak${peak}%|${durationMin}min|${gender}_${profile.age}j_${profile.weightKg.toInt()}kg"
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CopyableSummaryRow(label: String, value: String, copyValue: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipData.newPlainText("heartrate", copyValue).toClipEntry()
                        )
                    }
                    showCopied = true
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (showCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = "Kopieren",
                    modifier = Modifier.size(16.dp),
                    tint = if (showCopied) {
                        Color(0xFF66BB6A)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun ZoneDistributionRow(zone: HrZone, percentage: Float) {
    val zoneColor = Color(zone.colorHex)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(zoneColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = zone.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(110.dp)
        )

        // Progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(zoneColor.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage)
                    .clip(RoundedCornerShape(4.dp))
                    .background(zoneColor)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(percentage * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

// --- Workout Chart (full session) ---

@Composable
private fun WorkoutChart(session: WorkoutSession, modifier: Modifier = Modifier) {
    val samples = session.samples

    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas

        val minHr = (samples.minOf { it.heartRate } - 5).coerceAtLeast(40).toFloat()
        val maxHr = (samples.maxOf { it.heartRate } + 5).toFloat()
        val range = (maxHr - minHr).coerceAtLeast(1f)
        val totalTime = samples.last().timestampMs.toFloat().coerceAtLeast(1f)

        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = (sample.timestampMs / totalTime) * size.width
            val y = size.height - ((sample.heartRate - minHr) / range) * size.height

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Color the line based on zones
        drawPath(
            path = path,
            color = Color(0xFFE53935),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }
}

// --- Helpers ---

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}