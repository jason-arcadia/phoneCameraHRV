package com.example.phonecamerahrv

import android.Manifest
import android.accounts.AccountManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: HRVViewModel by viewModels()
    private var cameraManager: CameraManager? = null

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.CAMERA] == true) startMeasurement()
        // GET_ACCOUNTS denial is handled gracefully in getGoogleAccount()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Observe save events: write file then stop camera
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveEvent.collect { rrData ->
                    saveRRFile(rrData)
                    stopMeasurement()
                }
            }
        }

        setContent {
            val rmssd by viewModel.rmssd.collectAsStateWithLifecycle()
            val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
            val lastRR by viewModel.lastRR.collectAsStateWithLifecycle()
            val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
            val waveform by viewModel.waveform.collectAsStateWithLifecycle()
            val isStable by viewModel.isStable.collectAsStateWithLifecycle()
            val measurementSeconds by viewModel.measurementSeconds.collectAsStateWithLifecycle()

            App(
                rmssd = rmssd,
                heartRate = heartRate,
                lastRR = lastRR,
                isRunning = isRunning,
                waveform = waveform,
                isStable = isStable,
                measurementSeconds = measurementSeconds,
                onToggle = {
                    if (isRunning) stopMeasurement()
                    else requestPermissions.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.GET_ACCOUNTS)
                    )
                }
            )
        }
    }

    private fun startMeasurement() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager = CameraManager(this, this) { intensity, timestampMs ->
            viewModel.onFrame(intensity, timestampMs)
        }
        cameraManager?.start()
        viewModel.setRunning(true)
    }

    private fun stopMeasurement() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager?.stop()
        cameraManager = null
        viewModel.setRunning(false)
    }

    private fun saveRRFile(rrData: List<Double>) {
        val account = getGoogleAccount()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = account.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val filename = "${safeName}_${timestamp}.txt"

        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, filename)
        file.writeText(rrData.joinToString("\n"))

        Toast.makeText(this, "Saved ${rrData.size} RR intervals → $filename", Toast.LENGTH_LONG).show()
    }

    private fun getGoogleAccount(): String {
        return try {
            AccountManager.get(this)
                .getAccountsByType("com.google")
                .firstOrNull()?.name ?: "unknown"
        } catch (e: SecurityException) {
            "unknown"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stop()
    }
}
