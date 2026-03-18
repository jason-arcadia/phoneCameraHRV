package com.example.phonecamerahrv

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val viewModel: HRVViewModel by viewModels()
    private var cameraManager: CameraManager? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMeasurement()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val rmssd by viewModel.rmssd.collectAsStateWithLifecycle()
            val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
            val lastRR by viewModel.lastRR.collectAsStateWithLifecycle()
            val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
            val waveform by viewModel.waveform.collectAsStateWithLifecycle()

            App(
                rmssd = rmssd,
                heartRate = heartRate,
                lastRR = lastRR,
                isRunning = isRunning,
                waveform = waveform,
                onToggle = {
                    if (isRunning) stopMeasurement()
                    else requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }

    private fun startMeasurement() {
        cameraManager = CameraManager(this, this) { intensity, timestampMs ->
            viewModel.onFrame(intensity, timestampMs)
        }
        cameraManager?.start()
        viewModel.setRunning(true)
    }

    private fun stopMeasurement() {
        cameraManager?.stop()
        cameraManager = null
        viewModel.setRunning(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stop()
    }
}
