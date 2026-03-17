package com.example.kitchenmind.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LightGreen,
    secondary = EmeraldGreen,
    tertiary = LightGreen,
    background = DarkGray,
    surface = DarkGray,
    onPrimary = VerySoftWhite,
    onSecondary = VerySoftWhite,
    onBackground = VerySoftWhite,
    onSurface = VerySoftWhite,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    secondary = DarkGreen,
    tertiary = EmeraldGreen,
    background = VerySoftWhite,
    surface = SoftWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkGray,
    onSurface = DarkGray,
    error = ErrorRed
)

@Composable
fun KitchenMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}