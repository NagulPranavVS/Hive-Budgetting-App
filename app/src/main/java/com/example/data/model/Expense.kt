package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val description: String,
    val date: Long,
    val categoryId: Int,
    val isIncome: Boolean = false,
    val isSavings: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
