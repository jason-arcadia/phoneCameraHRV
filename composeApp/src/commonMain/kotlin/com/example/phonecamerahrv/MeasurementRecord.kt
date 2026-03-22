package com.example.phonecamerahrv

data class MeasurementRecord(
    val timestampMs: Long,
    val rmssd: Double,
    val heartRate: Double,
    val lastRR: Double
)

data class BaselineStats(
    val rmssd: Double,
    val heartRate: Double,
    val lastRR: Double,
    val sampleCount: Int
)
