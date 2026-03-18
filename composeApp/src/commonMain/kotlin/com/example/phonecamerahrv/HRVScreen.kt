package com.example.phonecamerahrv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HRVScreen(
    rmssd: Double,
    heartRate: Double,
    lastRR: Double,
    isRunning: Boolean,
    onToggle: () -> Unit
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("HRV Monitor", fontSize = 28.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(32.dp))

            MetricCard(
                label = "Heart Rate",
                value = if (heartRate > 0) "${heartRate.toLong()} bpm" else "-- bpm"
            )
            Spacer(Modifier.height(16.dp))
            MetricCard(
                label = "RMSSD",
                value = if (rmssd > 0) "${(rmssd * 10).toLong() / 10.0} ms" else "-- ms"
            )
            Spacer(Modifier.height(16.dp))
            MetricCard(
                label = "Peak-to-Peak (RR)",
                value = if (lastRR > 0) "${lastRR.toLong()} ms" else "-- ms"
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isRunning) "Stop" else "Start Measuring",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (isRunning) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Cover camera with your fingertip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
    }
}
