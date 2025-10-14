package com.vanshika.nimbus

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothService(private val context: Context) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null

    // States for UI
    private val _connectionState = MutableStateFlow<String>("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val _healthData = MutableStateFlow<HealthData?>(null)
    val healthData: StateFlow<HealthData?> = _healthData

    // UUIDs for your ESP32 Bluetooth service
    companion object {
        // Main Service UUID
        val HEALTH_SERVICE_UUID: UUID = UUID.fromString("0000181D-0000-1000-8000-00805F9B34FB")

        // Characteristic UUIDs for wrist and chest data
        val WRIST_DATA_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A58-0000-1000-8000-00805F9B34FB")
        // CHEST_DATA_CHARACTERISTIC_UUID needs to be unique
        val CHEST_DATA_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A59-0000-1000-8000-00805F9B34FB") // Made this unique

        // Standard Bluetooth descriptor for enabling notifications
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    data class HealthData(
        val oxygenLevel: Float = 0f,
        val heartRate: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = "Connecting to ${device.name}..."
        connectedDevice = device
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        connectedDevice = null
        _connectionState.value = "Disconnected"
        _healthData.value = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = "Connected - Discovering services..."
                    Log.d("BluetoothService", "Connected to GATT server")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = "Disconnected"
                    _healthData.value = null
                    Log.d("BluetoothService", "Disconnected from GATT server")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothService", "Services discovered successfully")
                _connectionState.value = "Services discovered - Setting up notifications..."

                // Enable notifications for both wrist and chest characteristics
                enableCharacteristicNotifications(gatt)
            } else {
                _connectionState.value = "Service discovery failed: $status"
                Log.e("BluetoothService", "Service discovery failed: $status")
            }
        }

        // Updated onCharacteristicChanged callback for Android 13+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            processCharacteristicData(characteristic.uuid, value)
        }

        // Deprecated callback for older devices
        @Deprecated("Used for older devices")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                characteristic.value?.let { value ->
                    processCharacteristicData(characteristic.uuid, value)
                }
            }
        }

        // Updated onCharacteristicRead callback for Android 13+
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                processCharacteristicData(characteristic.uuid, value)
            }
        }

        // Deprecated callback for older devices
        @Deprecated("Used for older devices")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    characteristic.value?.let { value ->
                        processCharacteristicData(characteristic.uuid, value)
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothService", "Notification enabled for characteristic: ${descriptor.characteristic.uuid}")
            } else {
                Log.e("BluetoothService", "Descriptor write failed for ${descriptor.characteristic.uuid} with status $status")
            }
        }
    }

    private fun processCharacteristicData(uuid: UUID, data: ByteArray) {
        when (uuid) {
            WRIST_DATA_CHARACTERISTIC_UUID -> {
                parseHealthData(data, "wrist")
            }
            CHEST_DATA_CHARACTERISTIC_UUID -> {
                parseHealthData(data, "chest")
            }
            else -> {
                Log.d("BluetoothService", "Unknown characteristic: $uuid")
            }
        }
    }

    private fun enableCharacteristicNotifications(gatt: BluetoothGatt) {
        try {
            val service = gatt.getService(HEALTH_SERVICE_UUID)
            if (service == null) {
                Log.e("BluetoothService", "Health service not found")
                _connectionState.value = "Health service not available"
                return
            }

            Log.d("BluetoothService", "Found health service, setting up characteristics...")

            // Enable notifications for wrist data characteristic
            val wristChar = service.getCharacteristic(WRIST_DATA_CHARACTERISTIC_UUID)
            if (wristChar != null) {
                enableNotificationsForCharacteristic(gatt, wristChar, "wrist")
            } else {
                Log.e("BluetoothService", "Wrist characteristic not found")
            }

            // Enable notifications for chest data characteristic
            val chestChar = service.getCharacteristic(CHEST_DATA_CHARACTERISTIC_UUID)
            if (chestChar != null) {
                enableNotificationsForCharacteristic(gatt, chestChar, "chest")
            } else {
                Log.e("BluetoothService", "Chest characteristic not found")
            }

            _connectionState.value = "Ready - Waiting for data..."

        } catch (e: Exception) {
            Log.e("BluetoothService", "Error setting up notifications: ${e.message}")
            _connectionState.value = "Error setting up notifications"
        }
    }

    private fun enableNotificationsForCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        charName: String
    ) {
        val properties = characteristic.properties
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
                Log.d("BluetoothService", "Enabling notifications for $charName characteristic...")
            } else {
                Log.e("BluetoothService", "No descriptor found for $charName characteristic")
            }
        } else {
            Log.e("BluetoothService", "$charName characteristic doesn't support notifications")
        }
    }

    private fun parseHealthData(data: ByteArray, source: String) {
        if (data.isEmpty()) {
            Log.w("BluetoothService", "Received empty data from $source")
            return
        }

        try {
            val dataString = String(data, Charsets.UTF_8)
            if (dataString.contains("O2:") && dataString.contains("HR:")) {
                val oxygen = dataString.substringAfter("O2:").substringBefore(",").trim().toFloatOrNull()
                val heartRate = dataString.substringAfter("HR:").trim().toIntOrNull()

                if (oxygen != null && heartRate != null) {
                    updateHealthData(oxygen, heartRate, "$source (string)")
                    return
                }
            }

            when (data.size) {
                2 -> {
                    val oxygen = data[0].toUByte().toFloat()
                    val heartRate = data[1].toUByte().toInt()
                    updateHealthData(oxygen, heartRate, "$source (2-byte)")
                }
                4 -> {
                    val oxygen = ((data[0].toInt() and 0xFF) or (data[1].toInt() and 0xFF shl 8)).toFloat()
                    val heartRate = (data[2].toInt() and 0xFF) or (data[3].toInt() and 0xFF shl 8)
                    updateHealthData(oxygen, heartRate, "$source (4-byte)")
                }
                else -> {
                    Log.w("BluetoothService", "Unsupported data size from $source: ${data.size} bytes. Data as string: $dataString")
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothService", "Error parsing data from $source: ${e.message}")
        }
    }

    private fun updateHealthData(oxygen: Float, heartRate: Int, source: String) {
        Log.d("BluetoothService", "New data from $source - O2: $oxygen%, HR: $heartRate BPM")

        val currentData = _healthData.value
        if (currentData?.oxygenLevel != oxygen || currentData?.heartRate != heartRate) {
            val newData = HealthData(
                oxygenLevel = oxygen,
                heartRate = heartRate,
                timestamp = System.currentTimeMillis()
            )
            _healthData.value = newData
        }
    }

    fun getConnectedDeviceName(): String {
        return connectedDevice?.name ?: "No device connected"
    }

    fun readCharacteristics() {
        bluetoothGatt?.let { gatt ->
            try {
                val service = gatt.getService(HEALTH_SERVICE_UUID)
                service?.let {
                    // Read wrist characteristic
                    val wristChar = it.getCharacteristic(WRIST_DATA_CHARACTERISTIC_UUID)
                    wristChar?.let { characteristic ->
                        gatt.readCharacteristic(characteristic)
                    }

                    // Read chest characteristic
                    val chestChar = it.getCharacteristic(CHEST_DATA_CHARACTERISTIC_UUID)
                    chestChar?.let { characteristic ->
                        gatt.readCharacteristic(characteristic)
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothService", "Error reading characteristics: ${e.message}")
            }
        }
    }
}
