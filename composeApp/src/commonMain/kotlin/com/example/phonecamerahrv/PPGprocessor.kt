package com.example.phonecamerahrv

import kotlin.math.sqrt

/**
 * Real-time PPG signal conditioner:
 *   1. Rolling-mean normalisation over a 2-second window → removes DC / amplitude drift
 *      output = (raw − mean) / mean  (dimensionless fractional change)
 *   2. Single 2nd-order Butterworth bandpass biquad, 0.7–3.5 Hz → isolates heart-rate band
 *      (42–210 bpm) while leaving enough headroom on both edges
 *
 * Replacing the previous 4th-order cascade (HP + LP) with one biquad halves the filter
 * order, reduces phase distortion, and avoids the "doubly attenuated transition band" that
 * was flattening the signal near the passband edges.
 *
 * Design (fs = 30 Hz, bilinear transform with pre-warping):
 *   Pre-warped analog:  Ωl = 60·tan(π·0.7/30) ≈ 4.409
 *                       Ωh = 60·tan(π·3.5/30) ≈ 23.032
 *   ω₀ = √(Ωl·Ωh) ≈ 10.078,  BW = Ωh − Ωl ≈ 18.623
 *
 *   H(z) = b0·(1 − z⁻²) / (1 + a1·z⁻¹ + a2·z⁻²)
 *   b0 = BW·K / (K² + BW·K + ω₀²)  where K = 2·fs = 60
 *      = 1117.4 / 4818.97 ≈ 0.23186
 *   a1 = 2·(ω₀² − K²) / (K² + BW·K + ω₀²)
 *      = 2·(101.57 − 3600) / 4818.97 ≈ −1.45218
 *   a2 = (K² − BW·K + ω₀²) / (K² + BW·K + ω₀²)
 *      = 2584.17 / 4818.97 ≈ 0.53630
 *   Pole magnitude |z| = √a2 ≈ 0.732  (< 1, stable ✓)
 */
class PPGSignalFilter {
    // Rolling mean (2-second window, keyed by camera-sensor timestamp)
    private val rollingBuf = ArrayDeque<Pair<Double, Long>>()

    // Biquad state
    private var x1 = 0.0; private var x2 = 0.0
    private var y1 = 0.0; private var y2 = 0.0

    companion object {
        private const val ROLLING_WINDOW_MS = 2_000L

        // 2nd-order Butterworth BPF, 0.7–3.5 Hz, fs = 30 Hz
        // b1 = 0 by construction (bandpass biquad zeros at DC and Nyquist)
        private const val B0 =  0.23186
        private const val B2 = -0.23186
        private const val A1 = -1.45218   // denominator: 1 + A1·z⁻¹ + A2·z⁻²
        private const val A2 =  0.53630
    }

    /**
     * Feed one raw red-channel sample; returns the bandpass-filtered, mean-normalised value.
     * Must be called in frame-arrival order.
     */
    fun process(raw: Double, timestampMs: Long): Double {
        // 1. Rolling-mean normalisation
        rollingBuf.addLast(raw to timestampMs)
        while (rollingBuf.isNotEmpty() && timestampMs - rollingBuf.first().second > ROLLING_WINDOW_MS) {
            rollingBuf.removeFirst()
        }
        val mean = rollingBuf.sumOf { it.first } / rollingBuf.size
        val normalized = if (mean > 0.0) (raw - mean) / mean else 0.0

        // 2. Bandpass biquad  y[n] = b0·x[n] + b2·x[n-2] − a1·y[n-1] − a2·y[n-2]
        val y = B0 * normalized + B2 * x2 - A1 * y1 - A2 * y2
        x2 = x1; x1 = normalized
        y2 = y1; y1 = y

        return y
    }

    fun reset() {
        rollingBuf.clear()
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }
}

data class HRVMetrics(
    val rmssd: Double,           // ms
    val heartRate: Double,       // bpm
    val lastRR: Double,          // ms — most recent RR interval
    val meanRR: Double,          // ms — mean of all valid RR intervals in session
    val isStable: Boolean,
    val isFingerDetected: Boolean,
    val validCount: Int,         // RR intervals accepted (300–1500 ms)
    val rejectedCount: Int       // RR intervals outside physiological range
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
    private var rejectedCount = 0

    companion object {
        private const val BUFFER_SIZE = 300
        private const val PEAK_HALF_WINDOW = 8    // ~0.27 s at 30 fps — local-max confirmation window
        private const val MIN_RR_MS = 300L         // validity gate lower bound (physiological)
        private const val MAX_RR_MS = 1500L        // validity gate upper bound (physiological)
        private const val MAX_RR_COUNT = 100       // enough for 70 s at 60 bpm
        private const val STABILITY_WINDOW = 30    // ~1 second at 30 fps
        private const val RAW_WINDOW = 15          // ~0.5 s window for finger detection
        const val STABLE_VARIANCE_THRESHOLD = 2e-3
        const val FINGER_THRESHOLD = 100.0

        // Adaptive threshold peak detection
        private const val REFRACTORY_MS = 500L           // dead-time after each peak
        private const val ROLLING_MAX_WINDOW_MS = 3_000L // window for computing rolling amplitude max
        private const val ADAPTIVE_THRESHOLD_RATIO = 0.6 // threshold = 60 % of rolling max
        private const val MIN_THRESHOLD = 1e-4            // guard against flat/zero signal
    }

    // yBrightness = Y-plane average (0–255); used only for finger detection.
    // Keeping it separate from the red channel avoids the Cr-inflation problem:
    // R = Y + 1.402*(Cr−128) can exceed 100 even with a finger on the lens,
    // whereas Y alone reliably stays below 100 when tissue blocks the torch.
    fun onFrame(redIntensity: Double, yBrightness: Double, timestampMs: Long) {
        // Y-plane average → finger detection (threshold on raw luminance, proven reliable)
        rawBuffer.addLast(yBrightness)
        if (rawBuffer.size > RAW_WINDOW) rawBuffer.removeFirst()

        // Red channel → bandpass filter + rolling-mean normalisation → PPG signal
        val filtered = filter.process(redIntensity, timestampMs)

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

    /** Last bandpass-filtered, mean-normalised sample; null until first frame arrives. */
    fun getLastFilteredSample(): Double? = signalBuffer.lastOrNull()

    /** Clear all state — call at the start of each new measurement session. */
    fun reset() {
        signalBuffer.clear()
        timestampBuffer.clear()
        rawBuffer.clear()
        rrIntervals.clear()
        rrTimestamps.clear()
        peakTimestamps.clear()
        lastDetectedPeakTimestamp = -1L
        rejectedCount = 0
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

        val center    = n - PEAK_HALF_WINDOW - 1
        val centerTs  = timestampBuffer[center]
        val value     = signalBuffer[center]

        // ── 1. Refractory period: ignore the first 500 ms after the last peak ──────
        if (peakTimestamps.isNotEmpty() && centerTs - peakTimestamps.last() < REFRACTORY_MS) return

        // ── 2. Adaptive threshold on the inverted signal (valleys = blood-volume peaks) ─
        //    Camera PPG dips when blood volume is highest, so cardiac events are valleys
        //    (negative excursions) in the filtered signal.  We work on -signal throughout.
        val windowStartMs = centerTs - ROLLING_MAX_WINDOW_MS
        var windowIdx = center
        while (windowIdx > 0 && timestampBuffer[windowIdx - 1] >= windowStartMs) windowIdx--
        // Rolling amplitude of negative excursions = -(rolling min of original signal)
        val rollingMin    = signalBuffer.subList(windowIdx, center + 1).minOrNull() ?: 0.0
        val rollingMaxNeg = -rollingMin   // positive amplitude of deepest valley in window
        val threshold     = maxOf(rollingMaxNeg * ADAPTIVE_THRESHOLD_RATIO, MIN_THRESHOLD)

        // Valley condition: value must be sufficiently negative
        if (-value <= threshold) return

        // ── 3. Local-minimum confirmation: no neighbour in ±HALF_WINDOW should be lower ─
        for (i in (center - PEAK_HALF_WINDOW)..(center + PEAK_HALF_WINDOW)) {
            if (i != center && signalBuffer[i] <= value) return
        }

        // ── 4. Guard against re-processing the exact same timestamp ──────────────────
        if (centerTs == lastDetectedPeakTimestamp) return

        // ── 5. Record RR interval and apply physiological validity gate ───────────────
        if (peakTimestamps.isNotEmpty()) {
            val rr = centerTs - peakTimestamps.last()
            if (rr in MIN_RR_MS..MAX_RR_MS) {
                rrIntervals.add(rr.toDouble())
                rrTimestamps.add(centerTs)
                if (rrIntervals.size > MAX_RR_COUNT) {
                    rrIntervals.removeAt(0)
                    rrTimestamps.removeAt(0)
                }
            } else {
                rejectedCount++
            }
        }

        peakTimestamps.add(centerTs)
        if (peakTimestamps.size > MAX_RR_COUNT + 1) peakTimestamps.removeAt(0)
        lastDetectedPeakTimestamp = centerTs
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
        if (rr.isEmpty()) return HRVMetrics(0.0, 0.0, 0.0, 0.0, stable, fingerDetected, 0, rejectedCount)
        val meanRR = rr.average()
        return HRVMetrics(
            rmssd = calculateRMSSD(rr),
            heartRate = 60000.0 / meanRR,
            lastRR = rr.last(),
            meanRR = meanRR,
            isStable = stable,
            isFingerDetected = fingerDetected,
            validCount = rr.size,
            rejectedCount = rejectedCount
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
