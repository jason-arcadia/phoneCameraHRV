package com.example.phonecamerahrv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HRVViewModel : ViewModel() {

    private val processor = PPGProcessor()
    private val waveformBuffer = ArrayDeque<Double>()
    private var timerJob: Job? = null

    private val _rmssd = MutableStateFlow(0.0)
    val rmssd = _rmssd.asStateFlow()

    private val _heartRate = MutableStateFlow(0.0)
    val heartRate = _heartRate.asStateFlow()

    private val _lastRR = MutableStateFlow(0.0)
    val lastRR = _lastRR.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _waveform = MutableStateFlow<List<Double>>(emptyList())
    val waveform = _waveform.asStateFlow()

    private val _isStable = MutableStateFlow(false)
    val isStable = _isStable.asStateFlow()

    private val _measurementSeconds = MutableStateFlow(0)
    val measurementSeconds = _measurementSeconds.asStateFlow()

    fun onFrame(intensity: Double, timestampMs: Long) {
        processor.onFrame(intensity, timestampMs)
        val metrics = processor.getMetrics()

        _rmssd.value = metrics.rmssd
        _heartRate.value = metrics.heartRate
        _lastRR.value = metrics.lastRR

        waveformBuffer.addLast(intensity)
        if (waveformBuffer.size > WAVEFORM_SIZE) waveformBuffer.removeFirst()
        _waveform.value = waveformBuffer.toList()

        val stable = metrics.isStable
        if (stable != _isStable.value) {
            _isStable.value = stable
            viewModelScope.launch {
                if (stable) startCountdown() else resetCountdown()
            }
        }
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) {
            viewModelScope.launch { resetCountdown() }
            _isStable.value = false
        }
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (s in 0..60) {
                _measurementSeconds.value = s
                delay(1000L)
            }
        }
    }

    private fun resetCountdown() {
        timerJob?.cancel()
        timerJob = null
        _measurementSeconds.value = 0
    }

    companion object {
        private const val WAVEFORM_SIZE = 150 // ~5 seconds at 30 fps
    }
}
