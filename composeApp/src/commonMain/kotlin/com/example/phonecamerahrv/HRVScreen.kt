package com.example.phonecamerahrv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HRVScreen(
    rmssd: Double,
    heartRate: Double,
    lastRR: Double,
    isRunning: Boolean,
    waveform: List<Double>,
    isStable: Boolean,
    measurementSeconds: Int,
    onToggle: () -> Unit
) {
    Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("HRV Monitor", fontSize = 28.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))

            PPGWaveform(
                samples = waveform,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.height(8.dp))

            // Stability / progress status line
            if (isRunning) {
                val (statusText, statusColor) = when {
                    measurementSeconds >= 70 -> "Saved!" to Color(0xFF2196F3)
                    isStable -> "Measuring: ${measurementSeconds}s / 70s" to Color(0xFF00CC44)
                    else -> "Stabilizing..." to Color(0xFFFFAA00)
                }
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            MetricCard(
                label = "Heart Rate",
                value = if (heartRate > 0) "${heartRate.toLong()} bpm" else "-- bpm"
            )
            Spacer(Modifier.height(16.dp))
            MetricCard(
                label = "RMSSD",
                value = if (rmssd > 0 && isStable) "${(rmssd * 10).toLong() / 10.0} ms" else "-- ms"
            )
            Spacer(Modifier.height(16.dp))
            MetricCard(
                label = "Peak-to-Peak (RR)",
                value = if (lastRR > 0) "${lastRR.toLong()} ms" else "-- ms"
            )

            Spacer(Modifier.height(24.dp))

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

            if (isRunning && !isStable) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Cover camera firmly with your fingertip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
}

@Composable
private fun PPGWaveform(samples: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0D1B0D))) {
        if (samples.size < 2) return@Canvas

        val min = samples.min()
        val max = samples.max()
        val range = (max - min).coerceAtLeast(1.0)
        val stepX = size.width / (samples.size - 1)

        val path = Path()
        samples.forEachIndexed { i, value ->
            val x = i * stepX
            val y = size.height * (1.0 - (value - min) / range).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color = Color(0xFF00FF66), style = Stroke(width = 2.dp.toPx()))
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
