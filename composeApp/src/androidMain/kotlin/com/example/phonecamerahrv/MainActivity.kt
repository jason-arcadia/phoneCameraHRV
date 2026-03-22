package com.example.phonecamerahrv

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private val viewModel: HRVViewModel by viewModels()
    private var cameraManager: CameraManager? = null

    private val prefs by lazy { getSharedPreferences("hrv_prefs", MODE_PRIVATE) }
    private var userName by mutableStateOf("")
    private var coachEmail by mutableStateOf("")

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMeasurement()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        userName = prefs.getString("user_name", "") ?: ""
        coachEmail = prefs.getString("coach_email", "") ?: ""

        // Observe save events: compute scores, save file, email coach, stop camera
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveEvent.collect { rrData ->
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    val file = saveRRFile(rrData, timestamp)
                    if (coachEmail.isNotBlank()) sendToCoach(file, timestamp)

                    // Compute HRV scores against personal baseline
                    val rmssd = viewModel.rmssd.value
                    val hr = viewModel.heartRate.value
                    val storedBaseline = prefs.getFloat("baseline_rmssd", 0f).toDouble()
                    val baseline = if (storedBaseline < 10.0) rmssd else storedBaseline
                    val scores = calculateHRVScores(rmssd, hr, baseline)
                    viewModel.setScores(scores)

                    // Update personal baseline with exponential moving average
                    val newBaseline = if (storedBaseline < 10.0) rmssd else 0.7 * storedBaseline + 0.3 * rmssd
                    prefs.edit().putFloat("baseline_rmssd", newBaseline.toFloat()).apply()

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
            val isFingerDetected by viewModel.isFingerDetected.collectAsStateWithLifecycle()
            val validCount by viewModel.validCount.collectAsStateWithLifecycle()
            val rejectedCount by viewModel.rejectedCount.collectAsStateWithLifecycle()
            val latestScores by viewModel.latestScores.collectAsStateWithLifecycle()

            App(
                rmssd = rmssd,
                heartRate = heartRate,
                lastRR = lastRR,
                isRunning = isRunning,
                waveform = waveform,
                isStable = isStable,
                measurementSeconds = measurementSeconds,
                isFingerDetected = isFingerDetected,
                validCount = validCount,
                rejectedCount = rejectedCount,
                userName = userName,
                coachEmail = coachEmail,
                latestScores = latestScores,
                onUserNameChange = { userName = it; prefs.edit().putString("user_name", it).apply() },
                onCoachEmailChange = { coachEmail = it; prefs.edit().putString("coach_email", it).apply() },
                onToggle = {
                    if (isRunning) stopMeasurement()
                    else requestCameraPermission.launch(Manifest.permission.CAMERA)
                },
                onDismissResults = { viewModel.clearScores() },
                cameraPreview = {
                    CameraPreviewComposable(
                        onSurfaceProviderReady = { cameraManager?.setSurfaceProvider(it) }
                    )
                }
            )
        }
    }

    private fun startMeasurement() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager = CameraManager(this, this) { red, yAvg, timestampMs ->
            viewModel.onFrame(red, yAvg, timestampMs)
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

    private fun saveRRFile(rrData: List<Double>, timestamp: String): File {
        val safeName = userName.ifBlank { "unknown" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileTimestamp = timestamp.replace(Regex("[: ]"), "-")
        val filename = "${safeName}_${fileTimestamp}.txt"
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, filename)
        file.writeText(rrData.joinToString("\n"))
        Toast.makeText(this, "已儲存 ${rrData.size} 個 RR 區間 → $filename", Toast.LENGTH_LONG).show()
        return file
    }

    private fun sendToCoach(file: File, timestamp: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val name = userName.ifBlank { "unknown" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(coachEmail))
            putExtra(Intent.EXTRA_SUBJECT, "HRV 測量報告 — $name — $timestamp")
            putExtra(Intent.EXTRA_TEXT, "附件為 60 秒穩定窗口的 RR 區間數據。")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "傳送給教練"))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stop()
    }
}
