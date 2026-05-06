package com.github.reygnn.pulse.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.reygnn.pulse.ble.HeartRateManager.ConnectionStatus
import com.github.reygnn.pulse.viewmodel.UiState

import com.github.reygnn.pulse.viewmodel.WorkoutState

@Composable
fun HeartRateScreen(
    state: UiState,
    workoutState: WorkoutState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (ScanResult) -> Unit,
    onDisconnect: () -> Unit,
    onStartWorkout: (longterm: Boolean) -> Unit,
    onStopWorkout: () -> Unit,
    onDismissSummary: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleScreenOn: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            TopSection(state, onOpenSettings)

            Spacer(modifier = Modifier.height(16.dp))

            when (state.connectionStatus) {
                ConnectionStatus.Disconnected -> {
                    DisconnectedView(
                        onStartScan = onStartScan,
                        errorMessage = state.errorMessage
                    )
                }
                ConnectionStatus.Scanning -> {
                    ScanningView(
                        scanResults = state.scanResults,
                        onDeviceSelected = onDeviceSelected,
                        onStopScan = onStopScan
                    )
                }
                ConnectionStatus.Connecting -> {
                    ConnectingView(
                        deviceName = state.deviceName,
                        onCancel = onDisconnect
                    )
                }
                ConnectionStatus.Connected -> {
                    ConnectedView(
                        heartRate = state.heartRate,
                        history = state.heartRateHistory,
                        deviceName = state.deviceName,
                        workoutState = workoutState,
                        onDisconnect = onDisconnect,
                        onStartWorkout = onStartWorkout,
                        onStopWorkout = onStopWorkout,
                        onToggleScreenOn = onToggleScreenOn
                    )
                }
            }
        }

        // Workout summary dialog
        if (workoutState.showSummary && workoutState.currentSession != null) {
            WorkoutSummaryDialog(
                session = workoutState.currentSession,
                onDismiss = onDismissSummary
            )
        }

        // Dimm-Overlay wenn "Display an lassen" aktiv
        if (workoutState.keepScreenOn && workoutState.isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { onToggleScreenOn() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.heartRate > 0) "${state.heartRate}" else "--",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(workoutState.currentZone.colorHex).copy(alpha = 0.6f)
                    )
                    Text(
                        text = "BPM",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Tippen zum Aufhellen",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}


// --- Top Section ---

@Composable
private fun TopSection(state: UiState, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.MonitorHeart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Pulse",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))

        // Battery level (immer anzeigen wenn bekannt)
        if (state.batteryLevel >= 0) {
            val isLow = state.batteryLevel <= 20
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isLow) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLow) Icons.Filled.BatteryAlert else Icons.Filled.Battery5Bar,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${state.batteryLevel}%",
                        color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Einstellungen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        StatusChip(status = state.connectionStatus)
    }
}

@Composable
private fun StatusChip(status: ConnectionStatus) {
    val (label, color) = when (status) {
        ConnectionStatus.Disconnected -> "Getrennt" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionStatus.Scanning -> "Suche..." to Color(0xFFFFA726)
        ConnectionStatus.Connecting -> "Verbinde..." to Color(0xFFFFA726)
        ConnectionStatus.Connected -> "Verbunden" to Color(0xFF66BB6A)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// --- Disconnected ---

@Composable
private fun DisconnectedView(onStartScan: () -> Unit, errorMessage: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Kein Gerät verbunden",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Starte einen Scan, um dein\nGarmin HRM-200 zu finden",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartScan,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan starten", fontWeight = FontWeight.SemiBold)
        }
    }
}

// --- Scanning ---

@SuppressLint("MissingPermission")
@Composable
private fun ScanningView(
    scanResults: List<ScanResult>,
    onDeviceSelected: (ScanResult) -> Unit,
    onStopScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Scanning indicator
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Gefundene Geräte",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (scanResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Suche nach Heart Rate Monitoren...\nStelle sicher, dass dein HRM-200 aktiv ist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scanResults) { result ->
                    DeviceCard(result = result, onClick = { onDeviceSelected(result) })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onStopScan,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Scan stoppen")
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceCard(result: ScanResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Watch,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.device.name ?: "Unbekanntes Gerät",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = result.device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${result.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Connecting ---

@Composable
private fun ConnectingView(deviceName: String?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Verbinde mit",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = deviceName ?: "...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abbrechen")
        }
    }
}

// --- Connected: Heart Rate Display ---

@Composable
private fun ConnectedView(
    heartRate: Int,
    history: List<Int>,
    deviceName: String?,
    workoutState: WorkoutState,
    onDisconnect: () -> Unit,
    onStartWorkout: (longterm: Boolean) -> Unit,
    onStopWorkout: () -> Unit,
    onToggleScreenOn: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Animated beating heart (smaller when workout active)
        PulsingHeart(bpm = heartRate)

        Spacer(modifier = Modifier.height(16.dp))

        // BPM Display
        Text(
            text = if (heartRate > 0) "$heartRate" else "--",
            fontSize = if (workoutState.isActive) 56.sp else 72.sp,
            fontWeight = FontWeight.Bold,
            color = if (workoutState.isActive) {
                Color(workoutState.currentZone.colorHex)
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Text(
            text = "BPM",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Workout panel (when active) or regular stats
        if (workoutState.isActive) {
            WorkoutPanel(
                workoutState = workoutState,
                currentHr = heartRate,
                onToggleScreenOn = onToggleScreenOn
            )
        } else {
            // Min / Max / Avg
            if (history.isNotEmpty()) {
                HrStatsRow(history)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mini heart rate chart
            if (history.size > 1) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    HeartRateChart(
                        data = history,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Workout Start/Stop
        WorkoutControls(
            workoutState = workoutState,
            onStartWorkout = onStartWorkout,
            onStopWorkout = onStopWorkout
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Device info + Disconnect (only when not in workout)
        if (!workoutState.isActive) {
            Text(
                text = deviceName ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDisconnect,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.BluetoothDisabled, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Trennen")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// --- Pulsing Heart Animation ---

@Composable
private fun PulsingHeart(bpm: Int) {
    // Calculate animation duration from BPM (one beat cycle)
    val durationMs = if (bpm > 0) (60_000 / bpm) else 1000

    val scale by rememberInfiniteTransition(label = "heartbeat").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                this.durationMillis = durationMs
                1.0f at 0
                1.2f at (durationMs * 0.1).toInt()   // systole
                1.0f at (durationMs * 0.25).toInt()
                1.1f at (durationMs * 0.35).toInt()   // diastole
                1.0f at (durationMs * 0.5).toInt()
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "heartScale"
    )

    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = "Heart",
        modifier = Modifier
            .size(80.dp)
            .scale(scale),
        tint = MaterialTheme.colorScheme.primary
    )
}

// --- Stats Row ---

@Composable
private fun HrStatsRow(history: List<Int>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(label = "Min", value = "${history.min()}")
        StatItem(label = "Avg", value = "${history.average().toInt()}")
        StatItem(label = "Max", value = "${history.max()}")
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Mini Chart ---

@Composable
private fun HeartRateChart(data: List<Int>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val minHr = (data.min() - 5).coerceAtLeast(40).toFloat()
        val maxHr = (data.max() + 5).toFloat()
        val range = (maxHr - minHr).coerceAtLeast(1f)

        val stepX = size.width / (data.size - 1)

        // Draw line
        val path = Path()
        data.forEachIndexed { index, hr ->
            val x = index * stepX
            val y = size.height - ((hr - minHr) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        // Draw gradient fill below line
        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.3f),
                    primaryColor.copy(alpha = 0.0f)
                )
            )
        )

        // Draw current value dot
        val lastX = (data.size - 1) * stepX
        val lastY = size.height - ((data.last() - minHr) / range) * size.height
        drawCircle(
            color = primaryColor,
            radius = 6f,
            center = Offset(lastX, lastY)
        )
    }
}