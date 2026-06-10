package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Double,
    val category: String,
    val dateMillis: Long,
    val isAutoCategorized: Boolean = false,
    val notes: String = ""
)
