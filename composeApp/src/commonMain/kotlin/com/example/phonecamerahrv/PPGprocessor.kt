package com.example.phonecamerahrv

import kotlin.math.sqrt

class PPGProcessor {
    private val signalBuffer = mutableListOf<Double>()

    // 1. Receives raw green intensity from camera
    fun onFrame(intensity: Double) {
        signalBuffer.add(intensity)
        if (signalBuffer.size > 300) signalBuffer.removeAt(0) // Keep ~5-10 secs
    }

    // 2. Simple math to calculate RMSSD (The Sports Science Standard)
    fun calculateRMSSD(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0
        val squaredDiffs = rrIntervals.zipWithNext { a, b ->
            val diff = a - b
            diff * diff
        }
        return sqrt(squaredDiffs.average())
    }
}