package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FinanceBackup(
    val version: Int = 1,
    val backupTimeMillis: Long,
    val expenses: List<Expense>,
    val categoryBudgets: List<CategoryBudget>,
    val recurringExpenses: List<RecurringExpense>,
    val monthlyBudgetLimit: Double
)
