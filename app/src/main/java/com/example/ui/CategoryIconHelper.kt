package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconHelper {
    val iconsMap = mapOf(
        "restaurant" to Icons.Filled.Restaurant,
        "directions_car" to Icons.Filled.DirectionsCar,
        "shopping_bag" to Icons.Filled.ShoppingBag,
        "power" to Icons.Filled.Power,
        "movie" to Icons.Filled.Movie,
        "medical_services" to Icons.Filled.MedicalServices,
        "school" to Icons.Filled.School,
        "category" to Icons.Filled.Category,
        "payments" to Icons.Filled.Payments,
        "flight" to Icons.Filled.Flight,
        "home" to Icons.Filled.Home,
        "pets" to Icons.Filled.Pets,
        "monetization_on" to Icons.Filled.MonetizationOn,
        "laptop" to Icons.Filled.Laptop,
        "trending_up" to Icons.Filled.TrendingUp,
        "card_giftcard" to Icons.Filled.CardGiftcard,
        "redeem" to Icons.Filled.Redeem,
        "account_balance_wallet" to Icons.Filled.AccountBalanceWallet,
        "description" to Icons.Filled.Description,
        "work" to Icons.Filled.Work
    )

    fun getIconForName(name: String): ImageVector {
        return iconsMap[name.lowercase()] ?: Icons.Filled.Category
    }

    val availableIconsList = listOf(
        "restaurant", "directions_car", "shopping_bag", "power", 
        "movie", "medical_services", "school", "category",
        "payments", "flight", "home", "pets",
        "monetization_on", "laptop", "trending_up", "card_giftcard",
        "account_balance_wallet", "work", "description"
    )

    val availableColorsList = listOf(
        "#EF4444", // Red
        "#3B82F6", // Blue
        "#EC4899", // Pink
        "#F59E0B", // Amber
        "#8B5CF6", // Violet
        "#10B981", // Emerald
        "#06B6D4", // Cyan
        "#6B7280", // Gray
        "#C8F24A", // Lime
        "#00E5C3", // Bright Cyan
        "#FF5722"  // Deep Orange
    )

    fun parseColor(hex: String, default: Color = Color.Gray): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            default
        }
    }
}
