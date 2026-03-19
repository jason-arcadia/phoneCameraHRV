package com.example.phonecamerahrv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    isFingerDetected: Boolean,
    validCount: Int,
    rejectedCount: Int,
    onToggle: () -> Unit,
    cameraPreview: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
    ) {
        if (isRunning) {
            // Camera preview fills background
            Box(Modifier.fillMaxSize()) { cameraPreview() }

            // Gradient scrim at bottom for button/metrics readability
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xDD000000))
                        )
                    )
            )
        }

        if (!isRunning) {
            // Non-running layout: standard column with metric cards
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("HRV Monitor", fontSize = 28.sp, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(24.dp))

                MetricCard(
                    label = "Heart Rate",
                    value = if (heartRate > 0) "${heartRate.toLong()} bpm" else "-- bpm"
                )
                Spacer(Modifier.height(12.dp))
                MetricCard(
                    label = "RMSSD",
                    value = if (rmssd > 0) "${(rmssd * 10).toLong() / 10.0} ms" else "-- ms"
                )
                Spacer(Modifier.height(12.dp))
                MetricCard(
                    label = "Peak-to-Peak (RR)",
                    value = if (lastRR > 0) "${lastRR.toLong()} ms" else "-- ms"
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Start Measuring",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        } else {
            // Running layout: overlay on camera preview
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top: waveform + status (only when finger detected)
                if (isFingerDetected) {
                    PPGWaveform(
                        samples = waveform,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(8.dp))
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
                    if (validCount + rejectedCount > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "✓ $validCount valid  ✗ $rejectedCount rejected",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Center: target circle
                FingerTargetOverlay(isFingerDetected = isFingerDetected)

                Spacer(Modifier.height(12.dp))

                if (!isFingerDetected) {
                    Text(
                        text = "Place finger on camera",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.weight(1f))

                // Bottom: compact metrics + stop button
                if (isFingerDetected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CompactMetric(
                            label = "Heart Rate",
                            value = if (heartRate > 0) "${heartRate.toLong()} bpm" else "-- bpm"
                        )
                        CompactMetric(
                            label = "RMSSD",
                            value = if (rmssd > 0 && isStable) "${(rmssd * 10).toLong() / 10.0} ms" else "-- ms"
                        )
                        CompactMetric(
                            label = "RR",
                            value = if (lastRR > 0) "${lastRR.toLong()} ms" else "-- ms"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Stop",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FingerTargetOverlay(isFingerDetected: Boolean) {
    Canvas(modifier = Modifier.size(160.dp)) {
        val radius = size.minDimension / 2f
        if (isFingerDetected) {
            drawCircle(
                color = Color(0xAACC2200),
                radius = radius
            )
        }
        drawCircle(
            color = Color.White,
            radius = radius,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun CompactMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun PPGWaveform(samples: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0D1B0D))) {
        if (samples.size < 2) return@Canvas

        // Scale the Y axis from the most recent 60 samples (~2 s at 30 fps) so that
        // early filter-settling transients don't compress the steady-state curve.
        val scaleWindow = samples.takeLast(60)
        val min = scaleWindow.min()
        val max = scaleWindow.max()
        val range = (max - min).coerceAtLeast(1e-6)
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
