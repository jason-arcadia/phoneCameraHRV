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
    private val onFrame: (intensity: Double, timestampMs: Long) -> Unit
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
    private val onFrame: (Double, Long) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        // Convert camera sensor nanosecond timestamp to milliseconds
        val timestampMs = image.imageInfo.timestamp / 1_000_000L
        onFrame(extractGreenIntensity(image), timestampMs)
        image.close()
    }

    // YUV_420_888: Y = 0.299R + 0.587G + 0.114B
    // Averaging the Y (luminance) plane over the center region gives a green-dominated
    // intensity signal suitable for PPG — green has the highest weight (0.587).
    private fun extractGreenIntensity(image: ImageProxy): Double {
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        val width = image.width
        val height = image.height
        val regionSize = minOf(50, width, height)
        val startX = (width - regionSize) / 2
        val startY = (height - regionSize) / 2

        var sum = 0L
        for (row in startY until startY + regionSize) {
            for (col in startX until startX + regionSize) {
                val index = row * rowStride + col * pixelStride
                sum += buffer.get(index).toInt() and 0xFF
            }
        }
        return sum.toDouble() / (regionSize * regionSize)
    }
}
