package com.sih.wearable.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sih.wearable.R
import com.sih.wearable.data.AppDb
import com.sih.wearable.data.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class BleForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "BleService"

    // Replace with your device names and characteristic UUIDs
    private val WRIST_NAME = "ND-WRIST"
    private val CHEST_NAME = "ND-CHEST"
    private val SERVICE_UUID = UUID.fromString("0000181D-0000-1000-8000-00805F9B34FB")
    private val CHAR_TELEM_UUID = UUID.fromString("00002A58-0000-1000-8000-00805F9B34FB")

    private var btAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattWrist: BluetoothGatt? = null
    private var gattChest: BluetoothGatt? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        scanner = btAdapter?.bluetoothLeScanner
        startInForeground()
        startScan()
    }

    private fun startInForeground() {
        val chId = "ble_foreground"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(chId, "BLE Collector", NotificationManager.IMPORTANCE_LOW))
        }
        val notif: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("Collecting sensor data")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notif)
    }

    private fun startScan() {
        val cb = object: ScanCallback(){
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (name == WRIST_NAME && gattWrist == null) {
                    gattWrist = result.device.connectGatt(this@BleForegroundService, true, gattCallbackWrist, BluetoothDevice.TRANSPORT_LE)
                }
                if (name == CHEST_NAME && gattChest == null) {
                    gattChest = result.device.connectGatt(this@BleForegroundService, true, gattCallbackChest, BluetoothDevice.TRANSPORT_LE)
                }
            }
        }
        scanner?.startScan(cb)
    }

    private val gattCallbackWrist = object: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) { gatt.discoverServices() }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val c = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_TELEM_UUID)
            if (c != null) {
                gatt.setCharacteristicNotification(c, true)
                val desc = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_TELEM_UUID) {
                handlePacket("wrist", characteristic.value)
            }
        }
    }

    private val gattCallbackChest = object: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) { gatt.discoverServices() }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val c = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_TELEM_UUID)
            if (c != null) {
                gatt.setCharacteristicNotification(c, true)
                val desc = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_TELEM_UUID) {
                handlePacket("chest", characteristic.value)
            }
        }
    }

    private fun handlePacket(src: String, bytes: ByteArray) {
        try {
            val json = JSONObject(String(bytes))
            val did = json.optString("did", if (src=="wrist") "W01" else "C01")
            val ts = json.optLong("ts", System.currentTimeMillis())
            val batt = json.optDouble("batt", 0.0)
            val vitals = json.optJSONObject("vitals")
            val hr = vitals?.optDouble("hr", 0.0) ?: 0.0
            val spo2 = vitals?.optDouble("spo2", 0.0) ?: 0.0
            val temp = json.optDouble("temp", 0.0)

            val dao = AppDb.get(this).telemDao()
            CoroutineScope(Dispatchers.IO).launch {
                dao.insert(Telemetry(uid="user_01", team="default", did=did, src=src, ts=ts, batt=batt, hr=hr, spo2=spo2, temp=temp, raw=json.toString()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bad packet: ${e.message}")
        }
    }
}
