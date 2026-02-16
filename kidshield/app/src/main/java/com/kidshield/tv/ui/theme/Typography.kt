package com.kidshield.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Custom text styles for TV viewing distance
// All styles default to white text for dark TV backgrounds
object TvTextStyles {
    val displayLarge = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
        color = Color.White
    )

    val headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    val headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )

    val titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White
    )

    val bodyLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White
    )

    val labelLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White
    )

    val labelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White
    )
}
