package com.noxsip.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Navy100,
    onPrimaryContainer = Navy900,
    secondary = Sky500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Violet500,
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE8EEF9),
    onSurfaceVariant = Slate600,
    surfaceContainer = Navy50,
    outline = Slate400,
    outlineVariant = Slate200,
    error = Red700,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Navy500,
    onPrimary = Color.White,
    primaryContainer = Navy800,
    onPrimaryContainer = Navy100,
    secondary = Sky500,
    onSecondary = Color.White,
    secondaryContainer = Navy800,
    onSecondaryContainer = Navy100,
    tertiary = Violet500,
    onTertiary = Color.White,
    background = Navy950,
    onBackground = Color(0xFFF8FAFC),
    surface = Navy900,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate400,
    surfaceContainer = Navy900,
    outline = Slate600,
    outlineVariant = Slate700,
    error = Red500,
    onError = Color.White,
)

@Composable
fun SipDialerTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeMode by ThemeModePrefs.mode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeModePrefs.LIGHT -> false
        ThemeModePrefs.DARK -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge bars let the current screen and Android's system UI share the same theme.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // পুরো অ্যাপের লেখা বড় করা: সিস্টেম ফন্ট স্কেলের ওপর আমাদের গুণক (সর্বোচ্চ 1.6 পর্যন্ত)
    val baseDensity = LocalDensity.current
    val effectiveFontScale = (baseDensity.fontScale * UiTextScale.scale).coerceAtMost(1.6f)
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, effectiveFontScale)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
