package com.coinv.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System fonts only — Google Fonts provider can fail on some devices and block first frame.
private val displayFont = FontFamily.SansSerif
private val bodyFont = FontFamily.SansSerif
val jetBrainsMono = FontFamily.Monospace

val Typography = Typography(
    displayMedium = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = 0.5.sp,
        color = CoinChrome
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = 0.5.sp,
        color = CoinChrome
    ),
    titleMedium = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        color = CoinChrome
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = CoinChrome
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = CoinChrome
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = CoinChromeMuted
    ),
    labelSmall = TextStyle(
        fontFamily = jetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = CoinChromeMuted
    )
)
