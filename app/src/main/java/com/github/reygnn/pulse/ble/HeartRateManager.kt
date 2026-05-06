package com.github.reygnn.pulse.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class HeartRateManager(private val context: Context) {

    companion object {
        private const val TAG = "HeartRateManager"
        private const val RECONNECT_DELAY_MS = 2000L

        // Standard Bluetooth SIG UUIDs
        val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    data class HrState(
        val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
        val heartRate: Int = 0,
        val batteryLevel: Int = -1,
        val deviceName: String? = null,
        val scanResults: List<ScanResult> = emptyList(),
        val errorMessage: String? = null
    )

    enum class ConnectionStatus {
        Disconnected, Scanning, Connecting, Connected
    }

    private val _state = MutableStateFlow(HrState())
    val state: StateFlow<HrState> = _state.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var userRequestedDisconnect = false

    // --- Scanning ---

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter ?: run {
            _state.value = _state.value.copy(errorMessage = "Bluetooth nicht verfügbar")
            return
        }

        if (!adapter.isEnabled) {
            _state.value = _state.value.copy(errorMessage = "Bitte Bluetooth aktivieren")
            return
        }

        _state.value = _state.value.copy(
            connectionStatus = ConnectionStatus.Scanning,
            scanResults = emptyList(),
            errorMessage = null
        )

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        adapter.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "BLE scan started")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_state.value.connectionStatus == ConnectionStatus.Scanning) {
            _state.value = _state.value.copy(connectionStatus = ConnectionStatus.Disconnected)
        }
        Log.d(TAG, "BLE scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val currentResults = _state.value.scanResults.toMutableList()
            if (currentResults.none { it.device.address == result.device.address }) {
                currentResults.add(result)
                _state.value = _state.value.copy(scanResults = currentResults)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = _state.value.copy(
                connectionStatus = ConnectionStatus.Disconnected,
                errorMessage = "Scan fehlgeschlagen (Code: $errorCode)"
            )
        }
    }

    // --- Connecting ---

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        // Cancel any pending auto-reconnect from a previous device.
        handler.removeCallbacksAndMessages(null)
        stopScan()
        userRequestedDisconnect = false
        _state.value = _state.value.copy(
            connectionStatus = ConnectionStatus.Connecting,
            deviceName = device.name ?: device.address,
            errorMessage = null
        )

        bluetoothGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(TAG, "Connecting to ${device.name ?: device.address}")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userRequestedDisconnect = true
        handler.removeCallbacksAndMessages(null)
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        _state.value = HrState()
        Log.d(TAG, "Disconnected by user")
    }

    // --- GATT Callback ---

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _state.value = _state.value.copy(connectionStatus = ConnectionStatus.Connected)
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server, status: $status")
                    val deviceAddress = gatt.device.address
                    gatt.close()
                    bluetoothGatt = null

                    if (!userRequestedDisconnect) {
                        _state.value = _state.value.copy(
                            connectionStatus = ConnectionStatus.Connecting,
                            heartRate = 0
                        )
                        Log.d(TAG, "Auto-reconnecting in ${RECONNECT_DELAY_MS}ms...")
                        handler.postDelayed({
                            val adapter = bluetoothAdapter ?: return@postDelayed
                            val device = adapter.getRemoteDevice(deviceAddress)
                            connectToDevice(device)
                        }, RECONNECT_DELAY_MS)
                    } else {
                        _state.value = _state.value.copy(
                            connectionStatus = ConnectionStatus.Disconnected,
                            heartRate = 0
                        )
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(errorMessage = "Service-Erkennung fehlgeschlagen")
                return
            }

            // Enable HR notifications
            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            val hrCharacteristic = hrService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

            if (hrCharacteristic == null) {
                _state.value = _state.value.copy(errorMessage = "Heart Rate Service nicht gefunden")
                return
            }

            gatt.setCharacteristicNotification(hrCharacteristic, true)

            val descriptor = hrCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor?.let {
                gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }

            Log.d(TAG, "Heart Rate notifications enabled")
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Descriptor write failed: $status")
                return
            }

            // After HR descriptor is written → read battery level
            if (descriptor.characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                Log.d(TAG, "HR Notifications aktiviert, lese Battery...")
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)
                if (batteryChar != null) {
                    gatt.readCharacteristic(batteryChar)
                } else {
                    Log.d(TAG, "Battery Service nicht verfügbar")
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                val level = value[0].toInt() and 0xFF
                _state.value = _state.value.copy(batteryLevel = level)
                Log.d(TAG, "Battery Level: $level%")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val heartRate = parseHeartRate(value)
                _state.value = _state.value.copy(heartRate = heartRate)
                Log.d(TAG, "Heart Rate: $heartRate bpm")
            }
        }
    }

    private fun parseHeartRate(data: ByteArray): Int {
        if (data.isEmpty()) return 0

        val flags = data[0].toInt()
        return if (flags and 0x01 == 0) {
            data[1].toInt() and 0xFF
        } else {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        }
    }
}