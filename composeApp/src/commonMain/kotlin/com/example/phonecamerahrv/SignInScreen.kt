package com.example.phonecamerahrv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SignInPinkLight = Color(0xFFFCE4EC)
private val SignInPinkMid   = Color(0xFFFFB3C1)
private val SignInPinkDeep  = Color(0xFFE91E63)
private val SignInPinkDark  = Color(0xFF880E4F)

@Composable
fun SignInScreen(onGoogleSignIn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SignInPinkLight, SignInPinkMid))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(100.dp)) { drawSignInHeart(SignInPinkDeep) }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "心率變異性",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = SignInPinkDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "HRV Monitor",
                fontSize = 16.sp,
                color = SignInPinkDark.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Text(
                text = "請登入以開始使用",
                fontSize = 15.sp,
                color = SignInPinkDark.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onGoogleSignIn,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "使用 Google 帳號登入",
                    fontSize = 16.sp,
                    color = Color(0xFF3C4043),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawSignInHeart(color: Color) {
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
