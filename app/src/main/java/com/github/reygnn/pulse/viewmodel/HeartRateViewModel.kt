package com.github.reygnn.pulse.viewmodel

import android.app.Application
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.reygnn.pulse.ble.HeartRateManager
import com.github.reygnn.pulse.ble.HeartRateService
import com.github.reygnn.pulse.workout.HrSample
import com.github.reygnn.pulse.workout.HrZone
import com.github.reygnn.pulse.workout.UserProfile
import com.github.reygnn.pulse.workout.UserProfileStore
import com.github.reygnn.pulse.workout.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HeartRateViewModel"
    }

    private val _service = MutableStateFlow<HeartRateService?>(null)

    // Keep last 60 HR readings for the live chart
    private val _history = mutableListOf<Int>()

    // --- User Profile ---
    private val profileStore = UserProfileStore(application.applicationContext)
    private val _userProfile = MutableStateFlow(profileStore.load())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    val isProfileSet: Boolean
        get() = profileStore.isProfileSet

    // --- Settings UI ---
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    // --- Workout State ---
    private val _workoutState = MutableStateFlow(WorkoutState())
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private var workoutStartMs: Long = 0L
    private val workoutSamples = mutableListOf<HrSample>()
    private var lastSampleMs: Long = 0L

    // UI state derived from the service's HeartRateManager
    val uiState: StateFlow<UiState> = _service
        .flatMapLatest { service ->
            service?.hrManager?.state ?: flowOf(HeartRateManager.HrState())
        }
        .onEach { bleState ->
            if (_workoutState.value.isActive && bleState.heartRate > 0) {
                recordSample(bleState.heartRate)
            }
        }
        .map { bleState ->
            UiState(
                connectionStatus = bleState.connectionStatus,
                heartRate = bleState.heartRate,
                batteryLevel = bleState.batteryLevel,
                deviceName = bleState.deviceName,
                scanResults = bleState.scanResults,
                errorMessage = bleState.errorMessage,
                heartRateHistory = updateHistory(bleState.heartRate)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState()
        )

    private fun updateHistory(hr: Int): List<Int> {
        if (hr > 0) {
            _history.add(hr)
            if (_history.size > 120) _history.removeFirst()   // ca 1 Minute
        }
        return _history.toList()
    }

    private fun recordSample(hr: Int) {
        val now = System.currentTimeMillis()
        val elapsed = now - workoutStartMs

        // Langzeit: nur alle 5 Sekunden samplen
        val isLongterm = _workoutState.value.isLongterm
        if (isLongterm && (now - lastSampleMs) < 5000) {
            val profile = _userProfile.value
            _workoutState.value = _workoutState.value.copy(
                currentZone = HrZone.fromHeartRate(hr, profile.estimatedMaxHr),
                elapsedMs = elapsed
            )
            return
        }
        lastSampleMs = now

        workoutSamples.add(HrSample(timestampMs = elapsed, heartRate = hr))

        // _history is already maintained by updateHistory() in the uiState pipeline.

        val profile = _userProfile.value
        val session = WorkoutSession(
            startTimeMs = workoutStartMs,
            samples = workoutSamples.toList(),
            maxHr = profile.estimatedMaxHr,
            userProfile = profile
        )

        _workoutState.value = _workoutState.value.copy(
            currentSession = session,
            currentZone = HrZone.fromHeartRate(hr, profile.estimatedMaxHr),
            elapsedMs = elapsed
        )
    }

    // --- Workout Actions ---

    fun startWorkout(longterm: Boolean = false) {
        val profile = _userProfile.value
        workoutStartMs = System.currentTimeMillis()
        lastSampleMs = 0L
        workoutSamples.clear()
        _workoutState.value = WorkoutState(
            isActive = true,
            isLongterm = longterm,
            maxHr = profile.estimatedMaxHr,
            currentSession = WorkoutSession(
                startTimeMs = workoutStartMs,
                maxHr = profile.estimatedMaxHr,
                userProfile = profile
            )
        )
        Log.d(TAG, "Workout started (maxHr=${profile.estimatedMaxHr}, profile=${profile})")
    }

    fun stopWorkout() {
        val profile = _userProfile.value
        val finalSession = WorkoutSession(
            startTimeMs = workoutStartMs,
            samples = workoutSamples.toList(),
            maxHr = profile.estimatedMaxHr,
            userProfile = profile
        )
        _workoutState.value = _workoutState.value.copy(
            isActive = false,
            keepScreenOn = false,
            currentSession = finalSession,
            showSummary = true
        )
        Log.d(TAG, "Workout stopped. Samples: ${workoutSamples.size}")
    }

    fun dismissSummary() {
        _workoutState.value = _workoutState.value.copy(showSummary = false)
    }

    // --- Profile / Settings ---

    fun openSettings() {
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    fun saveProfile(profile: UserProfile) {
        profileStore.save(profile)
        _userProfile.value = profile
        _showSettings.value = false
        Log.d(TAG, "Profile saved: $profile (maxHr=${profile.estimatedMaxHr})")
    }

    // --- Service Binding ---

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as HeartRateService.LocalBinder).getService()
            _service.value = service
            Log.d(TAG, "Bound to HeartRateService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            Log.d(TAG, "Unbound from HeartRateService")
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, HeartRateService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // --- BLE Actions ---

    fun startScan() {
        _service.value?.hrManager?.startScan()
    }

    fun stopScan() {
        _service.value?.hrManager?.stopScan()
    }

    fun connectToDevice(result: ScanResult) {
        _service.value?.let { service ->
            val context = getApplication<Application>()
            val intent = Intent(context, HeartRateService::class.java)
            context.startForegroundService(intent)
            service.startForegroundWithNotification()
            service.hrManager.connectToDevice(result.device)
        }
    }

    fun disconnect() {
        if (_workoutState.value.isActive) stopWorkout()
        _history.clear()
        val service = _service.value ?: return
        service.hrManager.disconnect()
        // Drop the foreground state + notification but keep the binding alive,
        // so the user can immediately scan again without re-creating the service.
        service.stopForegroundState()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: Exception) { }
    }

    fun toggleKeepScreenOn() {
        _workoutState.value = _workoutState.value.copy(
            keepScreenOn = !_workoutState.value.keepScreenOn
        )
    }
}

data class UiState(
    val connectionStatus: HeartRateManager.ConnectionStatus = HeartRateManager.ConnectionStatus.Disconnected,
    val heartRate: Int = 0,
    val batteryLevel: Int = -1,
    val deviceName: String? = null,
    val scanResults: List<ScanResult> = emptyList(),
    val errorMessage: String? = null,
    val heartRateHistory: List<Int> = emptyList()
)

data class WorkoutState(
    val isActive: Boolean = false,
    val isLongterm: Boolean = false,
    val keepScreenOn: Boolean = false,
    val showSummary: Boolean = false,
    val maxHr: Int = 190,
    val currentZone: HrZone = HrZone.REST,
    val elapsedMs: Long = 0L,
    val currentSession: WorkoutSession? = null
)
