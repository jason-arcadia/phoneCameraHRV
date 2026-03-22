package com.example.phonecamerahrv

import android.Manifest
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

    private val prefs by lazy { getSharedPreferences("hrv_prefs", MODE_PRIVATE) }
    private val measurementStore by lazy { MeasurementStore(this) }

    // Auth state
    private var isSignedIn       by mutableStateOf(false)
    private var googleEmail      by mutableStateOf("")
    private var googleDisplayName by mutableStateOf("")

    // Settings
    private var userName   by mutableStateOf("")
    private var apiBaseUrl by mutableStateOf("")

    // Google Sign-In — must be created before onCreate returns
    private lateinit var signInHelper: GoogleSignInHelper

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startMeasurement() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Load persisted settings
        userName   = prefs.getString("user_name",    "") ?: ""
        apiBaseUrl = prefs.getString("api_base_url", "") ?: ""
        googleEmail       = prefs.getString("google_email", "") ?: ""
        googleDisplayName = prefs.getString("google_name",  "") ?: ""
        isSignedIn        = googleEmail.isNotBlank()

        // Set up Google Sign-In
        signInHelper = GoogleSignInHelper(
            activity  = this,
            onSuccess = { email, name -> onSignInSuccess(email, name) },
            onFailure = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )

        // If not signed in via prefs, check if there's a cached Google account
        if (!isSignedIn) {
            signInHelper.getLastSignedInAccount()?.let { account ->
                onSignInSuccess(account.email ?: "", account.displayName ?: "")
            }
        }

        // Observe save events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveEvent.collect { rrData ->
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    saveRRFile(rrData, timestamp)

                    val rmssd  = viewModel.rmssd.value
                    val hr     = viewModel.heartRate.value
                    val lastRR = viewModel.lastRR.value

                    // Persist reading and compute scores
                    measurementStore.save(MeasurementRecord(System.currentTimeMillis(), rmssd, hr, lastRR))
                    val baseline          = measurementStore.computeBaseline()
                    val currentAsBaseline = BaselineStats(rmssd, hr, lastRR, measurementStore.recordCount())
                    val scores            = calculateHRVScores(rmssd, hr, lastRR, baseline, currentAsBaseline)
                    viewModel.setScores(scores)

                    // Auto-upload to Supabase
                    val meanRr = if (rrData.isEmpty()) 0.0 else rrData.average()
                    if (googleEmail.isNotBlank()) {
                        lifecycleScope.launch {
                            // 1. Upload RR file to Storage; continue even if it fails
                            val rrFileResult = MeasurementApiClient.uploadRRFile(
                                email     = googleEmail,
                                timestamp = timestamp,
                                content   = rrData.joinToString("\n")
                            )
                            rrFileResult.onFailure { e ->
                                Toast.makeText(this@MainActivity,
                                    "RR 檔案上傳失敗：${e.message}", Toast.LENGTH_LONG).show()
                            }
                            val rrFileUrl = rrFileResult.getOrElse { "" }

                            // 2. Insert measurement row with file URL
                            MeasurementApiClient.upload(
                                email         = googleEmail,
                                timestamp     = timestamp,
                                rmssd         = rmssd,
                                heartRate     = hr,
                                peakToPeak    = lastRR,
                                meanRr        = meanRr,
                                stressScore   = scores.stress,
                                energyScore   = scores.energy,
                                recoveryScore = scores.recovery,
                                rrFileUrl     = rrFileUrl
                            ).onSuccess {
                                Toast.makeText(this@MainActivity,
                                    "測量結果成功上傳雲端", Toast.LENGTH_LONG).show()
                            }.onFailure { e ->
                                Toast.makeText(this@MainActivity,
                                    "上傳失敗：${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    stopMeasurement()
                }
            }
        }

        setContent {
            val rmssd              by viewModel.rmssd.collectAsStateWithLifecycle()
            val heartRate          by viewModel.heartRate.collectAsStateWithLifecycle()
            val lastRR             by viewModel.lastRR.collectAsStateWithLifecycle()
            val isRunning          by viewModel.isRunning.collectAsStateWithLifecycle()
            val waveform           by viewModel.waveform.collectAsStateWithLifecycle()
            val isStable           by viewModel.isStable.collectAsStateWithLifecycle()
            val measurementSeconds by viewModel.measurementSeconds.collectAsStateWithLifecycle()
            val isFingerDetected   by viewModel.isFingerDetected.collectAsStateWithLifecycle()
            val validCount         by viewModel.validCount.collectAsStateWithLifecycle()
            val rejectedCount      by viewModel.rejectedCount.collectAsStateWithLifecycle()
            val latestScores       by viewModel.latestScores.collectAsStateWithLifecycle()

            App(
                // Auth
                isSignedIn        = isSignedIn,
                googleEmail       = googleEmail,
                googleDisplayName = googleDisplayName,
                onGoogleSignIn    = { signInHelper.launch() },
                onSignOut         = { signOut() },
                // Measurement
                rmssd              = rmssd,
                heartRate          = heartRate,
                lastRR             = lastRR,
                isRunning          = isRunning,
                waveform           = waveform,
                isStable           = isStable,
                measurementSeconds = measurementSeconds,
                isFingerDetected   = isFingerDetected,
                validCount         = validCount,
                rejectedCount      = rejectedCount,
                latestScores       = latestScores,
                onToggle           = { if (isRunning) stopMeasurement() else requestCameraPermission.launch(Manifest.permission.CAMERA) },
                onDismissResults   = { viewModel.clearScores() },
                cameraPreview      = { CameraPreviewComposable(onSurfaceProviderReady = { cameraManager?.setSurfaceProvider(it) }) },
                // Settings
                userName           = userName,
                apiBaseUrl         = apiBaseUrl,
                onUserNameChange   = { userName = it;   prefs.edit().putString("user_name",    it).apply() },
                onApiBaseUrlChange = { apiBaseUrl = it; prefs.edit().putString("api_base_url", it).apply() }
            )
        }
    }

    private fun onSignInSuccess(email: String, name: String) {
        googleEmail       = email
        googleDisplayName = name
        isSignedIn        = true
        prefs.edit()
            .putString("google_email", email)
            .putString("google_name",  name)
            .apply()
        // Pre-fill userName from Google display name if not set
        if (userName.isBlank() && name.isNotBlank()) {
            userName = name
            prefs.edit().putString("user_name", name).apply()
        }
    }

    private fun signOut() {
        signInHelper.signOut()
        googleEmail       = ""
        googleDisplayName = ""
        isSignedIn        = false
        prefs.edit()
            .remove("google_email")
            .remove("google_name")
            .apply()
    }

    private fun startMeasurement() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager = CameraManager(this, this) { red, yAvg, ts -> viewModel.onFrame(red, yAvg, ts) }
        cameraManager?.start()
        viewModel.setRunning(true)
    }

    private fun stopMeasurement() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager?.stop()
        cameraManager = null
        viewModel.setRunning(false)
    }

    private fun saveRRFile(rrData: List<Double>, timestamp: String) {
        val safeName      = userName.ifBlank { "unknown" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileTimestamp = timestamp.replace(Regex("[: ]"), "-")
        val file          = File(getExternalFilesDir(null) ?: filesDir, "${safeName}_${fileTimestamp}.txt")
        file.writeText(rrData.joinToString("\n"))
        Toast.makeText(this, "已儲存 ${rrData.size} 個 RR 區間", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stop()
    }
}
