package com.vanshika.nimbus

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vanshika.nimbus.ui.theme.NimbusAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothService: BluetoothService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothScan()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothService = BluetoothService(this)

        setContent {
            NimbusAppTheme {
                HealthMonitorApp(
                    bluetoothAdapter = bluetoothAdapter,
                    bluetoothService = bluetoothService,
                    onConnectClick = ::checkPermissionsAndScan
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.disconnect()
    }

    private fun checkPermissionsAndScan() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            startBluetoothScan()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun startBluetoothScan() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        val esp32Devices = pairedDevices.filter { device ->
            device.name?.contains("ESP32", ignoreCase = true) == true
        }

        if (esp32Devices.isNotEmpty()) {
            // Connect to the first found ESP32 device
            bluetoothService.connectToDevice(esp32Devices.first())
            Toast.makeText(this, "Connecting to ${esp32Devices.first().name}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No ESP32 devices found. Make sure your ESP32 is paired.", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMonitorApp(
    bluetoothAdapter: BluetoothAdapter?,
    bluetoothService: BluetoothService?,
    onConnectClick: () -> Unit
) {
    var status by remember { mutableStateOf("Ready to connect") }
    var oxygenLevel by remember { mutableStateOf("--") }
    var heartRate by remember { mutableStateOf("--") }
    var isScanning by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf("Never") }
    var connectedDevice by remember { mutableStateOf("None") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(bluetoothService) {
        bluetoothService?.let { service ->
            launch {
                service.connectionState.collectLatest { connectionState ->
                    status = connectionState
                    if (connectionState.startsWith("Connected")) {
                        connectedDevice = service.getConnectedDeviceName()
                    } else {
                        connectedDevice = "None"
                    }
                }
            }
            launch {
                service.healthData.collectLatest { healthData ->
                    healthData?.let {
                        oxygenLevel = String.format("%.1f", it.oxygenLevel)
                        heartRate = it.heartRate.toString()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nimbus Health Monitor") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Status: $status",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Device: $connectedDevice",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isScanning) {
                        Text(
                            text = "Scanning for ESP32 devices...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Health Data Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Oxygen Level: ${oxygenLevel}%",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Heart Rate: ${heartRate} BPM",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Connection Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connect Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isScanning = true
                            onConnectClick()
                            delay(3000) // UI feedback for scanning
                            isScanning = false
                        }
                    },
                    enabled = !isScanning && bluetoothAdapter != null && !status.startsWith("Connected"),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            isScanning -> "Scanning..."
                            bluetoothAdapter == null -> "No BT"
                            status.startsWith("Connected") -> "Connected"
                            else -> "Connect"
                        }
                    )
                }

                // Disconnect Button
                Button(
                    onClick = {
                        bluetoothService?.disconnect()
                        oxygenLevel = "--"
                        heartRate = "--"
                    },
                    enabled = status.startsWith("Connected"),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disconnect")
                }
            }

            // Sync Button
            Button(
                onClick = {
                    lastSync = "Just now"
                    Toast.makeText(context, "Data synced with server", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sync with Server")
            }

            // Demo Data Button (for testing without ESP32)
            Button(
                onClick = {
                    oxygenLevel = (95..99).random().toString()
                    heartRate = (65..85).random().toString()
                    status = "Demo data loaded"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Demo Data")
            }

            // Status Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Last sync: $lastSync",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (bluetoothAdapter == null) {
                    Text(
                        text = "Bluetooth not supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!bluetoothAdapter.isEnabled) {
                    Text(
                        text = "Bluetooth is disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HealthMonitorPreview() {
    NimbusAppTheme {
        HealthMonitorApp(bluetoothAdapter = null, bluetoothService = null, onConnectClick = {})
    }
}
