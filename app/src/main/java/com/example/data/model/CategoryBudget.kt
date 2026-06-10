package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val category: String,
    val limitAmount: Double,
    val isAlertEnabled: Boolean = true
)
