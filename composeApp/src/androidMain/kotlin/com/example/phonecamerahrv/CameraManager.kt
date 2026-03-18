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
        onFrame(extractRedChannel(image), timestampMs)
        image.close()
    }

    // YUV_420_888 red channel: R = clip(Y + 1.402 * (Cr - 128), 0, 255)
    // Plane 0 = Y (full resolution), Plane 2 = V/Cr (half resolution, quarter pixels).
    // Red channel is the best PPG channel: hemoglobin absorbs strongly at ~540 nm (green)
    // but scatters less and has higher SNR through tissue at longer wavelengths with a torch.
    private fun extractRedChannel(image: ImageProxy): Double {
        val yPlane = image.planes[0]
        val vPlane = image.planes[2]  // Cr (red-difference chroma)

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

        var sum = 0.0
        for (row in startY until startY + regionSize) {
            for (col in startX until startX + regionSize) {
                val y  = yBuf[row * yRowStride + col * yPixStride].toInt() and 0xFF
                val cr = vBuf[(row / 2) * vRowStride + (col / 2) * vPixStride].toInt() and 0xFF
                val r  = (y + 1.402 * (cr - 128)).coerceIn(0.0, 255.0)
                sum += r
            }
        }
        return sum / (regionSize * regionSize)
    }
}
