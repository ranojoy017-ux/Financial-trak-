package com.example.data.repository

import com.example.data.database.ExpenseDao
import com.example.data.model.Expense
import com.example.data.model.CategoryBudget
import com.example.data.model.RecurringExpense
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    suspend fun insert(expense: Expense): Long = expenseDao.insertExpense(expense)

    suspend fun update(expense: Expense) = expenseDao.updateExpense(expense)

    suspend fun delete(expense: Expense) = expenseDao.deleteExpense(expense)

    suspend fun getById(id: Int): Expense? = expenseDao.getExpenseById(id)

    fun getByCategory(category: String): Flow<List<Expense>> = expenseDao.getExpensesByCategory(category)

    // Category Budgets
    val allCategoryBudgets: Flow<List<CategoryBudget>> = expenseDao.getAllCategoryBudgets()
    
    suspend fun insertCategoryBudget(budget: CategoryBudget) {
        expenseDao.insertCategoryBudget(budget)
    }

    suspend fun deleteCategoryBudget(budget: CategoryBudget) {
        expenseDao.deleteCategoryBudget(budget)
    }

    // Recurring Expenses
    val allRecurringExpenses: Flow<List<RecurringExpense>> = expenseDao.getAllRecurringExpenses()

    suspend fun insertRecurringExpense(recurring: RecurringExpense): Long =
        expenseDao.insertRecurringExpense(recurring)

    suspend fun updateRecurringExpense(recurring: RecurringExpense) {
        expenseDao.updateRecurringExpense(recurring)
    }

    suspend fun deleteRecurringExpense(recurring: RecurringExpense) {
        expenseDao.deleteRecurringExpense(recurring)
    }

    suspend fun deleteAllExpenses() = expenseDao.deleteAllExpenses()
    suspend fun deleteAllCategoryBudgets() = expenseDao.deleteAllCategoryBudgets()
    suspend fun deleteAllRecurringExpenses() = expenseDao.deleteAllRecurringExpenses()
}
