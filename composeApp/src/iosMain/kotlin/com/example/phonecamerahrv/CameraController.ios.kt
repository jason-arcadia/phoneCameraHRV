package com.example.phonecamerahrv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.reinterpret
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.Foundation.NSNumber
import platform.Foundation.NSObject
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.DISPATCH_QUEUE_SERIAL

/**
 * iOS actual for [CameraController].
 *
 * Uses AVFoundation to capture rear-camera frames at 640×480, extracts the
 * red channel and Y-luma average from the centre 50×50 pixel region using the
 * biplanar YCbCr pixel format, and forwards each frame to the [onFrame] callback.
 *
 * The [AVCaptureSession] is kept as an [internal] property so that
 * [CameraPreviewView] can attach an [AVCaptureVideoPreviewLayer] to it.
 *
 * Torch is enabled on [start] and disabled on [stop].
 *
 * Pixel math mirrors Android's CameraManager.PPGAnalyzer:
 *   red = clip(Y + 1.402 × (Cr − 128), 0, 255)   for PPG signal
 *   yAvg = Y luma average                          for finger detection
 * iOS biplanar layout: plane-0 = Y, plane-1 = CbCr interleaved (Cb@0, Cr@1).
 */
actual class CameraController {

    internal val session = AVCaptureSession()
    private var device: AVCaptureDevice? = null
    private var frameDelegate: FrameDelegate? = null

    actual fun start(onFrame: (red: Double, yAvg: Double, timestampMs: Long) -> Unit) {
        val backCamera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return

        val input = runCatching {
            AVCaptureDeviceInput.deviceInputWithDevice(backCamera, null) as? AVCaptureDeviceInput
        }.getOrNull() ?: return

        val output = AVCaptureVideoDataOutput().apply {
            videoSettings = mapOf(
                kCVPixelBufferPixelFormatTypeKey to
                    NSNumber(unsignedInt = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange)
            )
            alwaysDiscardsLateVideoFrames = true
        }

        val captureQueue = dispatch_queue_create("ppg.capture.queue", DISPATCH_QUEUE_SERIAL)
        val delegate = FrameDelegate(onFrame)
        frameDelegate = delegate
        output.setSampleBufferDelegate(delegate, queue = captureQueue)

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPreset640x480
        if (session.canAddInput(input))   session.addInput(input)
        if (session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()

        device = backCamera
        enableTorch(true)

        // startRunning must not block the main thread; dispatch to a background queue.
        dispatch_async(dispatch_get_main_queue()) {
            session.startRunning()
        }
    }

    actual fun stop() {
        session.stopRunning()
        enableTorch(false)

        // Remove all inputs and outputs so the session can be cleanly restarted.
        session.inputs.toList().forEach  { session.removeInput(it  as platform.AVFoundation.AVCaptureInput) }
        session.outputs.toList().forEach { session.removeOutput(it as AVCaptureOutput) }

        frameDelegate = null
        device = null
    }

    // ── Torch ────────────────────────────────────────────────────────────────

    private fun enableTorch(on: Boolean) {
        val dev = device ?: return
        if (!dev.hasTorch) return
        runCatching {
            dev.lockForConfiguration(null)
            dev.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
            dev.unlockForConfiguration()
        }
    }
}

// ── Frame delegate ────────────────────────────────────────────────────────────

/**
 * ObjC-compatible delegate that receives decoded video frames from
 * [AVCaptureVideoDataOutput] and extracts the PPG signal.
 */
private class FrameDelegate(
    private val onFrame: (red: Double, yAvg: Double, timestampMs: Long) -> Unit
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        val sampleBuffer = didOutputSampleBuffer ?: return

        // ── Timestamp ───────────────────────────────────────────────────────
        val pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        val timestampMs = (CMTimeGetSeconds(pts) * 1_000.0).toLong()

        // ── Pixel buffer ────────────────────────────────────────────────────
        val imageBuffer: CVImageBufferRef = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return

        CVPixelBufferLockBaseAddress(imageBuffer, kCVPixelBufferLock_ReadOnly)
        try {
            extractAndDispatch(imageBuffer, timestampMs)
        } finally {
            CVPixelBufferUnlockBaseAddress(imageBuffer, kCVPixelBufferLock_ReadOnly)
        }
    }

    /**
     * Extracts the centre 50×50 region from a biplanar YCbCr pixel buffer and
     * computes (redAvg, yAvg), then calls [onFrame].
     *
     * Plane 0 — Y luma, 1 byte/pixel, full resolution.
     * Plane 1 — CbCr interleaved, 2 bytes/pixel, half resolution:
     *           byte 0 = Cb, byte 1 = Cr.
     *
     * Red = clip(Y + 1.402·(Cr − 128), 0, 255) — matches Android PPGAnalyzer.
     */
    private fun extractAndDispatch(imageBuffer: CVImageBufferRef, timestampMs: Long) {
        val width  = CVPixelBufferGetWidth(imageBuffer).toInt()
        val height = CVPixelBufferGetHeight(imageBuffer).toInt()

        val regionSize = minOf(50, width, height)
        val startX = (width  - regionSize) / 2
        val startY = (height - regionSize) / 2

        // Plane 0: Y luma
        val yBase: COpaquePointer = CVPixelBufferGetBaseAddressOfPlane(imageBuffer, 0u) ?: return
        val yBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(imageBuffer, 0u).toInt()
        val yBuf: CPointer<ByteVar> = yBase.reinterpret()

        // Plane 1: CbCr interleaved
        val cbcrBase: COpaquePointer = CVPixelBufferGetBaseAddressOfPlane(imageBuffer, 1u) ?: return
        val cbcrBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(imageBuffer, 1u).toInt()
        val cbcrBuf: CPointer<ByteVar> = cbcrBase.reinterpret()

        var redSum = 0.0
        var ySum   = 0.0

        for (row in startY until startY + regionSize) {
            for (col in startX until startX + regionSize) {
                val y  = yBuf   [row * yBytesPerRow + col].toInt() and 0xFF
                // Cr is at the odd byte of each CbCr pair (offset 1)
                val cr = cbcrBuf[(row / 2) * cbcrBytesPerRow + (col / 2) * 2 + 1].toInt() and 0xFF
                redSum += (y + 1.402 * (cr - 128)).coerceIn(0.0, 255.0)
                ySum   += y
            }
        }

        val pixels = (regionSize * regionSize).toDouble()
        onFrame(redSum / pixels, ySum / pixels, timestampMs)
    }
}

// ── Camera preview composable ─────────────────────────────────────────────────

/**
 * iOS actual for [CameraPreviewView].
 *
 * Wraps an [AVCaptureVideoPreviewLayer] inside a plain [UIView] using
 * Compose Multiplatform's [UIKitView] interop.  The preview layer's frame is
 * updated whenever the host view is laid out.
 */
@Composable
actual fun CameraPreviewView(controller: CameraController, modifier: Modifier) {
    UIKitView(
        factory = {
            val container = UIView()
            val previewLayer = AVCaptureVideoPreviewLayer(session = controller.session)
            previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
            container.layer.addSublayer(previewLayer)
            // Store layer reference in the view's tag slot via associated object pattern
            // is not available in K/N; instead we size the layer in `update`.
            container
        },
        modifier = modifier,
        update = { view ->
            // Resize the preview layer to fill the host view on every layout pass.
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            (view.layer.sublayers?.firstOrNull() as? AVCaptureVideoPreviewLayer)
                ?.setFrame(view.bounds)
            CATransaction.commit()
        }
    )
}
