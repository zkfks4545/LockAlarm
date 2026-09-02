package com.routinealarm.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoutineAlarmDarkColors = darkColorScheme(
    primary = Color(0xFF9B8CFF),
    onPrimary = Color(0xFF17122C),
    primaryContainer = Color(0xFF3F356F),
    onPrimaryContainer = Color(0xFFEAE4FF),
    secondary = Color(0xFF9BD8CE),
    background = Color(0xFF07090B),
    onBackground = Color(0xFFF4F4F7),
    surface = Color(0xFF101518),
    onSurface = Color(0xFFF4F4F7),
    surfaceVariant = Color(0xFF1B2226),
    onSurfaceVariant = Color(0xFFB9C0C4),
    outline = Color(0xFF586166),
)

private val RoutineAlarmLightColors = lightColorScheme(
    primary = Color(0xFF5B4B8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF),
    onPrimaryContainer = Color(0xFF211347),
    secondary = Color(0xFF3D766D),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE7EBF1),
    onSurfaceVariant = Color(0xFF5D6268),
    outline = Color(0xFF7B8188),
)

@Composable
fun RoutineAlarmTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) RoutineAlarmDarkColors else RoutineAlarmLightColors,
        content = content,
    )
}
