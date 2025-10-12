package com.sih.wearable.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.sih.wearable.R
import com.sih.wearable.ble.BleForegroundService
import com.sih.wearable.data.AppDb
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvLast: TextView

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)
        tvLast = findViewById(R.id.tvLast)
        val btnStart: Button = findViewById(R.id.btnStart)
        val btnSync: Button = findViewById(R.id.btnSync)

        permLauncher.launch(arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ))

        btnStart.setOnClickListener {
            val intent = Intent(this, BleForegroundService::class.java)
            startForegroundService(intent)
            tvStatus.text = "Scanning & Connecting..."
        }

        btnSync.setOnClickListener {
            lifecycleScope.launch {
                com.sih.wearable.net.SyncWorker.triggerOneShot(this@MainActivity)
            }
        }

        lifecycleScope.launch {
            val dao = AppDb.get(this@MainActivity).telemDao()
            val last = dao.getLatest()
            tvLast.text = "Last packet: ${last?.ts ?: "-"}"
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if(!adapter.isEnabled){
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}
