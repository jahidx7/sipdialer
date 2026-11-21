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
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF10213A),
    primaryContainer = Color(0xFF1B3150),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFF72C7E6),
    onSecondary = Color(0xFF082433),
    secondaryContainer = Color(0xFF17384A),
    onSecondaryContainer = Color(0xFFD4F2FF),
    tertiary = Color(0xFFB9A9F4),
    onTertiary = Color(0xFF201A3B),
    tertiaryContainer = Color(0xFF302947),
    onTertiaryContainer = Color(0xFFEBE5FF),
    background = Color(0xFF0A111C),
    onBackground = Color(0xFFEAF0F8),
    surface = Color(0xFF0F1927),
    onSurface = Color(0xFFEAF0F8),
    surfaceVariant = Color(0xFF1B2A3D),
    onSurfaceVariant = Color(0xFFB1BECE),
    surfaceContainerLowest = Color(0xFF080E17),
    surfaceContainerLow = Color(0xFF121E2E),
    surfaceContainer = Color(0xFF162337),
    surfaceContainerHigh = Color(0xFF1C2B40),
    surfaceContainerHighest = Color(0xFF24364D),
    outline = Color(0xFF60748C),
    outlineVariant = Color(0xFF354960),
    error = Color(0xFFFF737A),
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

    // Respect accessibility sizing without the former app-wide text enlargement.
    val baseDensity = LocalDensity.current
    val effectiveFontScale = (baseDensity.fontScale * UiTextScale.scale).coerceIn(0.85f, 1.3f)
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
