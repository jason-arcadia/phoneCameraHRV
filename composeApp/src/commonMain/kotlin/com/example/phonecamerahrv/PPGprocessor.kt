package com.example.phonecamerahrv

import kotlin.math.sqrt

data class HRVMetrics(
    val rmssd: Double,      // ms
    val heartRate: Double,  // bpm
    val lastRR: Double,     // ms
    val isStable: Boolean
)

class PPGProcessor {
    private val signalBuffer = mutableListOf<Double>()
    private val timestampBuffer = mutableListOf<Long>()
    private val rrIntervals = mutableListOf<Double>()
    private val peakTimestamps = mutableListOf<Long>()
    private var lastDetectedPeakTimestamp = -1L

    companion object {
        private const val BUFFER_SIZE = 300
        private const val PEAK_HALF_WINDOW = 8  // ~0.27s at 30 fps
        private const val MIN_RR_MS = 300L       // 200 bpm max
        private const val MAX_RR_MS = 2000L      // 30 bpm min
        private const val MAX_RR_COUNT = 20
        private const val STABILITY_WINDOW = 30  // ~1 second at 30 fps
        const val STABLE_VARIANCE_THRESHOLD = 150.0
    }

    fun onFrame(intensity: Double, timestampMs: Long) {
        signalBuffer.add(intensity)
        timestampBuffer.add(timestampMs)
        if (signalBuffer.size > BUFFER_SIZE) {
            signalBuffer.removeAt(0)
            timestampBuffer.removeAt(0)
        }
        // Only accumulate RR intervals during stable signal segments
        if (isSignalStable()) detectPeak()
    }

    private fun isSignalStable(): Boolean {
        if (signalBuffer.size < STABILITY_WINDOW) return false
        val window = signalBuffer.takeLast(STABILITY_WINDOW)
        val mean = window.average()
        val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
        return variance < STABLE_VARIANCE_THRESHOLD
    }

    private fun detectPeak() {
        val n = signalBuffer.size
        if (n < PEAK_HALF_WINDOW * 2 + 1) return

        val center = n - PEAK_HALF_WINDOW - 1
        val value = signalBuffer[center]
        val mean = signalBuffer.average()

        if (value <= mean) return

        for (i in (center - PEAK_HALF_WINDOW)..(center + PEAK_HALF_WINDOW)) {
            if (i != center && signalBuffer[i] >= value) return
        }

        val timestamp = timestampBuffer[center]
        if (timestamp == lastDetectedPeakTimestamp) return

        if (peakTimestamps.isNotEmpty()) {
            val rr = timestamp - peakTimestamps.last()
            if (rr in MIN_RR_MS..MAX_RR_MS) {
                rrIntervals.add(rr.toDouble())
                if (rrIntervals.size > MAX_RR_COUNT) rrIntervals.removeAt(0)
            }
        }

        peakTimestamps.add(timestamp)
        if (peakTimestamps.size > MAX_RR_COUNT + 1) peakTimestamps.removeAt(0)
        lastDetectedPeakTimestamp = timestamp
    }

    fun getMetrics(): HRVMetrics {
        val stable = isSignalStable()
        val rr = rrIntervals.toList()
        if (rr.isEmpty()) return HRVMetrics(0.0, 0.0, 0.0, stable)
        return HRVMetrics(
            rmssd = calculateRMSSD(rr),
            heartRate = 60000.0 / rr.average(),
            lastRR = rr.last(),
            isStable = stable
        )
    }

    fun calculateRMSSD(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0
        val squaredDiffs = rrIntervals.zipWithNext { a, b ->
            val diff = a - b
            diff * diff
        }
        return sqrt(squaredDiffs.average())
    }
}
