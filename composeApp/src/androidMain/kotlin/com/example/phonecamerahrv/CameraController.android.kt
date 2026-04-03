package com.example.phonecamerahrv

import android.content.Context
import androidx.camera.core.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.LifecycleOwner

/**
 * Android actual for [CameraController].
 *
 * Wraps [CameraManager] (CameraX) so it satisfies the common expect interface while
 * keeping the Android-specific constructor parameters ([Context] + [LifecycleOwner]).
 *
 * The inner [CameraManager] is recreated on every [start] call so that the
 * single-thread executor is fresh after [stop] shuts it down.
 *
 * [setSurfaceProvider] may be called before or after [start]; the surface provider is
 * stored and applied to each new [CameraManager] instance as it is created.
 */
actual class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var innerManager: CameraManager? = null
    private var pendingSurfaceProvider: Preview.SurfaceProvider? = null

    actual fun start(onFrame: (red: Double, yAvg: Double, timestampMs: Long) -> Unit) {
        val mgr = CameraManager(context, lifecycleOwner, onFrame)
        pendingSurfaceProvider?.let { mgr.setSurfaceProvider(it) }
        innerManager = mgr
        mgr.start()
    }

    actual fun stop() {
        innerManager?.stop()
        innerManager = null
    }

    /**
     * Link the CameraX [Preview] surface provider so the live feed is rendered.
     * Called by [CameraPreviewView] when the [PreviewView] surface is ready.
     */
    internal fun setSurfaceProvider(sp: Preview.SurfaceProvider) {
        pendingSurfaceProvider = sp
        innerManager?.setSurfaceProvider(sp)
    }
}

@Composable
actual fun CameraPreviewView(controller: CameraController, modifier: Modifier) {
    CameraPreviewComposable(
        modifier = modifier,
        onSurfaceProviderReady = { controller.setSurfaceProvider(it) }
    )
}
