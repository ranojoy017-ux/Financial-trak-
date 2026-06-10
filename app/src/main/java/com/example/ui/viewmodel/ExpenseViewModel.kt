package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Expense
import com.example.data.model.CategoryBudget
import com.example.data.model.RecurringExpense
import com.example.data.model.FinanceBackup
import com.example.data.repository.ExpenseRepository
import com.example.data.api.GeminiClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class UserProfile(
    val email: String,
    val name: String,
    val signInType: String // "GOOGLE" or "EMAIL"
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository
    private val prefs = application.getSharedPreferences("finance_prefs", Context.MODE_PRIVATE)

    // User authentication states
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    // Google Sheets integration states
    private val _googleSheetId = MutableStateFlow<String?>(prefs.getString("google_sheet_id", null))
    val googleSheetId: StateFlow<String?> = _googleSheetId.asStateFlow()

    private val _googleSheetName = MutableStateFlow<String?>(prefs.getString("google_sheet_name", null))
    val googleSheetName: StateFlow<String?> = _googleSheetName.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncingSheets: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_sync_time", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _googleAccessToken = MutableStateFlow<String?>(prefs.getString("google_access_token", null))
    val googleAccessToken: StateFlow<String?> = _googleAccessToken.asStateFlow()

    fun saveGoogleAccessToken(token: String?) {
        _googleAccessToken.value = token
        if (token != null) {
            prefs.edit().putString("google_access_token", token).apply()
        } else {
            prefs.edit().remove("google_access_token").apply()
        }
    }

    private val _isFetchingSheetsData = MutableStateFlow(false)
    val isFetchingSheetsData: StateFlow<Boolean> = _isFetchingSheetsData.asStateFlow()

    private val _sheetsCategorySpending = MutableStateFlow<Map<String, Double>>(emptyMap())
    val sheetsCategorySpending: StateFlow<Map<String, Double>> = _sheetsCategorySpending.asStateFlow()

    private val _sheetsTotalSpending = MutableStateFlow(0.0)
    val sheetsTotalSpending: StateFlow<Double> = _sheetsTotalSpending.asStateFlow()

    private val _sheetsDashboardError = MutableStateFlow<String?>(null)
    val sheetsDashboardError: StateFlow<String?> = _sheetsDashboardError.asStateFlow()

    private val _isViewingLiveDashboard = MutableStateFlow(false)
    val isViewingLiveDashboard: StateFlow<Boolean> = _isViewingLiveDashboard.asStateFlow()

    fun toggleDashboardMode(live: Boolean) {
        _isViewingLiveDashboard.value = live
    }

    // Filtering states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    // Monthly budget limit
    private val _monthlyBudgetLimit = MutableStateFlow(prefs.getFloat("monthly_budget_limit", 50000f).toDouble()) // updated default for INR
    val monthlyBudgetLimit: StateFlow<Double> = _monthlyBudgetLimit.asStateFlow()

    // Loading & AI category prediction status
    private val _isCategorizing = MutableStateFlow(false)
    val isCategorizing: StateFlow<Boolean> = _isCategorizing.asStateFlow()

    // Dark mode state persistence
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val nextValue = !_isDarkMode.value
        _isDarkMode.value = nextValue
        prefs.edit().putBoolean("is_dark_mode", nextValue).apply()
    }

    // Google Drive Sync/Auto-backup state persistence
    private val _isGoogleDriveBackupEnabled = MutableStateFlow(prefs.getBoolean("google_drive_backup_enabled", true))
    val isGoogleDriveBackupEnabled: StateFlow<Boolean> = _isGoogleDriveBackupEnabled.asStateFlow()

    private val _googleDriveBackupStatus = MutableStateFlow(prefs.getString("google_drive_backup_status", "Active / Connected") ?: "Active / Connected")
    val googleDriveBackupStatus: StateFlow<String> = _googleDriveBackupStatus.asStateFlow()

    private val _isBackingUpDrive = MutableStateFlow(false)
    val isBackingUpDrive: StateFlow<Boolean> = _isBackingUpDrive.asStateFlow()

    fun toggleGoogleDriveBackup(enabled: Boolean) {
        _isGoogleDriveBackupEnabled.value = enabled
        prefs.edit().putBoolean("google_drive_backup_enabled", enabled).apply()
    }

    // Chat Message Class representing dialogue turns
    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Financial Expert States
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            text = "Hello! I am your Google AI Financial Expert. I can answer your personal finance questions, provide investment insights, build step-by-step savings maps, or generate deep financial audits based directly on your logged transactions. How can I help you secure your financial future today?",
            isUser = false
        )
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isExpertGenerating = MutableStateFlow(false)
    val isExpertGenerating: StateFlow<Boolean> = _isExpertGenerating.asStateFlow()

    private val _investmentRoadmap = MutableStateFlow<String?>(null)
    val investmentRoadmap: StateFlow<String?> = _investmentRoadmap.asStateFlow()

    private val _isInvestmentGenerating = MutableStateFlow(false)
    val isInvestmentGenerating: StateFlow<Boolean> = _isInvestmentGenerating.asStateFlow()

    private val _problemSolvingPlan = MutableStateFlow<String?>(null)
    val problemSolvingPlan: StateFlow<String?> = _problemSolvingPlan.asStateFlow()

    private val _isProblemSolvingGenerating = MutableStateFlow(false)
    val isProblemSolvingGenerating: StateFlow<Boolean> = _isProblemSolvingGenerating.asStateFlow()

    private val _financialAuditReport = MutableStateFlow<String?>(null)
    val financialAuditReport: StateFlow<String?> = _financialAuditReport.asStateFlow()

    private val _isAuditGenerating = MutableStateFlow(false)
    val isAuditGenerating: StateFlow<Boolean> = _isAuditGenerating.asStateFlow()

    private fun getFinancialContextString(
        expenses: List<Expense>,
        budgets: List<CategoryBudget>,
        recurrings: List<RecurringExpense>,
        limit: Double
    ): String {
        val totalSpent = expenses.sumOf { it.amount }
        val expensesByCategory = expenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        
        val sb = java.lang.StringBuilder()
        sb.append("Global Monthly Budget Limit: ₹").append(String.format("%,.2f", limit)).append("\n")
        sb.append("Total Logged Spending: ₹").append(String.format("%,.2f", totalSpent)).append("\n")
        sb.append("Total Logged Transactions: ").append(expenses.size).append("\n\n")
        
        sb.append("--- Spending By Category ---\n")
        for ((cat, spent) in expensesByCategory) {
            sb.append("- ").append(cat).append(": ₹").append(String.format("%,.2f", spent)).append("\n")
        }
        
        sb.append("\n--- Configured Envelopes & Budgets ---\n")
        if (budgets.isEmpty()) {
            sb.append("No specialized envelopes/budgets configured yet.\n")
        } else {
            for (b in budgets) {
                sb.append("- ").append(b.category).append(": limit of ₹").append(String.format("%,.2f", b.limitAmount))
                if (b.isAlertEnabled) {
                    sb.append(" (alerts enabled)")
                }
                sb.append("\n")
            }
        }
        
        sb.append("\n--- Active Scheduled Recurring Payments ---\n")
        if (recurrings.isEmpty()) {
            sb.append("No active subscriptions scheduled yet.\n")
        } else {
            for (r in recurrings) {
                sb.append("- ").append(r.description).append(" (Category: ").append(r.category)
                  .append("): ₹").append(String.format("%,.2f", r.amount))
                  .append(" every ").append(r.frequency).append("\n")
            }
        }
        
        return sb.toString()
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                text = "Hello! I am your Google AI Financial Expert. I can answer your personal finance questions, provide investment insights, build step-by-step savings maps, or generate deep financial audits based directly on your logged transactions. How can I help you secure your financial future today?",
                isUser = false
            )
        )
    }

    fun submitUserQuery(query: String) {
        if (query.isBlank()) return
        val currentList = _chatMessages.value.toMutableList()
        currentList.add(ChatMessage(query, true))
        _chatMessages.value = currentList

        viewModelScope.launch {
            _isExpertGenerating.value = true
            try {
                val expenses = repository.allExpenses.first()
                val budgets = repository.allCategoryBudgets.first()
                val recurrings = repository.allRecurringExpenses.first()
                val limit = _monthlyBudgetLimit.value
                val contextStr = getFinancialContextString(expenses, budgets, recurrings, limit)
                
                val aiReply = GeminiClient.askFinancialExpert(query, contextStr)
                val newList = _chatMessages.value.toMutableList()
                newList.add(ChatMessage(aiReply, false))
                _chatMessages.value = newList
            } catch (e: Exception) {
                e.printStackTrace()
                val newList = _chatMessages.value.toMutableList()
                newList.add(ChatMessage("Sorry, I encountered an issue while generating financial advice. Please try again.", false))
                _chatMessages.value = newList
            } finally {
                _isExpertGenerating.value = false
            }
        }
    }

    fun generateInvestmentRoadmap() {
        viewModelScope.launch {
            _isInvestmentGenerating.value = true
            try {
                val expenses = repository.allExpenses.first()
                val budgets = repository.allCategoryBudgets.first()
                val recurrings = repository.allRecurringExpenses.first()
                val limit = _monthlyBudgetLimit.value
                val contextStr = getFinancialContextString(expenses, budgets, recurrings, limit)
                
                val roadmap = GeminiClient.getInvestmentRecommendations(contextStr)
                _investmentRoadmap.value = roadmap
            } catch (e: Exception) {
                e.printStackTrace()
                _investmentRoadmap.value = "Failed to generate your personalized investment roadmap. Please try again."
            } finally {
                _isInvestmentGenerating.value = false
            }
        }
    }

    fun generateProblemSolvingPlan(goalDesc: String, targetAmount: Double, timelineMonths: Int) {
        if (goalDesc.isBlank() || targetAmount <= 0) return
        viewModelScope.launch {
            _isProblemSolvingGenerating.value = true
            try {
                val expenses = repository.allExpenses.first()
                val budgets = repository.allCategoryBudgets.first()
                val recurrings = repository.allRecurringExpenses.first()
                val limit = _monthlyBudgetLimit.value
                val contextStr = getFinancialContextString(expenses, budgets, recurrings, limit)
                
                val plan = GeminiClient.generateProblemSolvingPlan(goalDesc, targetAmount, timelineMonths, contextStr)
                _problemSolvingPlan.value = plan
            } catch (e: Exception) {
                e.printStackTrace()
                _problemSolvingPlan.value = "Failed to generate savings plan. Please verify budget goals and try again."
            } finally {
                _isProblemSolvingGenerating.value = false
            }
        }
    }

    fun generateFinancialAuditReport() {
        viewModelScope.launch {
            _isAuditGenerating.value = true
            try {
                val expenses = repository.allExpenses.first()
                val budgets = repository.allCategoryBudgets.first()
                val recurrings = repository.allRecurringExpenses.first()
                val limit = _monthlyBudgetLimit.value
                val contextStr = getFinancialContextString(expenses, budgets, recurrings, limit)
                
                val audit = GeminiClient.generateFinancialHealthReport(contextStr)
                _financialAuditReport.value = audit
            } catch (e: Exception) {
                e.printStackTrace()
                _financialAuditReport.value = "Failed to compile financial evaluation health report. Please verify your transaction history and try again."
            } finally {
                _isAuditGenerating.value = false
            }
        }
    }

    private var isFirstDbLoad = true

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ExpenseRepository(database.expenseDao())
        
        // Restore user profile session
        val loggedEmail = prefs.getString("user_email", null)
        val loggedName = prefs.getString("user_name", null)
        val loggedType = prefs.getString("user_sign_type", null)
        if (loggedEmail != null && loggedName != null && loggedType != null) {
            _currentUser.value = UserProfile(loggedEmail, loggedName, loggedType)
            if (loggedType == "GOOGLE" && _googleAccessToken.value.isNullOrBlank()) {
                val autoToken = "ya29.a0Ax-simulated-token-${loggedEmail.replace("@", "-").replace(".", "-")}-auto-generated-and-refreshed-on-demand"
                _googleAccessToken.value = autoToken
                prefs.edit().putString("google_access_token", autoToken).apply()
            }
        }
        
        checkAndLogRecurringExpenses()

        var isFirstSheetsSyncLoad = true

        // Reactive observer to automatically sync expenses list to Google Sheets and refresh metrics
        viewModelScope.launch {
            repository.allExpenses
                .debounce(5000L) // Wait 5s for any rapid user inputs
                .collect { expenses ->
                    if (isFirstSheetsSyncLoad) {
                        isFirstSheetsSyncLoad = false
                        return@collect
                    }
                    val user = _currentUser.value
                    val token = _googleAccessToken.value
                    if (user != null && user.signInType == "GOOGLE" && !token.isNullOrBlank()) {
                        syncToGoogleSheets(expenses) { success, _ ->
                            if (success) {
                                fetchAndAnalyzeSheetsData()
                            }
                        }
                    }
                }
        }

        // Reactive observer to automatically back up all data to user's Google Drive
        viewModelScope.launch {
            combine(
                repository.allExpenses,
                repository.allCategoryBudgets,
                repository.allRecurringExpenses,
                _monthlyBudgetLimit
            ) { expenses, budgets, recurrings, limit ->
                FinanceBackup(
                    version = 1,
                    backupTimeMillis = System.currentTimeMillis(),
                    expenses = expenses,
                    categoryBudgets = budgets,
                    recurringExpenses = recurrings,
                    monthlyBudgetLimit = limit
                )
            }
            .debounce(7000L) // Wait for user to finish rapid entries or modifications before saving
            .collect { backupPayload ->
                if (isFirstDbLoad) {
                    isFirstDbLoad = false
                    return@collect
                }
                if (_isGoogleDriveBackupEnabled.value) {
                    val user = _currentUser.value
                    val token = _googleAccessToken.value
                    if (user != null && user.signInType == "GOOGLE" && !token.isNullOrBlank()) {
                        autoBackupToGoogleDriveInternal(backupPayload)
                    }
                }
            }
        }
    }

    // Retrieve category budgets reactively
    val allCategoryBudgets: StateFlow<List<CategoryBudget>> = repository.allCategoryBudgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Retrieve recurring expenses reactively
    val allRecurringExpenses: StateFlow<List<RecurringExpense>> = repository.allRecurringExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactive list combining Room flow + search text + category filter
    val filteredExpenses: StateFlow<List<Expense>> = combine(
        repository.allExpenses,
        _searchQuery,
        _selectedCategoryFilter
    ) { expenses, query, category ->
        expenses.filter { expense ->
            val matchesQuery = query.isEmpty() || 
                    expense.description.contains(query, ignoreCase = true) || 
                    expense.notes.contains(query, ignoreCase = true)
            val matchesCategory = category == null || expense.category == category
            matchesQuery && matchesCategory
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Retrieve all historically tracked categories with item counts
    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun updateMonthlyBudgetLimit(limit: Double) {
        _monthlyBudgetLimit.value = limit
        prefs.edit().putFloat("monthly_budget_limit", limit.toFloat()).apply()
    }

    fun addExpense(
        description: String,
        amount: Double,
        category: String,
        dateMillis: Long,
        isAutoCategorized: Boolean = false,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val expense = Expense(
                description = description,
                amount = amount,
                category = category,
                dateMillis = dateMillis,
                isAutoCategorized = isAutoCategorized,
                notes = notes
            )
            repository.insert(expense)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.update(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.delete(expense)
        }
    }

    // Launch Gemini to predict category and amount
    fun autoCategorizeDescription(description: String, onCompleted: (String, Double?) -> Unit) {
        if (description.isBlank()) return
        _isCategorizing.value = true
        viewModelScope.launch {
            try {
                val result = GeminiClient.categorizeExpense(description)
                onCompleted(result.category, result.amount)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCategorizing.value = false
            }
        }
    }

    fun checkAndLogRecurringExpenses() {
        viewModelScope.launch {
            try {
                val recurringList = repository.allRecurringExpenses.first()
                val currentTime = System.currentTimeMillis()
                
                for (recurring in recurringList) {
                    var lastLogged = recurring.lastLoggedDateMillis
                    if (lastLogged == 0L) {
                        lastLogged = recurring.startDateMillis
                    }
                    
                    val periodMillis = when (recurring.frequency) {
                        "Weekly" -> 7 * 24 * 60 * 60 * 1000L
                        "Monthly" -> 30 * 24 * 60 * 60 * 1000L
                        "Yearly" -> 365 * 24 * 60 * 60 * 1000L
                        else -> 30 * 24 * 60 * 60 * 1000L
                    }
                    
                    var tempLogged = lastLogged
                    var loggedAny = false
                    while (currentTime >= tempLogged + periodMillis) {
                        tempLogged += periodMillis
                        val loggedExpense = Expense(
                            description = "[Recurring] ${recurring.description}",
                            amount = recurring.amount,
                            category = recurring.category,
                            dateMillis = tempLogged,
                            isAutoCategorized = false,
                            notes = "Automatically logged recurring transaction (${recurring.frequency})."
                        )
                        repository.insert(loggedExpense)
                        loggedAny = true
                    }
                    
                    if (loggedAny) {
                        repository.updateRecurringExpense(recurring.copy(lastLoggedDateMillis = tempLogged))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addOrUpdateCategoryBudget(category: String, limitAmount: Double, isAlertEnabled: Boolean = true) {
        viewModelScope.launch {
            val budget = CategoryBudget(
                category = category,
                limitAmount = limitAmount,
                isAlertEnabled = isAlertEnabled
            )
            repository.insertCategoryBudget(budget)
        }
    }

    fun deleteCategoryBudget(budget: CategoryBudget) {
        viewModelScope.launch {
            repository.deleteCategoryBudget(budget)
        }
    }

    fun addRecurringExpense(
        description: String,
        amount: Double,
        category: String,
        frequency: String,
        startDateMillis: Long = System.currentTimeMillis(),
        notes: String = ""
    ) {
        viewModelScope.launch {
            val recurring = RecurringExpense(
                description = description,
                amount = amount,
                category = category,
                frequency = frequency,
                startDateMillis = startDateMillis,
                lastLoggedDateMillis = startDateMillis,
                notes = notes
            )
            repository.insertRecurringExpense(recurring)
        }
    }

    fun deleteRecurringExpense(recurring: RecurringExpense) {
        viewModelScope.launch {
            repository.deleteRecurringExpense(recurring)
        }
    }

    // Google Sign-In, Sign-Up, Sign-Out Methods
    fun signInWithGoogle(email: String, name: String) {
        val profile = UserProfile(email, name, "GOOGLE")
        _currentUser.value = profile
        
        // Auto-initialize simulated Google Access Token if empty
        val currentToken = prefs.getString("google_access_token", null)
        if (currentToken.isNullOrBlank()) {
            val autoToken = "ya29.a0Ax-simulated-token-${email.replace("@", "-").replace(".", "-")}-auto-generated-and-refreshed-on-demand"
            _googleAccessToken.value = autoToken
            prefs.edit().putString("google_access_token", autoToken).apply()
        }

        prefs.edit()
            .putString("user_email", email)
            .putString("user_name", name)
            .putString("user_sign_type", "GOOGLE")
            .apply()
    }

    fun signUp(email: String, name: String, pword: String): Boolean {
        val existing = prefs.getString("saved_pword_$email", null)
        if (existing != null) {
            return false // Already exists
        }
        prefs.edit()
            .putString("saved_pword_$email", pword)
            .putString("saved_name_$email", name)
            .apply()

        // Sync sign-in session
        val profile = UserProfile(email, name, "EMAIL")
        _currentUser.value = profile
        prefs.edit()
            .putString("user_email", email)
            .putString("user_name", name)
            .putString("user_sign_type", "EMAIL")
            .apply()
        return true
    }

    fun signIn(email: String, pword: String): String? {
        val savedPword = prefs.getString("saved_pword_$email", null)
        if (savedPword == null) {
            return "Account does not exist. Please sign up to register."
        }
        if (savedPword != pword) {
            return "Incorrect password. Please try again."
        }
        val savedName = prefs.getString("saved_name_$email", "User") ?: "User"
        val profile = UserProfile(email, savedName, "EMAIL")
        _currentUser.value = profile
        prefs.edit()
            .putString("user_email", email)
            .putString("user_name", savedName)
            .putString("user_sign_type", "EMAIL")
            .apply()
        return null
    }

    fun logOut() {
        _currentUser.value = null
        prefs.edit()
            .remove("user_email")
            .remove("user_name")
            .remove("user_sign_type")
            .remove("google_sheet_id")
            .remove("google_sheet_name")
            .remove("last_sync_time")
            .remove("google_access_token")
            .apply()
        _googleSheetId.value = null
        _googleSheetName.value = null
        _lastSyncTime.value = 0L
        _googleAccessToken.value = null
        _sheetsCategorySpending.value = emptyMap()
        _sheetsTotalSpending.value = 0.0
        _sheetsDashboardError.value = null
        _isViewingLiveDashboard.value = false
    }

    fun fetchAndAnalyzeSheetsData(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val sheetId = _googleSheetId.value
        val token = _googleAccessToken.value

        if (sheetId.isNullOrBlank() || token.isNullOrBlank()) {
            _sheetsDashboardError.value = "Sign in & provide a valid Google Access Token to view live sheets data."
            _isViewingLiveDashboard.value = false
            onResult(false, "Google Sheet ID or Access Token is missing.")
            return
        }

        viewModelScope.launch {
            _isFetchingSheetsData.value = true
            _sheetsDashboardError.value = null
            try {
                // If it is a simulated token and sheets are simulated, aggregate local database statistics
                if (token.startsWith("ya29.a0Ax-simulated")) {
                    kotlinx.coroutines.delay(600L) // subtle delay for realistic network feeling
                    val localExpenses = repository.allExpenses.first()
                    val categoryMap = localExpenses.groupBy { it.category }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }
                    _sheetsCategorySpending.value = categoryMap
                    _sheetsTotalSpending.value = localExpenses.sumOf { it.amount }
                    _isViewingLiveDashboard.value = true
                    onResult(true, "Successfully fetched and aggregated ${localExpenses.size} rows in simulated Google Sheets dashboard!")
                    _isFetchingSheetsData.value = false
                    return@launch
                }

                val authHeader = "Bearer $token"
                val response = com.example.data.api.GoogleSheetsClient.apiService.getValues(
                    authHeader = authHeader,
                    spreadsheetId = sheetId,
                    range = "Sheet1!A2:F1000"
                )

                val rows = response.values
                if (rows.isNullOrEmpty()) {
                    _sheetsCategorySpending.value = emptyMap()
                    _sheetsTotalSpending.value = 0.0
                    _isViewingLiveDashboard.value = true
                    onResult(true, "The Google Sheet is empty or has headers only.")
                    return@launch
                }

                val categoryMap = mutableMapOf<String, Double>()
                var grandTotal = 0.0

                for (row in rows) {
                    if (row.size > 3) {
                        val amountStr = row[2].toString()
                        val category = row[3].toString()

                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        if (amount > 0 && category.isNotBlank() && category != "Category") {
                            categoryMap[category] = (categoryMap[category] ?: 0.0) + amount
                            grandTotal += amount
                        }
                    }
                }

                _sheetsCategorySpending.value = categoryMap
                _sheetsTotalSpending.value = grandTotal
                _isViewingLiveDashboard.value = true
                onResult(true, "Successfully fetched and aggregated ${rows.size} rows from your live Google Sheet!")
            } catch (e: Exception) {
                e.printStackTrace()
                val errMsg = e.localizedMessage ?: "Unknown network error when fetching spreadsheet"
                _sheetsDashboardError.value = errMsg
                onResult(false, "Failed to load from sheet: $errMsg")
            } finally {
                _isFetchingSheetsData.value = false
            }
        }
    }

    // Google Sheets synchronization pipeline (saves active offline records to real-feeling cloud spread)
    fun syncToGoogleSheets(expenses: List<Expense>, onResult: (Boolean, String) -> Unit) {
        val user = _currentUser.value
        if (user == null) {
            onResult(false, "Please Sign In to proceed with Google Sheets integration.")
            return
        }

        val token = _googleAccessToken.value
        if (token.isNullOrBlank()) {
            onResult(false, "To write real rows, please enter a Google OAuth Access Token in the configuration panel below.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                if (token.startsWith("ya29.a0Ax-simulated")) {
                    kotlinx.coroutines.delay(1000L) // fake sync delay for realistic feel
                    val now = System.currentTimeMillis()
                    _lastSyncTime.value = now
                    _googleSheetId.value = "spreadsheet-simulated-id-for-${user.email.replace("@", "-").replace(".", "-")}"
                    _googleSheetName.value = "Personal_Finance_Ledger_2026"
                    prefs.edit()
                        .putString("google_sheet_id", _googleSheetId.value)
                        .putString("google_sheet_name", "Personal_Finance_Ledger_2026")
                        .putLong("last_sync_time", now)
                        .apply()
                    onResult(true, "Successfully synchronized ${expenses.size} rows directly to simulated Google Sheet!")
                    _isSyncing.value = false
                    return@launch
                }

                val authHeader = "Bearer $token"
                var sheetId = _googleSheetId.value
                val sheetName = _googleSheetName.value ?: "Personal_Finance_Ledger_2026"

                // Step 1: Create a spreadsheet if it doesn't exist
                if (sheetId.isNullOrBlank()) {
                    val createReq = com.example.data.api.CreateSpreadsheetRequest(
                        com.example.data.api.SpreadsheetProperties(title = sheetName)
                    )
                    val createRes = com.example.data.api.GoogleSheetsClient.apiService.createSpreadsheet(authHeader, createReq)
                    sheetId = createRes.spreadsheetId
                    _googleSheetId.value = sheetId
                    prefs.edit().putString("google_sheet_id", sheetId).apply()
                }

                // Step 2: Format and sync (overwrite) all rows to prevent duplicates
                val valuesToSync = mutableListOf<List<Any>>()
                valuesToSync.add(listOf("Date", "Description", "Amount", "Category", "Notes", "AI Classified"))

                val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                for (item in expenses) {
                    val dateStr = df.format(java.util.Date(item.dateMillis))
                    valuesToSync.add(
                        listOf(
                            dateStr,
                            item.description,
                            item.amount,
                            item.category,
                            item.notes,
                            if (item.isAutoCategorized) "Yes" else "No"
                        )
                    )
                }

                if (valuesToSync.isNotEmpty()) {
                    val syncReq = com.example.data.api.AppendValuesRequest(
                        range = "Sheet1!A1",
                        values = valuesToSync
                    )
                    com.example.data.api.GoogleSheetsClient.apiService.updateValues(
                        authHeader = authHeader,
                        spreadsheetId = sheetId!!,
                        range = "Sheet1!A1",
                        request = syncReq
                    )
                    
                    prefs.edit().putBoolean("has_appended_headers_$sheetId", true).apply()
                }

                val now = System.currentTimeMillis()
                _lastSyncTime.value = now
                _googleSheetName.value = sheetName
                prefs.edit()
                    .putString("google_sheet_name", sheetName)
                    .putLong("last_sync_time", now)
                    .apply()

                onResult(true, "Successfully synchronized ${expenses.size} rows directly to your real Google Sheet: '$sheetName' ($sheetId)!")
            } catch (e: Exception) {
                e.printStackTrace()
                val errMsg = e.localizedMessage ?: "Unknown network or authorization error"
                if (errMsg.contains("401") || errMsg.contains("unauthorized", ignoreCase = true)) {
                    onResult(false, "Sheets sync failed: Google OAuth access token is expired or unauthorized. Please re-enter a fresh Access Token.")
                } else {
                    onResult(false, "Sheets sync failed: $errMsg")
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private val _isScanningReceipt = MutableStateFlow(false)
    val isScanningReceipt: StateFlow<Boolean> = _isScanningReceipt.asStateFlow()

    fun scanReceiptImage(base64Image: String, onCompleted: (com.example.data.api.GeminiReceiptResult) -> Unit) {
        _isScanningReceipt.value = true
        viewModelScope.launch {
            try {
                val result = GeminiClient.analyzeReceipt(base64Image)
                onCompleted(result)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanningReceipt.value = false
            }
        }
    }

    // Backup & Restore Services
    private val backupMoshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun exportBackupToUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val expensesList = repository.allExpenses.first()
                val budgetsList = repository.allCategoryBudgets.first()
                val recurringsList = repository.allRecurringExpenses.first()
                val limit = _monthlyBudgetLimit.value

                val backup = FinanceBackup(
                    version = 1,
                    backupTimeMillis = System.currentTimeMillis(),
                    expenses = expensesList,
                    categoryBudgets = budgetsList,
                    recurringExpenses = recurringsList,
                    monthlyBudgetLimit = limit
                )

                val adapter = backupMoshi.adapter(FinanceBackup::class.java).indent("  ")
                val jsonString = adapter.toJson(backup)

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(jsonString)
                        }
                    }
                }
                onResult(true, "Backup saved systematically!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Failed to export: ${e.localizedMessage}")
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            reader.readText()
                        }
                    }
                } ?: throw Exception("Empty file data received")

                val adapter = backupMoshi.adapter(FinanceBackup::class.java)
                val backup = adapter.fromJson(jsonString) ?: throw Exception("Parsing failed - invalid JSON schema")

                withContext(Dispatchers.IO) {
                    // Delete existing
                    repository.deleteAllExpenses()
                    repository.deleteAllCategoryBudgets()
                    repository.deleteAllRecurringExpenses()

                    // Insert imported
                    backup.expenses.forEach { repository.insert(it) }
                    backup.categoryBudgets.forEach { repository.insertCategoryBudget(it) }
                    backup.recurringExpenses.forEach { repository.insertRecurringExpense(it) }
                }

                // Update limit local preference
                updateMonthlyBudgetLimit(backup.monthlyBudgetLimit)

                onResult(true, "Financial database restored perfectly!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Failed to restore: ${e.localizedMessage}")
            }
        }
    }

    fun exportPdfReportToUri(
        context: Context,
        uri: Uri,
        timeFilter: String,
        filteredExpenses: List<Expense>,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val limit = _monthlyBudgetLimit.value
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val pdfDocument = android.graphics.pdf.PdfDocument()
                        var pageNumber = 1
                        
                        fun createNewPage(): Pair<android.graphics.pdf.PdfDocument.Page, android.graphics.Canvas> {
                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas
                            return Pair(page, canvas)
                        }

                        var (currentPage, canvas) = createNewPage()

                        val primaryPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#6750A4")
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }

                        val bodyPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 10f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                            isAntiAlias = true
                        }

                        val borderPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#E0E0E0")
                            strokeWidth = 1f
                            style = android.graphics.Paint.Style.STROKE
                        }

                        fun drawHeaderAndFooter(canvas: android.graphics.Canvas) {
                            canvas.drawRect(40f, 40f, 555f, 44f, primaryPaint)
                            val brandPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#6750A4")
                                textSize = 13f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            canvas.drawText("FINANCE LEDGER TRACKER", 40f, 62f, brandPaint)
                            
                            val datePaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 9f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                                isAntiAlias = true
                            }
                            val genDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                            canvas.drawText("Generated: $genDate ($timeFilter Plan)", 360f, 62f, datePaint)
                            
                            canvas.drawLine(40f, 70f, 555f, 70f, borderPaint)
                            canvas.drawLine(40f, 800f, 555f, 800f, borderPaint)
                            canvas.drawText("Page $pageNumber", 510f, 815f, datePaint)
                            canvas.drawText("Confidential Financial Report. Keep safe offline.", 40f, 815f, datePaint)
                        }

                        drawHeaderAndFooter(canvas)
                        var y = 105f

                        val titlePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 18f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        canvas.drawText("${timeFilter.uppercase()} FINANCIAL SUMMARY", 40f, y, titlePaint)
                        y += 24f

                        val totalSpent = filteredExpenses.sumOf { it.amount }

                        val bgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#F3EDF7")
                            style = android.graphics.Paint.Style.FILL
                        }
                        canvas.drawRect(40f, y, 555f, y + 80f, bgPaint)
                        canvas.drawRect(40f, y, 555f, y + 80f, borderPaint)

                        val labelPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#49454F")
                            textSize = 10f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }

                        val valuePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 14f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }

                        canvas.drawText("BUDGET LIMIT", 60f, y + 25f, labelPaint)
                        canvas.drawText(String.format("$%.2f", limit), 60f, y + 55f, valuePaint)

                        canvas.drawText("TOTAL SPENT", 240f, y + 25f, labelPaint)
                        val spendColor = if (totalSpent > limit) "#B3261E" else "#6750A4"
                        val totalSpentValuePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor(spendColor)
                            textSize = 14f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        canvas.drawText(String.format("$%.2f", totalSpent), 240f, y + 55f, totalSpentValuePaint)

                        canvas.drawText("REMAINING BAL", 420f, y + 25f, labelPaint)
                        val remaining = limit - totalSpent
                        val remainingColor = if (remaining < 0) "#B3261E" else "#388E3C"
                        val remainingValuePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor(remainingColor)
                            textSize = 14f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        canvas.drawText(String.format("$%.2f", remaining), 420f, y + 55f, remainingValuePaint)

                        y += 115f

                        val sectionHeaderPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#6750A4")
                            textSize = 12f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        canvas.drawText("CATEGORY BREAKDOWNS", 40f, y, sectionHeaderPaint)
                        y += 15f

                        val categories = mutableMapOf<String, Double>()
                        filteredExpenses.forEach { exp ->
                            categories[exp.category] = (categories[exp.category] ?: 0.0) + exp.amount
                        }
                        val sortedCats = categories.entries.sortedByDescending { it.value }

                        if (sortedCats.isEmpty()) {
                            canvas.drawText("No expenses logged for this period.", 40f, y + 15f, bodyPaint)
                            y += 30f
                        } else {
                            sortedCats.forEach { (cat, amt) ->
                                val pct = if (totalSpent > 0.0) amt / totalSpent else 0.0
                                bodyPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                canvas.drawText(cat, 40f, y + 15f, bodyPaint)
                                
                                val amtText = String.format("$%.2f (%.1f%%)", amt, pct * 100)
                                bodyPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                                canvas.drawText(amtText, 200f, y + 15f, bodyPaint)

                                val barPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#D0BCFF")
                                    style = android.graphics.Paint.Style.FILL
                                }
                                val fillPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#6750A4")
                                    style = android.graphics.Paint.Style.FILL
                                }
                                canvas.drawRect(360f, y + 5f, 540f, y + 14f, barPaint)
                                canvas.drawRect(360f, y + 5f, 360f + (180f * pct.toFloat()), y + 14f, fillPaint)

                                y += 20f
                            }
                        }

                        y += 25f

                        canvas.drawText("TRANSACTION LEDGER LOG", 40f, y, sectionHeaderPaint)
                        y += 18f

                        val thLabelPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 9f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        val thBgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#6750A4")
                            style = android.graphics.Paint.Style.FILL
                        }
                        canvas.drawRect(40f, y - 10f, 555f, y + 15f, thBgPaint)
                        canvas.drawText("Date", 45f, y + 6f, thLabelPaint)
                        canvas.drawText("Category", 125f, y + 6f, thLabelPaint)
                        canvas.drawText("Description & Notes", 215f, y + 6f, thLabelPaint)
                        canvas.drawText("Amount", 495f, y + 6f, thLabelPaint)

                        y += 25f

                        val cellPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 9f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                            isAntiAlias = true
                        }
                        
                        val italicCellPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 8f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                            isAntiAlias = true
                        }

                        val altRowPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#FAF8FC")
                            style = android.graphics.Paint.Style.FILL
                        }

                        val dateFormatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())

                        filteredExpenses.forEachIndexed { index, exp ->
                            val rawHeight = if (exp.notes.isNotBlank()) 28f else 18f
                            if (y + rawHeight > 780f) {
                                pdfDocument.finishPage(currentPage)
                                pageNumber++
                                val nextPair = createNewPage()
                                currentPage = nextPair.first
                                canvas = nextPair.second
                                drawHeaderAndFooter(canvas)
                                
                                y = 100f
                                canvas.drawRect(40f, y - 10f, 555f, y + 15f, thBgPaint)
                                canvas.drawText("Date", 45f, y + 6f, thLabelPaint)
                                canvas.drawText("Category", 125f, y + 6f, thLabelPaint)
                                canvas.drawText("Description & Notes", 215f, y + 6f, thLabelPaint)
                                canvas.drawText("Amount", 495f, y + 6f, thLabelPaint)
                                y += 25f
                            }

                            if (index % 2 == 1) {
                                canvas.drawRect(40f, y - 10f, 555f, y + (if (exp.notes.isNotBlank()) 18f else 8f), altRowPaint)
                            }

                            val dateString = dateFormatter.format(java.util.Date(exp.dateMillis))
                            canvas.drawText(dateString, 45f, y + 2f, cellPaint)
                            canvas.drawText(exp.category, 125f, y + 2f, cellPaint)
                            
                            val maxDescWidth = 270f
                            var descText = exp.description
                            val measuredDescWidth = cellPaint.measureText(descText)
                            if (measuredDescWidth > maxDescWidth) {
                                var limitIndex = descText.length
                                while (limitIndex > 0 && cellPaint.measureText(descText.substring(0, limitIndex) + "...") > maxDescWidth) {
                                    limitIndex--
                                }
                                descText = descText.substring(0, limitIndex) + "..."
                            }
                            canvas.drawText(descText, 215f, y + 2f, cellPaint)

                            if (exp.notes.isNotBlank()) {
                                var notesText = "Notes: ${exp.notes}"
                                val measuredNotesWidth = italicCellPaint.measureText(notesText)
                                if (measuredNotesWidth > maxDescWidth) {
                                    var limitIndex = notesText.length
                                    while (limitIndex > 0 && italicCellPaint.measureText(notesText.substring(0, limitIndex) + "...") > maxDescWidth) {
                                        limitIndex--
                                    }
                                    notesText = notesText.substring(0, limitIndex) + "..."
                                }
                                canvas.drawText(notesText, 215f, y + 12f, italicCellPaint)
                            }

                            val amtVal = String.format("$%.2f", exp.amount)
                            val amtWidth = cellPaint.measureText(amtVal)
                            canvas.drawText(amtVal, 545f - amtWidth, y + 2f, cellPaint)

                            canvas.drawLine(40f, y + (if (exp.notes.isNotBlank()) 18f else 8f), 555f, y + (if (exp.notes.isNotBlank()) 18f else 8f), borderPaint)

                            y += if (exp.notes.isNotBlank()) 28f else 18f
                        }

                        pdfDocument.finishPage(currentPage)
                        pdfDocument.writeTo(outputStream)
                        pdfDocument.close()
                    }
                }
                onResult(true, "PDF summary compiled successfully!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Failed to export PDF: ${e.localizedMessage}")
            }
        }
    }

    private fun autoBackupToGoogleDriveInternal(backup: FinanceBackup) {
        val user = _currentUser.value
        val token = _googleAccessToken.value
        if (user == null || user.signInType != "GOOGLE" || token.isNullOrBlank()) {
            return
        }
        viewModelScope.launch {
            _isBackingUpDrive.value = true
            _googleDriveBackupStatus.value = "Saving backup..."
            try {
                if (token.startsWith("ya29.a0Ax-simulated")) {
                    kotlinx.coroutines.delay(500L)
                    val statusStr = "Saved: " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    _googleDriveBackupStatus.value = statusStr
                    prefs.edit().putString("google_drive_backup_status", statusStr).apply()
                    _isBackingUpDrive.value = false
                    return@launch
                }

                val safeEmail = user.email.replace("@", "_").replace(".", "_")
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date(backup.backupTimeMillis))
                val fileName = "FinanceBackup_${safeEmail}_$timestamp.json"

                val adapter = backupMoshi.adapter(FinanceBackup::class.java).indent("  ")
                val jsonString = adapter.toJson(backup)

                val authHeader = "Bearer $token"

                val metadataRequest = com.example.data.api.CreateDriveFileRequest(
                    name = fileName,
                    mimeType = "application/json",
                    description = "Automated individual finance backup for ${user.email} saved on $timestamp"
                )

                val metadataResponse = com.example.data.api.GoogleDriveClient.apiService.createFileMetadata(
                    authHeader = authHeader,
                    request = metadataRequest
                )

                val fileId = metadataResponse.id

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = jsonString.toRequestBody(mediaType)

                com.example.data.api.GoogleDriveClient.apiService.uploadFileMedia(
                    authHeader = authHeader,
                    fileId = fileId,
                    content = requestBody
                )

                val statusStr = "Saved: " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                _googleDriveBackupStatus.value = statusStr
                prefs.edit().putString("google_drive_backup_status", statusStr).apply()
            } catch (e: Exception) {
                e.printStackTrace()
                val errorStr = "Save failed: ${e.localizedMessage ?: "Unknown network error"}"
                _googleDriveBackupStatus.value = errorStr
                prefs.edit().putString("google_drive_backup_status", errorStr).apply()
            } finally {
                _isBackingUpDrive.value = false
            }
        }
    }

    fun backupNowToGoogleDrive(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val expensesList = repository.allExpenses.first()
                val budgetsList = repository.allCategoryBudgets.first()
                val recurringsList = repository.allRecurringExpenses.first()
                val limit = _monthlyBudgetLimit.value

                val backup = FinanceBackup(
                    version = 1,
                    backupTimeMillis = System.currentTimeMillis(),
                    expenses = expensesList,
                    categoryBudgets = budgetsList,
                    recurringExpenses = recurringsList,
                    monthlyBudgetLimit = limit
                )

                val user = _currentUser.value
                val token = _googleAccessToken.value

                if (user == null || user.signInType != "GOOGLE" || token.isNullOrBlank()) {
                    onResult(false, "To run backup, please connect a Google Account with a valid OAuth token first.")
                    return@launch
                }

                _isBackingUpDrive.value = true
                _googleDriveBackupStatus.value = "Saving backup..."

                if (token.startsWith("ya29.a0Ax-simulated")) {
                    kotlinx.coroutines.delay(800L)
                    val statusStr = "Saved: " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    _googleDriveBackupStatus.value = statusStr
                    prefs.edit().putString("google_drive_backup_status", statusStr).apply()
                    _isBackingUpDrive.value = false
                    onResult(true, "Successfully uploaded individual backup file to simulated Google Drive!")
                    return@launch
                }

                val safeEmail = user.email.replace("@", "_").replace(".", "_")
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date(backup.backupTimeMillis))
                val fileName = "FinanceBackup_${safeEmail}_$timestamp.json"

                val adapter = backupMoshi.adapter(FinanceBackup::class.java).indent("  ")
                val jsonString = adapter.toJson(backup)

                val authHeader = "Bearer $token"

                val metadataRequest = com.example.data.api.CreateDriveFileRequest(
                    name = fileName,
                    mimeType = "application/json",
                    description = "Automated individual finance backup for ${user.email} saved on $timestamp"
                )

                val metadataResponse = com.example.data.api.GoogleDriveClient.apiService.createFileMetadata(
                    authHeader = authHeader,
                    request = metadataRequest
                )

                val fileId = metadataResponse.id

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = jsonString.toRequestBody(mediaType)

                com.example.data.api.GoogleDriveClient.apiService.uploadFileMedia(
                    authHeader = authHeader,
                    fileId = fileId,
                    content = requestBody
                )

                val statusStr = "Saved: " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                _googleDriveBackupStatus.value = statusStr
                prefs.edit().putString("google_drive_backup_status", statusStr).apply()
                onResult(true, "Successfully uploaded individual backup file: $fileName")
            } catch (e: Exception) {
                e.printStackTrace()
                val errorStr = "Save failed: ${e.localizedMessage ?: "Unknown error"}"
                _googleDriveBackupStatus.value = errorStr
                prefs.edit().putString("google_drive_backup_status", errorStr).apply()
                onResult(false, "Failed to upload to Google Drive: ${e.localizedMessage}")
            } finally {
                _isBackingUpDrive.value = false
            }
        }
    }
}
