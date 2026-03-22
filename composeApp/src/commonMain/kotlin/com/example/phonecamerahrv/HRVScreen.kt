package com.example.phonecamerahrv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val PinkLight = Color(0xFFFCE4EC)
private val PinkMid   = Color(0xFFFFB3C1)
private val PinkDeep  = Color(0xFFE91E63)
private val PinkDark  = Color(0xFF880E4F)

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
            .background(Brush.verticalGradient(listOf(PinkLight, PinkMid)))
    ) {
        // Run camera analysis invisibly when active (analysis works without a visible preview)
        if (isRunning) {
            Box(Modifier.size(1.dp)) { cameraPreview() }
        }

        if (!isRunning) {
            IdleContent(onToggle = onToggle)
        } else {
            MeasuringContent(
                heartRate = heartRate,
                isFingerDetected = isFingerDetected,
                isStable = isStable,
                measurementSeconds = measurementSeconds,
                waveform = waveform,
                validCount = validCount,
                rejectedCount = rejectedCount,
                onToggle = onToggle
            )
        }
    }
}

@Composable
private fun IdleContent(onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Static heart
        Canvas(modifier = Modifier.size(120.dp)) { drawHeart(PinkDeep) }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "心率變異性測量",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PinkDark,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "將手指蓋住後置鏡頭\n保持靜止 70 秒",
            fontSize = 15.sp,
            color = PinkDark.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PinkDeep),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "開始測量",
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MeasuringContent(
    heartRate: Double,
    isFingerDetected: Boolean,
    isStable: Boolean,
    measurementSeconds: Int,
    waveform: List<Double>,
    validCount: Int,
    rejectedCount: Int,
    onToggle: () -> Unit
) {
    // Pulse animation: contract–expand like a real heartbeat
    val pulsePeriodMs = if (heartRate > 20) (60000.0 / heartRate).toInt().coerceIn(400, 2000) else 1000
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = pulsePeriodMs
                1.00f at 0                       with LinearEasing
                1.18f at (pulsePeriodMs * 0.15).toInt() with LinearEasing
                0.95f at (pulsePeriodMs * 0.30).toInt() with LinearEasing
                1.12f at (pulsePeriodMs * 0.45).toInt() with LinearEasing
                1.00f at (pulsePeriodMs * 0.60).toInt() with LinearEasing
                1.00f at pulsePeriodMs
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "heartScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Waveform strip (only when stable signal)
        if (isFingerDetected && isStable && waveform.size >= 2) {
            PPGWaveform(
                samples = waveform,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(78.dp))
        }

        // Status text
        val (statusText, statusColor) = when {
            measurementSeconds >= 70 -> "已儲存！" to Color(0xFF1565C0)
            !isFingerDetected -> "請將手指蓋住鏡頭" to PinkDark
            isStable -> "測量中：${measurementSeconds}s / 70s" to Color(0xFF2E7D32)
            else -> "訊號穩定中，請保持不動" to Color(0xFFE65100)
        }
        Text(
            text = statusText,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        if (isFingerDetected && (validCount + rejectedCount > 0)) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "✓ $validCount 有效  ✗ $rejectedCount 無效",
                color = PinkDark.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.weight(1f))

        // Heart + tick ring
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            // Tick marks ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTickRing(
                    filledTicks = measurementSeconds,
                    totalTicks = 70,
                    isActive = isFingerDetected && isStable
                )
            }

            // Animated heart
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer(
                        scaleX = if (isFingerDetected) heartScale else 1f,
                        scaleY = if (isFingerDetected) heartScale else 1f
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawHeart(
                        color = if (isFingerDetected) PinkDeep else PinkDeep.copy(alpha = 0.35f)
                    )
                }
            }
        }

        // Live BPM
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (heartRate > 0 && isStable) "${heartRate.toLong()}" else "--",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = PinkDark,
            textAlign = TextAlign.Center
        )
        Text(
            text = "BPM",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = PinkDark.copy(alpha = 0.65f)
        )

        Spacer(Modifier.weight(1f))

        // Instruction text
        val instruction = when {
            !isFingerDetected -> "請將手指輕輕蓋住後鏡頭"
            !isStable -> "請保持手指靜止不動"
            else -> "測量中，請保持靜止"
        }
        Text(
            text = instruction,
            fontSize = 14.sp,
            color = PinkDark.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "停止測量",
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

private fun DrawScope.drawTickRing(filledTicks: Int, totalTicks: Int, isActive: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outerR = size.minDimension / 2f
    val innerR = outerR * 0.84f

    for (i in 0 until totalTicks) {
        val angleDeg = -90f + i * 360f / totalTicks
        val rad = angleDeg * PI.toFloat() / 180f
        val isFilled = isActive && i < filledTicks
        val isMajor = i % 5 == 0
        val tickColor = when {
            isFilled -> PinkDeep
            else -> PinkMid.copy(alpha = if (isMajor) 0.5f else 0.25f)
        }
        drawLine(
            color = tickColor,
            start = Offset(cx + innerR * cos(rad), cy + innerR * sin(rad)),
            end = Offset(cx + outerR * cos(rad), cy + outerR * sin(rad)),
            strokeWidth = if (isMajor) 4.dp.toPx() else 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawHeart(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.50f, h * 0.90f)
        // left side up to left bump peak
        cubicTo(w * 0.10f, h * 0.60f, w * 0.00f, h * 0.30f, w * 0.25f, h * 0.15f)
        // left bump to center dip
        cubicTo(w * 0.40f, h * 0.02f, w * 0.50f, h * 0.12f, w * 0.50f, h * 0.35f)
        // center dip to right bump peak
        cubicTo(w * 0.50f, h * 0.12f, w * 0.60f, h * 0.02f, w * 0.75f, h * 0.15f)
        // right side down to bottom tip
        cubicTo(w * 1.00f, h * 0.30f, w * 0.90f, h * 0.60f, w * 0.50f, h * 0.90f)
        close()
    }
    drawPath(path, color = color)
}

@Composable
private fun PPGWaveform(samples: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0x33E91E63))) {
        if (samples.size < 2) return@Canvas

        val mean = samples.average()
        val std = sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        val clip = 3.0
        val stepX = size.width / (samples.size - 1)

        val path = Path()
        samples.forEachIndexed { i, value ->
            val z = if (std > 1e-10) ((value - mean) / std).coerceIn(-clip, clip) else 0.0
            val x = i * stepX
            val y = size.height.toDouble() * (1.0 - (z + clip) / (2.0 * clip))
            if (i == 0) path.moveTo(x, y.toFloat()) else path.lineTo(x, y.toFloat())
        }
        drawPath(path, color = PinkDeep, style = Stroke(width = 2.dp.toPx()))
    }
}
