package com.github.reygnn.pulse.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.reygnn.pulse.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground Service that keeps the BLE connection alive when the app
 * is in the background or the screen is off.
 *
 * Shows a persistent notification with the current heart rate.
 */
class HeartRateService : Service() {

    companion object {
        private const val TAG = "HeartRateService"
        private const val CHANNEL_ID = "heart_rate_channel"
        private const val NOTIFICATION_ID = 1
    }

    private val binder = LocalBinder()
    val hrManager by lazy { HeartRateManager(applicationContext) }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    inner class LocalBinder : Binder() {
        fun getService(): HeartRateService = this@HeartRateService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()

        // Observe HR changes and update notification
        serviceScope.launch {
            hrManager.state.collectLatest { state ->
                if (state.connectionStatus == HeartRateManager.ConnectionStatus.Connected) {
                    updateNotification(state.heartRate, state.deviceName)
                }
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    fun startForegroundWithNotification() {
        val notification = buildNotification(0, null)
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        Log.d(TAG, "Foreground service started")
    }

    fun stopForegroundState() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Foreground state stopped, notification removed")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        hrManager.disconnect()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Herzfrequenz",
            NotificationManager.IMPORTANCE_LOW // Low = no sound, still visible
        ).apply {
            description = "Zeigt die aktuelle Herzfrequenz an"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(heartRate: Int, deviceName: String?): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (heartRate > 0) {
            "$heartRate BPM — ${deviceName ?: "HRM"}"
        } else {
            "Verbinde mit ${deviceName ?: "HRM"}..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pulse")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Built-in icon as fallback
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(heartRate: Int, deviceName: String?) {
        val notification = buildNotification(heartRate, deviceName)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}