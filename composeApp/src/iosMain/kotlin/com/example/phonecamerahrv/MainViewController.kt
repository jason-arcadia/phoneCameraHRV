package com.example.phonecamerahrv

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS entry point — mirrors Android's [MainActivity] logic inside a
 * [ComposeUIViewController] so it is lifecycle-aware via Compose.
 *
 * What this handles:
 *  - [HRVViewModel] (shared KMP ViewModel) for all state and signal processing
 *  - [CameraController] (iOS AVFoundation actual) for frame capture and torch
 *  - Camera permission check / request via AVFoundation
 *  - [saveEvent] collection to persist readings and compute scores locally
 *
 * What is NOT yet implemented on iOS (Android-only for now):
 *  - Google Sign-In   → user always treated as "signed in" on iOS
 *  - Supabase upload  → readings are scored but not uploaded
 *  - Local file save  → [MeasurementStore] is Android-specific; iOS uses no-op baseline
 *
 * IMPORTANT: add `NSCameraUsageDescription` to iosApp/iosApp/Info.plist before
 * shipping, e.g.:
 *   <key>NSCameraUsageDescription</key>
 *   <string>Camera is used to measure your heart-rate variability via PPG.</string>
 */
fun MainViewController() = ComposeUIViewController {

    val viewModel: HRVViewModel = viewModel { HRVViewModel() }

    // ── Camera controller (one instance per composition lifetime) ──────────
    val cameraController = remember { CameraController() }

    // ── Camera permission state ────────────────────────────────────────────
    var hasCameraPermission by remember {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        mutableStateOf(status == AVAuthorizationStatusAuthorized)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    hasCameraPermission = granted
                }
            }
        }
    }

    // ── Observe save events (measurement complete) ─────────────────────────
    LaunchedEffect(Unit) {
        viewModel.saveEvent.collect { rrData ->
            val rmssd  = viewModel.rmssd.value
            val hr     = viewModel.heartRate.value
            val lastRR = viewModel.lastRR.value

            // iOS: no local MeasurementStore yet; use current reading as its own baseline.
            val currentAsBaseline = BaselineStats(rmssd, hr, lastRR, sampleCount = 1)
            val scores = calculateHRVScores(
                rmssd             = rmssd,
                heartRate         = hr,
                lastRR            = lastRR,
                baseline          = null,           // no persistent baseline on iOS yet
                currentAsBaseline = currentAsBaseline
            )
            viewModel.setScores(scores)

            // TODO (iOS): upload to Supabase once Google Sign-In is implemented.

            stopMeasurement(cameraController, viewModel)
        }
    }

    // ── Collect ViewModel state ────────────────────────────────────────────
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
        // iOS: skip sign-in screen until Google Sign-In is implemented for iOS
        isSignedIn        = true,
        googleEmail       = "",
        googleDisplayName = "",
        onGoogleSignIn    = { /* TODO: implement iOS Google Sign-In */ },
        onSignOut         = { /* TODO */ },
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
        onToggle           = {
            if (isRunning) {
                stopMeasurement(cameraController, viewModel)
            } else if (hasCameraPermission) {
                startMeasurement(cameraController, viewModel)
            }
            // If permission not granted, the LaunchedEffect above has already requested it.
        },
        onDismissResults = { viewModel.clearScores() },
        cameraPreview    = { CameraPreviewView(controller = cameraController) },
        // Settings (not persisted on iOS yet)
        userName           = "",
        apiBaseUrl         = "",
        onUserNameChange   = {},
        onApiBaseUrlChange = {}
    )
}

// ── Helper functions matching Android's startMeasurement / stopMeasurement ───

private fun startMeasurement(controller: CameraController, viewModel: HRVViewModel) {
    controller.start { red, yAvg, ts -> viewModel.onFrame(red, yAvg, ts) }
    viewModel.setRunning(true)
}

private fun stopMeasurement(controller: CameraController, viewModel: HRVViewModel) {
    controller.stop()
    viewModel.setRunning(false)
}
