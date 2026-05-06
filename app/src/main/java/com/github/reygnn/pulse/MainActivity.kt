package com.github.reygnn.pulse

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.pulse.ui.screens.HeartRateScreen
import com.github.reygnn.pulse.ui.screens.SettingsSheet
import com.github.reygnn.pulse.ui.theme.PulseTheme
import com.github.reygnn.pulse.viewmodel.HeartRateViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PulseTheme {
                Scaffold { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        PulseApp()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PulseApp() {
    val viewModel: HeartRateViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // BLE permissions depending on Android version
    val permissions = remember {
        buildList {

            add(android.Manifest.permission.BLUETOOTH_SCAN)
            add(android.Manifest.permission.BLUETOOTH_CONNECT)
            add(android.Manifest.permission.POST_NOTIFICATIONS)
            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    if (permissionState.allPermissionsGranted) {
        val workoutState by viewModel.workoutState.collectAsStateWithLifecycle()
        val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
        val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

        // Show settings on first launch if profile not set
        LaunchedEffect(Unit) {
            if (!viewModel.isProfileSet) {
                viewModel.openSettings()
            }
        }

        val activity = LocalActivity.current
        LaunchedEffect(workoutState.keepScreenOn, workoutState.isActive) {
            val window = activity?.window ?: return@LaunchedEffect
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (workoutState.keepScreenOn && workoutState.isActive) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply {
                    screenBrightness = 0.01f
                }
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        HeartRateScreen(
            state = state,
            workoutState = workoutState,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onDeviceSelected = viewModel::connectToDevice,
            onDisconnect = viewModel::disconnect,
            onStartWorkout = { longterm -> viewModel.startWorkout(longterm) },
            onStopWorkout = viewModel::stopWorkout,
            onDismissSummary = viewModel::dismissSummary,
            onOpenSettings = viewModel::openSettings,
            onToggleScreenOn = viewModel::toggleKeepScreenOn
        )

        // Settings dialog
        if (showSettings) {
            SettingsSheet(
                profile = userProfile,
                onSave = viewModel::saveProfile,
                onDismiss = viewModel::closeSettings
            )
        }
    } else {
        // Permission request screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Berechtigungen benötigt",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Um dein Garmin HRM-200 zu finden und zu verbinden,\nbenötigt die App Bluetooth- und Standort-Berechtigungen.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { permissionState.launchMultiplePermissionRequest() }
            ) {
                Text("Berechtigungen erteilen")
            }
        }
    }
}