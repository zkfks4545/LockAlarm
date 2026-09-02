package com.routinealarm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.routinealarm.app.alarm.AlarmInteractionGate
import kotlinx.coroutines.delay

@Composable
fun DismissProgressControl(
    ringStartedAtMillis: Long,
    delaySeconds: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMillis = delaySeconds.coerceAtLeast(0) * 1_000L
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ringStartedAtMillis, durationMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(if (nowMillis - ringStartedAtMillis < durationMillis) 50L else 1_000L)
        }
    }
    val elapsed = (nowMillis - ringStartedAtMillis).coerceAtLeast(0L)
    val progress = if (durationMillis == 0L) {
        1f
    } else {
        (elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
    }
    val complete = AlarmInteractionGate.isUnlocked(
        ringStartedAtMillis = ringStartedAtMillis,
        delaySeconds = delaySeconds,
        nowMillis = nowMillis,
    )
    val remainingSeconds = ((durationMillis - elapsed).coerceAtLeast(0L) + 999L) / 1_000L
    val description = if (complete) "알람 닫기" else "${remainingSeconds}초 뒤 알람을 닫을 수 있음"

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.68f))
            .semantics { contentDescription = description }
            .clickable(enabled = complete, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(58.dp)) {
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                style = Stroke(width = 5.dp.toPx()),
            )
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(
            text = if (complete) "×" else remainingSeconds.toString(),
            color = Color.White,
            style = if (complete) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
        )
    }
}
