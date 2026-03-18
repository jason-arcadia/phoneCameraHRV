package com.example.phonecamerahrv

import kotlin.math.sqrt

/**
 * Real-time PPG signal conditioner:
 *   1. Rolling-mean normalization over a 5-second window → removes DC and amplitude drift
 *      (normalized = (raw − mean) / mean, a dimensionless fractional change)
 *   2. 2nd-order Butterworth high-pass at 0.5 Hz → removes residual baseline wander
 *   3. 2nd-order Butterworth low-pass at 4 Hz  → removes shot noise / motion HF artifacts
 *
 * Coefficients designed for fs = 30 Hz via the bilinear transform (pre-warped).
 * HP 0.5 Hz: Ω_hp = 2·30·tan(π·0.5/30) ≈ 3.145  →  b = [0.9286, −1.8572,  0.9286], a = [−1.8521, 0.8622]
 * LP 4.0 Hz: Ω_lp = 2·30·tan(π·4.0/30) ≈ 26.71  →  b = [0.1085,  0.2169,  0.1085], a = [−0.8772, 0.3110]
 */
class PPGSignalFilter {
    // ── Rolling mean (5-second window, keyed by camera-sensor timestamp) ──────────
    private val rollingBuf = ArrayDeque<Pair<Double, Long>>()  // (rawValue, timestampMs)

    // ── HP biquad state (x = input history, y = output history) ─────────────────
    private var hpX1 = 0.0; private var hpX2 = 0.0
    private var hpY1 = 0.0; private var hpY2 = 0.0

    // ── LP biquad state ──────────────────────────────────────────────────────────
    private var lpX1 = 0.0; private var lpX2 = 0.0
    private var lpY1 = 0.0; private var lpY2 = 0.0

    companion object {
        private const val ROLLING_WINDOW_MS = 5_000L

        // 2nd-order Butterworth HP at 0.5 Hz, fs = 30 Hz
        // H(z) = b0(1 − 2z⁻¹ + z⁻²) / (1 + a1·z⁻¹ + a2·z⁻²)
        private const val HP_B0 =  0.92860
        private const val HP_B1 = -1.85720
        private const val HP_B2 =  0.92860
        private const val HP_A1 = -1.85214   // sign convention: denominator = 1 + a1·z⁻¹ + a2·z⁻²
        private const val HP_A2 =  0.86225

        // 2nd-order Butterworth LP at 4.0 Hz, fs = 30 Hz
        private const val LP_B0 =  0.10845
        private const val LP_B1 =  0.21690
        private const val LP_B2 =  0.10845
        private const val LP_A1 = -0.87722
        private const val LP_A2 =  0.31101
    }

    /**
     * Feed one raw sample; returns the bandpass-filtered, mean-normalized value.
     * Call once per camera frame in arrival order.
     */
    fun process(raw: Double, timestampMs: Long): Double {
        // 1. Update rolling-mean window
        rollingBuf.addLast(raw to timestampMs)
        while (rollingBuf.isNotEmpty() && timestampMs - rollingBuf.first().second > ROLLING_WINDOW_MS) {
            rollingBuf.removeFirst()
        }
        val mean = rollingBuf.sumOf { it.first } / rollingBuf.size
        val normalized = if (mean > 0.0) (raw - mean) / mean else 0.0

        // 2. High-pass biquad (difference equation: y[n] = b·x − (−a)·y_prev)
        val hp = HP_B0 * normalized + HP_B1 * hpX1 + HP_B2 * hpX2 - HP_A1 * hpY1 - HP_A2 * hpY2
        hpX2 = hpX1; hpX1 = normalized
        hpY2 = hpY1; hpY1 = hp

        // 3. Low-pass biquad
        val lp = LP_B0 * hp + LP_B1 * lpX1 + LP_B2 * lpX2 - LP_A1 * lpY1 - LP_A2 * lpY2
        lpX2 = lpX1; lpX1 = hp
        lpY2 = lpY1; lpY1 = lp

        return lp
    }

    fun reset() {
        rollingBuf.clear()
        hpX1 = 0.0; hpX2 = 0.0; hpY1 = 0.0; hpY2 = 0.0
        lpX1 = 0.0; lpX2 = 0.0; lpY1 = 0.0; lpY2 = 0.0
    }
}

data class HRVMetrics(
    val rmssd: Double,           // ms
    val heartRate: Double,       // bpm
    val lastRR: Double,          // ms
    val isStable: Boolean,
    val isFingerDetected: Boolean
)

class PPGProcessor {
    // Filtered signal buffer — fed to stability check and peak detection.
    private val signalBuffer = mutableListOf<Double>()
    private val timestampBuffer = mutableListOf<Long>()
    // Raw red-channel buffer — used only for finger detection (threshold on raw brightness).
    private val rawBuffer = ArrayDeque<Double>()

    private val filter = PPGSignalFilter()

    // Parallel lists: rrTimestamps[i] is the camera timestamp of the peak ending rrIntervals[i]
    private val rrIntervals = mutableListOf<Double>()
    private val rrTimestamps = mutableListOf<Long>()
    private val peakTimestamps = mutableListOf<Long>()
    private var lastDetectedPeakTimestamp = -1L

    companion object {
        private const val BUFFER_SIZE = 300
        private const val PEAK_HALF_WINDOW = 8   // ~0.27 s at 30 fps
        private const val MIN_RR_MS = 300L        // 200 bpm max
        private const val MAX_RR_MS = 2000L       // 30 bpm min
        private const val MAX_RR_COUNT = 100      // enough for 70 s at 60 bpm
        private const val STABILITY_WINDOW = 30   // ~1 second at 30 fps
        private const val RAW_WINDOW = 15         // ~0.5 s window for finger detection
        // Variance threshold on the bandpass-normalized signal (dimensionless fractional units).
        // A clean PPG has amplitude ~1–3 % of DC → variance ~ 5e-5 to 4.5e-4.
        // Motion / no-finger artifacts push this well above 1e-3.
        const val STABLE_VARIANCE_THRESHOLD = 1e-3
        const val FINGER_THRESHOLD = 100.0        // raw red-channel avg below this → finger present
    }

    fun onFrame(rawIntensity: Double, timestampMs: Long) {
        // Track raw red-channel for finger detection
        rawBuffer.addLast(rawIntensity)
        if (rawBuffer.size > RAW_WINDOW) rawBuffer.removeFirst()

        // Apply bandpass filter + rolling-mean normalisation
        val filtered = filter.process(rawIntensity, timestampMs)

        signalBuffer.add(filtered)
        timestampBuffer.add(timestampMs)
        if (signalBuffer.size > BUFFER_SIZE) {
            signalBuffer.removeAt(0)
            timestampBuffer.removeAt(0)
        }
        if (isFingerDetected() && isSignalStable()) detectPeak()
    }

    fun isFingerDetected(): Boolean {
        if (rawBuffer.isEmpty()) return false
        return rawBuffer.average() < FINGER_THRESHOLD
    }

    /** Clear all state — call at the start of each new measurement session. */
    fun reset() {
        signalBuffer.clear()
        timestampBuffer.clear()
        rawBuffer.clear()
        rrIntervals.clear()
        rrTimestamps.clear()
        peakTimestamps.clear()
        lastDetectedPeakTimestamp = -1L
        filter.reset()
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
                rrTimestamps.add(timestamp)
                if (rrIntervals.size > MAX_RR_COUNT) {
                    rrIntervals.removeAt(0)
                    rrTimestamps.removeAt(0)
                }
            }
        }

        peakTimestamps.add(timestamp)
        if (peakTimestamps.size > MAX_RR_COUNT + 1) peakTimestamps.removeAt(0)
        lastDetectedPeakTimestamp = timestamp
    }

    /** Returns RR intervals (ms) whose end-peak timestamp falls within [fromMs, toMs]. */
    fun getRRIntervalsInRange(fromMs: Long, toMs: Long): List<Double> =
        rrTimestamps.zip(rrIntervals)
            .filter { (ts, _) -> ts in fromMs..toMs }
            .map { (_, rr) -> rr }

    /** Camera-sensor timestamp (ms) of the most recent frame received. */
    fun getLastTimestampMs(): Long = timestampBuffer.lastOrNull() ?: 0L

    fun getMetrics(): HRVMetrics {
        val stable = isSignalStable()
        val fingerDetected = isFingerDetected()
        val rr = rrIntervals.toList()
        if (rr.isEmpty()) return HRVMetrics(0.0, 0.0, 0.0, stable, fingerDetected)
        return HRVMetrics(
            rmssd = calculateRMSSD(rr),
            heartRate = 60000.0 / rr.average(),
            lastRR = rr.last(),
            isStable = stable,
            isFingerDetected = fingerDetected
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
