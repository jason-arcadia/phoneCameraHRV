package com.example.phonecamerahrv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific camera controller.
 *
 * Android actual  — wraps CameraX CameraManager (rear camera + torch).
 * iOS actual      — wraps AVFoundation AVCaptureSession (rear camera + torch).
 *
 * Call [start] to begin capturing frames; [stop] to release all camera resources.
 * Both calls are idempotent and can be alternated.
 */
expect class CameraController {
    /**
     * Start capturing frames.
     * [onFrame] receives (redChannelAvg 0–255, yLumaAvg 0–255, timestampMs) per frame.
     */
    fun start(onFrame: (red: Double, yAvg: Double, timestampMs: Long) -> Unit)

    /** Stop capturing and release the camera + torch. */
    fun stop()
}

/**
 * Platform-specific camera live-preview composable.
 * Renders the camera feed (full-bleed) linked to [controller]'s capture session.
 */
@Composable
expect fun CameraPreviewView(controller: CameraController, modifier: Modifier = Modifier)
