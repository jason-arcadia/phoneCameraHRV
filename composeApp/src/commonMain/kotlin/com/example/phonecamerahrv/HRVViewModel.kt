package com.example.phonecamerahrv

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HRVViewModel : ViewModel() {

    private val processor = PPGProcessor()

    private val _rmssd = MutableStateFlow(0.0)
    val rmssd = _rmssd.asStateFlow()

    private val _heartRate = MutableStateFlow(0.0)
    val heartRate = _heartRate.asStateFlow()

    private val _lastRR = MutableStateFlow(0.0)
    val lastRR = _lastRR.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    fun onFrame(intensity: Double, timestampMs: Long) {
        processor.onFrame(intensity, timestampMs)
        val metrics = processor.getMetrics()
        _rmssd.value = metrics.rmssd
        _heartRate.value = metrics.heartRate
        _lastRR.value = metrics.lastRR
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}
