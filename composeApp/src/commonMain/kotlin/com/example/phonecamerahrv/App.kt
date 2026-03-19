package com.example.phonecamerahrv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun App(
    rmssd: Double = 0.0,
    heartRate: Double = 0.0,
    lastRR: Double = 0.0,
    isRunning: Boolean = false,
    waveform: List<Double> = emptyList(),
    isStable: Boolean = false,
    measurementSeconds: Int = 0,
    isFingerDetected: Boolean = false,
    validCount: Int = 0,
    rejectedCount: Int = 0,
    userName: String = "",
    coachEmail: String = "",
    onUserNameChange: (String) -> Unit = {},
    onCoachEmailChange: (String) -> Unit = {},
    onToggle: () -> Unit = {},
    cameraPreview: @Composable () -> Unit = {}
) {
    MaterialTheme {
        var selectedTab by remember { mutableStateOf(0) }

        val topHorizontal = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(topHorizontal)
                .consumeWindowInsets(topHorizontal)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Measure") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Settings") }
                )
            }

            when (selectedTab) {
                0 -> HRVScreen(
                    rmssd = rmssd,
                    heartRate = heartRate,
                    lastRR = lastRR,
                    isRunning = isRunning,
                    waveform = waveform,
                    isStable = isStable,
                    measurementSeconds = measurementSeconds,
                    isFingerDetected = isFingerDetected,
                    validCount = validCount,
                    rejectedCount = rejectedCount,
                    onToggle = onToggle,
                    cameraPreview = cameraPreview
                )
                1 -> SettingsScreen(
                    userName = userName,
                    coachEmail = coachEmail,
                    onUserNameChange = onUserNameChange,
                    onCoachEmailChange = onCoachEmailChange,
                    modifier = Modifier
                )
            }
        }
    }
}
