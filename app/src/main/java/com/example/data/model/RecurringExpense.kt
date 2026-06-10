package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_expenses")
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Double,
    val category: String,
    val frequency: String, // "Weekly", "Monthly", "Yearly"
    val startDateMillis: Long,
    val lastLoggedDateMillis: Long,
    val notes: String = ""
)
