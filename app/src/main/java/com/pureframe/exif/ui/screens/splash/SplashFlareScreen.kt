package com.pureframe.exif.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashFlareScreen(
    onFinished: () -> Unit
) {
    val tealDark = Color(0xFF0A5C5F)
    val tealMid = Color(0xFF0D7377)
    val tealLight = Color(0xFF14919B)

    val card1Scale = remember { Animatable(0.3f) }
    val card1Alpha = remember { Animatable(0f) }
    val card1Rotate = remember { Animatable(-45f) }
    val card1OffsetX = remember { Animatable(-180f) }
    val card1OffsetY = remember { Animatable(60f) }

    val card2Scale = remember { Animatable(0.3f) }
    val card2Alpha = remember { Animatable(0f) }
    val card2Rotate = remember { Animatable(35f) }
    val card2OffsetX = remember { Animatable(120f) }
    val card2OffsetY = remember { Animatable(-150f) }

    val card3Scale = remember { Animatable(0.3f) }
    val card3Alpha = remember { Animatable(0f) }
    val card3Rotate = remember { Animatable(-25f) }
    val card3OffsetX = remember { Animatable(150f) }
    val card3OffsetY = remember { Animatable(120f) }

    val float1 = remember { Animatable(0f) }
    val float2 = remember { Animatable(0f) }
    val float3 = remember { Animatable(0f) }

    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(50f) }
    val dividerScale = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            launch { card1Scale.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
            launch { card1Alpha.animateTo(1f, tween(800)) }
            launch { card1Rotate.animateTo(-8f, tween(1400, easing = FastOutSlowInEasing)) }
            launch { card1OffsetX.animateTo(0f, tween(1200, easing = FastOutSlowInEasing)) }
            launch { card1OffsetY.animateTo(0f, tween(1200, easing = FastOutSlowInEasing)) }
        }

        delay(250)

        launch {
            launch { card2Scale.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
            launch { card2Alpha.animateTo(1f, tween(800)) }
            launch { card2Rotate.animateTo(6f, tween(1400, easing = FastOutSlowInEasing)) }
            launch { card2OffsetX.animateTo(0f, tween(1200, easing = FastOutSlowInEasing)) }
            launch { card2OffsetY.animateTo(0f, tween(1200, easing = FastOutSlowInEasing)) }
        }

        delay(250)

        launch {
            launch { card3Scale.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
            launch { card3Alpha.animateTo(1f, tween(800)) }
            launch { card3Rotate.animateTo(-4f, tween(1400, easing = FastOutSlowInEasing)) }
            launch { card3OffsetX.animateTo(0f, tween(1200, easing = FastOutSlowInEasing)) }
            launch { card3OffsetY.animateTo(0f, tween(1200, easing = FastOutSlowInEasing)) }
        }

        delay(1400)

        launch {
            float1.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
        launch {
            delay(400)
            float2.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
        launch {
            delay(800)
            float3.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }

        delay(400)
        launch {
            dividerScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
        delay(200)
        launch {
            textAlpha.animateTo(1f, tween(900))
            textOffset.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        }
        delay(400)
        launch {
            taglineAlpha.animateTo(1f, tween(700))
        }

        delay(2500)

        launch {
            card1Alpha.animateTo(0f, tween(600))
            card2Alpha.animateTo(0f, tween(600))
            card3Alpha.animateTo(0f, tween(600))
            textAlpha.animateTo(0f, tween(500))
            taglineAlpha.animateTo(0f, tween(500))
            dividerScale.animateTo(0f, tween(400))
        }

        delay(600)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(tealDark, tealMid, tealLight)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            PhotoCard(
                modifier = Modifier
                    .offset(
                        x = card1OffsetX.value.dp,
                        y = (card1OffsetY.value - 8f + float1.value * 4f).dp
                    )
                    .rotate(card1Rotate.value)
                    .scale(card1Scale.value)
                    .alpha(card1Alpha.value),
                color = Color(0xFFE8F5E9),
                innerColor = Color(0xFF81C784),
                shape = RoundedCornerShape(10.dp)
            )

            PhotoCard(
                modifier = Modifier
                    .offset(
                        x = (card2OffsetX.value + 20f).dp,
                        y = (card2OffsetY.value - 12f + float2.value * 5f).dp
                    )
                    .rotate(card2Rotate.value)
                    .scale(card2Scale.value)
                    .alpha(card2Alpha.value),
                color = Color(0xFFFFF3E0),
                innerColor = Color(0xFFFFB74D),
                shape = RoundedCornerShape(12.dp)
            )

            PhotoCard(
                modifier = Modifier
                    .offset(
                        x = (card3OffsetX.value - 10f).dp,
                        y = (card3OffsetY.value + 10f + float3.value * 3f).dp
                    )
                    .rotate(card3Rotate.value)
                    .scale(card3Scale.value)
                    .alpha(card3Alpha.value),
                color = Color(0xFFE3F2FD),
                innerColor = Color(0xFF64B5F6),
                shape = RoundedCornerShape(14.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = (40f * dividerScale.value).dp, height = 2.dp)
                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            )

            Text(
                text = "EXIF Pure",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .offset(y = textOffset.value.dp)
            )

            Text(
                text = "Your photos. Your privacy.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(taglineAlpha.value)
                    .offset(y = (textOffset.value * 0.5f).dp)
            )
        }
    }
}

@Composable
private fun PhotoCard(
    modifier: Modifier = Modifier,
    color: Color,
    innerColor: Color,
    shape: RoundedCornerShape
) {
    Box(
        modifier = modifier
            .size(150.dp, 110.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(color)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(innerColor)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(90.dp, 45.dp)
                    .background(
                        Color.Black.copy(alpha = 0.12f),
                        RoundedCornerShape(topStart = 45.dp, topEnd = 45.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(14.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp, 16.dp, 0.dp, 0.dp)
                    .size(24.dp, 8.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            )
        }
    }
}
