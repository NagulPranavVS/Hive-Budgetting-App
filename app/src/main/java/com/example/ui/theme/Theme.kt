package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    secondary = DarkNavy,
    tertiary = ElectricMint,
    background = DarkBg,
    surface = SurfaceDark,
    outline = Color(0xFF2A2A2A),
    surfaceVariant = Color(0xFF2A2A2A),
    onPrimary = Color.White,
    onBackground = Color(0xFFF0F0F0),
    onSurface = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF666666),
    outlineVariant = Color(0xFF555555),
    error = CoralRed
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    secondary = DarkNavy,
    tertiary = CoolTeal,
    background = LightBg,
    surface = PureWhite,
    outline = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFFEFEFEF),
    onPrimary = Color.White,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF888888),
    outlineVariant = Color(0xFFBBBBBB),
    error = CoralRed
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
