package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StyledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    
    // Smooth transition animation for sliding thumb
    val thumbOffset = animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        label = "thumbOffset"
    )
    
    // Color scheme matching
    val trackBgColor = animateColorAsState(
        targetValue = if (checked) {
            if (isDark) Color(0xFF5F56FF) else MaterialTheme.colorScheme.primary
        } else {
            if (isDark) Color(0xFF3A3A3C) else Color(0xFFDFE4EE)
        },
        label = "trackBgColor"
    )
    
    val thumbColor = animateColorAsState(
        targetValue = if (checked) Color.White else {
            if (isDark) Color(0xFF8E8E93) else Color.White
        },
        label = "thumbColor"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(trackBgColor.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCheckedChange(!checked)
            }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.value)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor.value)
        )
    }
}
