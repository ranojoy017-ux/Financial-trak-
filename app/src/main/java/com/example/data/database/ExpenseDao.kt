package com.example.data.database

import androidx.room.*
import com.example.data.model.Expense
import com.example.data.model.CategoryBudget
import com.example.data.model.RecurringExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC, id DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Int): Expense?

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY dateMillis DESC")
    fun getExpensesByCategory(category: String): Flow<List<Expense>>

    // Category Budgets
    @Query("SELECT * FROM category_budgets")
    fun getAllCategoryBudgets(): Flow<List<CategoryBudget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryBudget(budget: CategoryBudget)

    @Delete
    suspend fun deleteCategoryBudget(budget: CategoryBudget)

    // Recurring Expenses
    @Query("SELECT * FROM recurring_expenses")
    fun getAllRecurringExpenses(): Flow<List<RecurringExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringExpense(recurring: RecurringExpense): Long

    @Update
    suspend fun updateRecurringExpense(recurring: RecurringExpense)

    @Delete
    suspend fun deleteRecurringExpense(recurring: RecurringExpense)

    // Clear methods for backup restore
    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllCategoryBudgets()

    @Query("DELETE FROM recurring_expenses")
    suspend fun deleteAllRecurringExpenses()
}
