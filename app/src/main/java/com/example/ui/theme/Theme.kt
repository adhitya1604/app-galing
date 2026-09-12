package com.example.ui.theme

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
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF09090B),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = DuoOrangeBright,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF431407),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = DuoCyanScan,
    background = DuoBackgroundDark,
    surface = DuoSurfaceDark,
    surfaceVariant = DuoSurfaceVariantDark,
    onBackground = DuoTextPrimaryDark,
    onSurface = DuoTextPrimaryDark,
    onSurfaceVariant = DuoTextSecondaryDark,
    outline = Color(0xFF3F3F46)
)

private val LightColorScheme = lightColorScheme(
    primary = DuoBluePrimary, // Editorial deep midnight ink
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4F4F5), // Refined neutral container
    onPrimaryContainer = DuoBluePrimary,
    secondary = DuoOrangeAccent, // Editorial terracotta
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF7ED),
    onSecondaryContainer = Color(0xFF9A3412),
    tertiary = DuoCyanScan,
    background = DuoBackgroundLight, // Archival warm paper
    surface = DuoSurfaceLight, // Pure crisp editorial white
    surfaceVariant = DuoSurfaceVariantLight, // Tinted parchment
    onBackground = DuoTextPrimary, // High-contrast ink
    onSurface = DuoTextPrimary,
    onSurfaceVariant = DuoTextSecondary, // Editorial slate
    outline = DuoCardStroke // Crisp 1dp hairline border
)

@Composable
fun DuoGalingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set false to preserve Duo Galing's distinctive branding colors
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Keep alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = DuoGalingTheme(darkTheme, dynamicColor, content)
