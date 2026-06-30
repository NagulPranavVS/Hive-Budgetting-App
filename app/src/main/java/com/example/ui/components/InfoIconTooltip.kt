package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider

@Composable
fun InfoIconTooltip(
    description: String,
    contentDescription: String
) {
    var expanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val iconColor = if (isDark) Color.White else Color.Black
    val containerBgColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEFEFEF)
    val contentTextColor = MaterialTheme.colorScheme.onSurface
    val borderColor = if (isDark) Color(0xFF444444) else Color(0xFFD1D5DB)

    Box(modifier = Modifier.wrapContentSize()) {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        if (expanded) {
            val density = LocalDensity.current
            val positionProvider = remember(density) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val gap = with(density) { 2.dp.roundToPx() }
                        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                            .coerceIn(8, windowSize.width - popupContentSize.width - 8)
                        val y = anchorBounds.top - popupContentSize.height - gap
                        return IntOffset(x, y)
                    }
                }
            }
            
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = true,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 240.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, borderColor),
                        colors = CardDefaults.cardColors(
                            containerColor = containerBgColor,
                            contentColor = contentTextColor
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = false }
                    ) {
                        Text(
                            text = description,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    
                    Canvas(
                        modifier = Modifier
                            .size(12.dp, 6.dp)
                            .offset(y = (-1).dp)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                        drawPath(path, containerBgColor)
                    }
                }
            }
        }
    }
}
