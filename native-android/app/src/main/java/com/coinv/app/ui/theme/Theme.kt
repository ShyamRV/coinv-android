package com.coinv.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CoinDarkScheme = darkColorScheme(
    primary = CoinBlue,
    onPrimary = CoinBackground,
    primaryContainer = CoinBlueDim,
    onPrimaryContainer = CoinChrome,
    secondary = CoinChrome,
    onSecondary = CoinBackground,
    background = CoinBackground,
    onBackground = CoinChrome,
    surface = CoinSurface,
    onSurface = CoinChrome,
    surfaceVariant = CoinSurfaceRaised,
    onSurfaceVariant = CoinChromeMuted,
    error = CoinError,
    onError = CoinChrome,
    outline = CoinBorder
)

private val CoinLightScheme = lightColorScheme(
    primary = CoinBlue,
    onPrimary = CoinChrome,
    background = androidx.compose.ui.graphics.Color(0xFFF4F6FA),
    onBackground = androidx.compose.ui.graphics.Color(0xFF0A0F18),
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color(0xFF0A0F18),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8ECF2),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF5A6478),
    error = CoinError,
    outline = androidx.compose.ui.graphics.Color(0xFFD0D8E4)
)

@Composable
fun CoinVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) CoinDarkScheme else CoinLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            findActivity(view.context)?.let { activity ->
                val window = activity.window
                window.statusBarColor = scheme.background.toArgb()
                window.navigationBarColor = scheme.background.toArgb()
                val lightBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightBars
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
