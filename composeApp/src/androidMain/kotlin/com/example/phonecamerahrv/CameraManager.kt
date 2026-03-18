package com.example.phonecamerahrv

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    // red  = red channel (0–255) for PPG signal
    // yAvg = Y-plane average (0–255) for finger detection
    private val onFrame: (red: Double, yAvg: Double, timestampMs: Long) -> Unit
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private val previewUseCase = Preview.Builder().build()

    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        previewUseCase.setSurfaceProvider(surfaceProvider)
    }

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, PPGAnalyzer(onFrame)) }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                imageAnalysis
            )
            camera?.cameraControl?.enableTorch(true)
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        camera?.cameraControl?.enableTorch(false)
        camera = null
        analysisExecutor.shutdown()
    }
}

private class PPGAnalyzer(
    private val onFrame: (red: Double, yAvg: Double, timestampMs: Long) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        val timestampMs = image.imageInfo.timestamp / 1_000_000L
        val (red, yAvg) = extractChannels(image)
        onFrame(red, yAvg, timestampMs)
        image.close()
    }

    // Returns Pair(redAvg, yAvg) over the centre 50×50 region.
    //   redAvg = R = clip(Y + 1.402*(Cr−128), 0, 255)  — used for PPG signal
    //   yAvg   = Y luminance average                    — used for finger detection
    //
    // Separating them is important: the Cr component elevates R values above Y when
    // blood-filled tissue transmits reddish light, so the old Y-based threshold still
    // reliably detects finger contact while R carries the cleaner PPG waveform.
    private fun extractChannels(image: ImageProxy): Pair<Double, Double> {
        val yPlane = image.planes[0]
        val vPlane = image.planes[2]  // V = Cr (red-difference chroma), half resolution

        val yBuf = yPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixStride = vPlane.pixelStride

        val width = image.width
        val height = image.height
        val regionSize = minOf(50, width, height)
        val startX = (width - regionSize) / 2
        val startY = (height - regionSize) / 2

        var redSum = 0.0
        var ySum   = 0.0
        for (row in startY until startY + regionSize) {
            for (col in startX until startX + regionSize) {
                val y  = yBuf[row * yRowStride + col * yPixStride].toInt() and 0xFF
                val cr = vBuf[(row / 2) * vRowStride + (col / 2) * vPixStride].toInt() and 0xFF
                redSum += (y + 1.402 * (cr - 128)).coerceIn(0.0, 255.0)
                ySum   += y
            }
        }
        val pixels = (regionSize * regionSize).toDouble()
        return redSum / pixels to ySum / pixels
    }
}
