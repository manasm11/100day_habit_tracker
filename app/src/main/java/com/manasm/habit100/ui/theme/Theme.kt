package com.manasm.habit100.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = HabitColors.done,
    onPrimary = Color.White,
    surface = Color(0xFFFBFBF9),
    background = Color(0xFFFBFBF9),
    error = HabitColors.missed,
)

private val DarkColors = darkColorScheme(
    primary = HabitColors.done,
    onPrimary = Color.White,
    surface = Color(0xFF16181A),
    background = Color(0xFF101214),
    error = HabitColors.missed,
)

@Composable
fun HabitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HabitTypography,
        content = content,
    )
}
