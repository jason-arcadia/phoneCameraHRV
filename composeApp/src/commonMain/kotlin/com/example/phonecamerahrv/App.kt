package com.example.phonecamerahrv

import androidx.compose.runtime.Composable

@Composable
fun App(
    rmssd: Double = 0.0,
    heartRate: Double = 0.0,
    lastRR: Double = 0.0,
    isRunning: Boolean = false,
    onToggle: () -> Unit = {}
) {
    HRVScreen(
        rmssd = rmssd,
        heartRate = heartRate,
        lastRR = lastRR,
        isRunning = isRunning,
        onToggle = onToggle
    )
}
