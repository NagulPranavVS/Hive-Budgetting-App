package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun LiquidBackground(
    modifier: Modifier = Modifier,
    themeMode: String = "system"
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    // Modern solid screen backgrounds
    // Light: Very custom light grayish blue (Slate blue)
    // Dark: Premium slate/space greyish black
    val baseBgColor = if (darkTheme) {
        Color(0xFF0F0F0F) // Space greyish black (#0F0F0F)
    } else {
        Color(0xFFF5F5F7) // Clean light page background (#F5F5F7)
    }

    Box(modifier = modifier.fillMaxSize().background(baseBgColor))
}
