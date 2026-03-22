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
    ) {
        if (!isRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(PinkLight, PinkMid)))
            )
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
                onToggle = onToggle,
                cameraPreview = cameraPreview
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
    onToggle: () -> Unit,
    cameraPreview: @Composable () -> Unit
) {
    // Pulse scale: animate in sync with measured heart rate
    val pulsePeriodMs = if (heartRate > 20) (60000.0 / heartRate).toInt().coerceIn(400, 2000) else 1000
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = pulsePeriodMs
                1.00f at 0                                      with LinearEasing
                1.04f at (pulsePeriodMs * 0.15).toInt()         with LinearEasing
                0.98f at (pulsePeriodMs * 0.30).toInt()         with LinearEasing
                1.02f at (pulsePeriodMs * 0.45).toInt()         with LinearEasing
                1.00f at (pulsePeriodMs * 0.60).toInt()         with LinearEasing
                1.00f at pulsePeriodMs
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "ringScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Layer 1: camera preview fills the screen ──────────────────────────
        Box(Modifier.fillMaxSize()) { cameraPreview() }

        // ── Layer 2: top scrim + waveform + status ────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xDD000000), Color(0x88000000), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isFingerDetected && isStable && waveform.size >= 2) {
                    PPGWaveform(
                        samples = waveform,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                }

                val (statusText, statusColor) = when {
                    measurementSeconds >= 70 -> "已儲存！" to Color(0xFF64B5F6)
                    !isFingerDetected        -> "請將手指蓋住鏡頭" to Color.White
                    isStable                 -> "測量中：${measurementSeconds}s / 70s" to Color(0xFF81C784)
                    else                     -> "訊號穩定中，請保持不動" to Color(0xFFFFB74D)
                }
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                if (isFingerDetected && validCount + rejectedCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "✓ $validCount 有效  ✗ $rejectedCount 無效",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Layer 3: center — tick ring + circle guide (camera visible through) ─
        Canvas(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.Center)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = size.minDimension / 2f
            val tickInnerR = outerR * 0.82f
            val circleR = outerR * 0.72f

            // Tick ring
            for (i in 0 until 70) {
                val angleDeg = -90f + i * 360f / 70
                val rad = angleDeg * PI.toFloat() / 180f
                val isFilled = isFingerDetected && isStable && i < measurementSeconds
                val isMajor = i % 5 == 0
                val tickColor = when {
                    isFilled -> PinkDeep
                    else     -> Color.White.copy(alpha = if (isMajor) 0.5f else 0.25f)
                }
                drawLine(
                    color = tickColor,
                    start = Offset(cx + tickInnerR * cos(rad), cy + tickInnerR * sin(rad)),
                    end   = Offset(cx + outerR * cos(rad), cy + outerR * sin(rad)),
                    strokeWidth = if (isMajor) 4.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Circle guide — transparent centre so camera is fully visible
            val circleRScaled = circleR * if (isFingerDetected) ringScale else 1f
            if (isFingerDetected) {
                // Subtle coloured glow to confirm finger contact
                drawCircle(
                    color = Color(0x44E91E63),
                    radius = circleRScaled,
                    center = Offset(cx, cy)
                )
            }
            drawCircle(
                color = if (isFingerDetected) PinkDeep else Color.White,
                radius = circleRScaled,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // ── Layer 4: bottom scrim + BPM + instruction + stop button ───────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xBB000000), Color(0xEE000000))
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Live BPM
                Text(
                    text = if (heartRate > 0 && isStable) "${heartRate.toLong()}" else "--",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "BPM",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(8.dp))

                val instruction = when {
                    !isFingerDetected -> "請將手指輕輕蓋住後鏡頭"
                    !isStable         -> "請保持手指靜止不動"
                    else              -> "測量中，請保持靜止"
                }
                Text(
                    text = instruction,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))

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
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawHeart(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.50f, h * 0.90f)
        cubicTo(w * 0.10f, h * 0.60f, w * 0.00f, h * 0.30f, w * 0.25f, h * 0.15f)
        cubicTo(w * 0.40f, h * 0.02f, w * 0.50f, h * 0.12f, w * 0.50f, h * 0.35f)
        cubicTo(w * 0.50f, h * 0.12f, w * 0.60f, h * 0.02f, w * 0.75f, h * 0.15f)
        cubicTo(w * 1.00f, h * 0.30f, w * 0.90f, h * 0.60f, w * 0.50f, h * 0.90f)
        close()
    }
    drawPath(path, color = color)
}

@Composable
private fun PPGWaveform(samples: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0D1B0D))) {
        if (samples.size < 2) return@Canvas
        val mean = samples.average()
        val std = sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        val clip = 3.0
        val stepX = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { i, value ->
            val z = if (std > 1e-10) ((value - mean) / std).coerceIn(-clip, clip) else 0.0
            val x = i * stepX
            // Invert y so that signal valleys (PPG blood-volume peaks) render pointing up
            val y = size.height.toDouble() * (z + clip) / (2.0 * clip)
            if (i == 0) path.moveTo(x, y.toFloat()) else path.lineTo(x, y.toFloat())
        }
        drawPath(path, color = Color(0xFF00FF66), style = Stroke(width = 2.dp.toPx()))
    }
}
