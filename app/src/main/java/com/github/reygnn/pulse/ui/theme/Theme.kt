package com.github.reygnn.pulse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Custom Heart-Rate palette
val HeartRed = Color(0xFFE53935)
val HeartRedDark = Color(0xFFC62828)
val HeartPink = Color(0xFFFF8A80)
val DarkSurface = Color(0xFF1A1A2E)
val DarkBackground = Color(0xFF0F0F1A)
val CardDark = Color(0xFF16213E)

private val DarkColorScheme = darkColorScheme(
    primary = HeartRed,
    onPrimary = Color.White,
    primaryContainer = HeartRedDark,
    secondary = HeartPink,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = CardDark,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B0C0),
    error = Color(0xFFFF6B6B)
)

private val LightColorScheme = lightColorScheme(
    primary = HeartRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    secondary = HeartRedDark,
    background = Color(0xFFFFF8F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFFFF0EE),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    error = HeartRedDark
)

@Composable
fun PulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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
        typography = Typography(),
        content = content
    )
}
