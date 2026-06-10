package com.example.ui.view

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Expense
import com.example.data.model.CategoryBudget
import com.example.data.model.RecurringExpense
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.UserProfile
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.core.content.FileProvider
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.withStyle

// Helper mapping standard categories to modern emojis + vibrant colors
fun getCategoryMetadata(category: String): Pair<String, Color> {
    return when (category) {
        "Food & Dining" -> Pair("🍔", Color(0xFFFF9800))       // Deep Orange
        "Transportation" -> Pair("🚗", Color(0xFF03A9F4))      // Light Blue
        "Shopping" -> Pair("🛍️", Color(0xFFE91E63))            // Pink
        "Entertainment" -> Pair("🎬", Color(0xFF9C27B0))        // Purple
        "Utilities" -> Pair("💡", Color(0xFF009688))            // Teal
        "Health & Fitness" -> Pair("💪", Color(0xFF4CAF50))     // Green
        "Travel" -> Pair("✈️", Color(0xFF3F51B5))              // Indigo
        else -> Pair("💵", Color(0xFF607D8B))                  // Blue Grey / Others
    }
}

fun formatMillisToDate(millis: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
}

fun formatToINR(amount: Double): String {
    return "₹${String.format(Locale.US, "%,.2f", amount)}"
}

// Logic helper to determine if a millisecond timestamp falls within the current calendar month
fun isMillisInCurrentMonth(millis: Long): Boolean {
    val cal = Calendar.getInstance()
    val checkCal = Calendar.getInstance()
    checkCal.timeInMillis = millis
    return cal.get(Calendar.YEAR) == checkCal.get(Calendar.YEAR) &&
           cal.get(Calendar.MONTH) == checkCal.get(Calendar.MONTH)
}

fun createImageUri(context: Context): Uri {
    val tempFile = File.createTempFile("receipt_capture_", ".jpg", context.cacheDir).apply {
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}

fun getResizedBase64Image(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        
        // Scale down to max 1024 to keep payload small and fast for the API
        val maxDimension = 1024
        val width = originalBitmap.width
        val height = originalBitmap.height
        val (newWidth, newHeight) = if (width > height) {
            val ratio = width.toFloat() / maxDimension
            if (ratio > 1) {
                Pair(maxDimension, (height / ratio).toInt())
            } else {
                Pair(width, height)
            }
        } else {
            val ratio = height.toFloat() / maxDimension
            if (ratio > 1) {
                Pair((width / ratio).toInt(), maxDimension)
            } else {
                Pair(width, height)
            }
        }
        
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.filteredExpenses.collectAsStateWithLifecycle()
    val allExpensesList by viewModel.allExpenses.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val budgetLimit by viewModel.monthlyBudgetLimit.collectAsStateWithLifecycle()
    val isCategorizing by viewModel.isCategorizing.collectAsStateWithLifecycle()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val googleSheetId by viewModel.googleSheetId.collectAsStateWithLifecycle()
    val googleSheetName by viewModel.googleSheetName.collectAsStateWithLifecycle()
    val isSyncingSheets by viewModel.isSyncingSheets.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val googleAccessToken by viewModel.googleAccessToken.collectAsStateWithLifecycle()

    val isFetchingSheetsData by viewModel.isFetchingSheetsData.collectAsStateWithLifecycle()
    val sheetsCategorySpending by viewModel.sheetsCategorySpending.collectAsStateWithLifecycle()
    val sheetsTotalSpending by viewModel.sheetsTotalSpending.collectAsStateWithLifecycle()
    val sheetsDashboardError by viewModel.sheetsDashboardError.collectAsStateWithLifecycle()
    val isViewingLiveDashboard by viewModel.isViewingLiveDashboard.collectAsStateWithLifecycle()

    val isGoogleDriveBackupEnabled by viewModel.isGoogleDriveBackupEnabled.collectAsStateWithLifecycle()
    val googleDriveBackupStatus by viewModel.googleDriveBackupStatus.collectAsStateWithLifecycle()
    val isBackingUpDrive by viewModel.isBackingUpDrive.collectAsStateWithLifecycle()

    var showAuthDialog by remember { mutableStateOf(false) }

    // Retrieve custom category budgets and recurring expenses from state Flows
    val categoryBudgetsList by viewModel.allCategoryBudgets.collectAsStateWithLifecycle()
    val recurringExpensesList by viewModel.allRecurringExpenses.collectAsStateWithLifecycle()

    // Selected navigation tab state (0 = Overview, 1 = Budgets, 2 = Reports, 3 = Recurring)
    var currentTab by remember { mutableStateOf(0) }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showAddManualDialog by remember { mutableStateOf(false) }

    // Form inputs for quick/add state
    var descriptionInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food & Dining") }
    var notesInput by remember { mutableStateOf("") }
    var isManualEditActive by remember { mutableStateOf(false) }

    val isScanningReceipt by viewModel.isScanningReceipt.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var scannedImageUriForPreview by remember { mutableStateOf<Uri?>(null) }
    var showScanConfirmationDialog by remember { mutableStateOf(false) }
    var scannedReceiptResult by remember { mutableStateOf<com.example.data.api.GeminiReceiptResult?>(null) }
    var showScanOptionsDialog by remember { mutableStateOf(false) }

    // Launcher for taking pictures via Camera
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = cameraImageUri
            if (uri != null) {
                val base64 = getResizedBase64Image(context, uri)
                if (base64 != null) {
                    scannedImageUriForPreview = uri
                    viewModel.scanReceiptImage(base64) { result ->
                        scannedReceiptResult = result
                        showScanConfirmationDialog = true
                    }
                }
            }
        }
    }

    // Launcher for picking images from Gallery
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val base64 = getResizedBase64Image(context, uri)
            if (base64 != null) {
                scannedImageUriForPreview = uri
                viewModel.scanReceiptImage(base64) { result ->
                    scannedReceiptResult = result
                    showScanConfirmationDialog = true
                }
            }
        }
    }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val uri = createImageUri(context)
                cameraImageUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to initialize camera capture file", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Camera permission is required to snapshot paper receipts.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var showBackupDialog by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var showCalculatorDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri) { success, message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                if (success) {
                    showBackupDialog = false
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(context, uri) { success, message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                if (success) {
                    showBackupDialog = false
                }
            }
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    val categoriesList = listOf(
        "Food & Dining",
        "Transportation",
        "Shopping",
        "Entertainment",
        "Utilities",
        "Health & Fitness",
        "Travel",
        "Others"
    )

    // Calculate totals for current month
    val currentMonthExpenses = remember(allExpensesList) {
        allExpensesList.filter { isMillisInCurrentMonth(it.dateMillis) }
    }

    val totalSpentCurrentMonth = remember(currentMonthExpenses) {
        currentMonthExpenses.sumOf { it.amount }
    }

    val totalSpentAcrossTime = remember(allExpensesList) {
        allExpensesList.sumOf { it.amount }
    }

    val budgetRatio = remember(totalSpentCurrentMonth, budgetLimit) {
        if (budgetLimit > 0) (totalSpentCurrentMonth / budgetLimit).toFloat() else 0f
    }

    // Spend distribution by category for current month
    val currentMonthSpentByCategory = remember(currentMonthExpenses) {
        val distribution = categoriesList.associateWith { 0.0 }.toMutableMap()
        currentMonthExpenses.forEach { exp ->
            val prev = distribution[exp.category] ?: 0.0
            distribution[exp.category] = prev + exp.amount
        }
        distribution
    }

    // Top month category spending card
    val topCategoryAndAmount = remember(currentMonthSpentByCategory) {
        val maxEntry = currentMonthSpentByCategory.filter { it.value > 0.0 }.maxByOrNull { it.value }
        if (maxEntry != null) {
            Pair(maxEntry.key, maxEntry.value)
        } else {
            Pair("None", 0.0)
        }
    }

    // Dynamic alerts computation: check category budget exceedance
    val activeBentoAlerts = remember(currentMonthSpentByCategory, categoryBudgetsList) {
        val alerts = mutableListOf<String>()
        categoryBudgetsList.forEach { budget ->
            val spent = currentMonthSpentByCategory[budget.category] ?: 0.0
            if (budget.isAlertEnabled && budget.limitAmount > 0.0) {
                val ratio = spent / budget.limitAmount
                if (ratio >= 1.0) {
                    alerts.add("🚨 [Over-limit] ${budget.category} limit of ${formatToINR(budget.limitAmount)} exceeded! You spent ${formatToINR(spent)}.")
                } else if (ratio >= 0.85) {
                    val pct = (ratio * 100).toInt()
                    alerts.add("⚠️ [Close-to] ${budget.category} budget of ${formatToINR(budget.limitAmount)} is $pct% spent (Spent ${formatToINR(spent)}).")
                }
            }
        }
        alerts
    }

    Scaffold(
        modifier = modifier.testTag("expense_scaffold"),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showAuthDialog = true }
                                .padding(vertical = 4.dp)
                                .testTag("welcome_profile_header")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (currentUser?.signInType == "GOOGLE") 
                                            Color(0xFFE2F0D9) 
                                        else 
                                            MaterialTheme.colorScheme.secondaryContainer, 
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentUser?.signInType == "GOOGLE") {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color(0xFF1E4620)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "User avatar",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (currentUser != null) "Logged in as," else "Welcome back,",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = currentUser?.name ?: "Guest User (Sign In)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
                            IconButton(
                                onClick = { viewModel.toggleDarkMode() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (isDarkMode) Color(0xFF2E2A35) else Color(0xFFF3EDF7),
                                        shape = CircleShape
                                    )
                                    .testTag("theme_toggle_button")
                            ) {
                                if (isDarkMode) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Switch to Light Mode",
                                        tint = Color(0xFFFFD600),
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Switch to Dark Mode",
                                        tint = Color(0xFF6750A4),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showCalculatorDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (isDarkMode) Color(0xFF2E2A35) else Color(0xFFF3EDF7),
                                        shape = CircleShape
                                    )
                                    .testTag("calculator_trigger_button")
                            ) {
                                Text(
                                    text = "🧮",
                                    fontSize = 17.sp
                                )
                            }

                            IconButton(
                                onClick = { showBackupDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (isDarkMode) Color(0xFF2E2A35) else Color(0xFFF3EDF7),
                                        shape = CircleShape
                                    )
                                    .testTag("backup_trigger_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Backup & Restore options",
                                    tint = if (isDarkMode) Color(0xFFD0BCFF) else Color(0xFF6750A4),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = when (currentTab) {
                                        0 -> "BENTO OVERVIEW"
                                        1 -> "BUDGETS HUB"
                                        2 -> "ANALYTICS & REPORTS"
                                        3 -> "AUTOPAY BILLS"
                                        else -> "INTELLIGENT AI EXPERT"
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (currentTab == 0) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                showScanOptionsDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            icon = { Icon(Icons.Default.Star, contentDescription = "Scan Receipt", tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                        text = { Text("Scan Receipt", fontSize = 12.sp, fontWeight = FontWeight.Black) },
                        modifier = Modifier.testTag("scan_receipt_fab")
                    )

                    FloatingActionButton(
                        onClick = {
                            isManualEditActive = false
                            descriptionInput = ""
                            amountInput = ""
                            selectedCategory = "Food & Dining"
                            notesInput = ""
                            showAddManualDialog = true
                        },
                        modifier = Modifier.testTag("add_expense_fab"),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add transaction manually")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Overview") },
                    label = { Text("Overview", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Budgets") },
                    label = { Text("Budgets", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Reports") },
                    label = { Text("Visual Reports", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Recurring") },
                    label = { Text("Autopay", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Face, contentDescription = "AI Advisor") },
                    label = { Text("AI Advisor", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                0 -> {
                    // TAB 0: Bento Overview Dashboard
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Display Active On-screen Budget Alerts / Banners
                        if (activeBentoAlerts.isNotEmpty()) {
                            item {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    activeBentoAlerts.forEach { alert ->
                                        val isOver = alert.contains("[Over-limit]")
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isOver) Color(0xFFFFDAD6) else Color(0xFFFFF2CC)
                                            ),
                                            border = BorderStroke(1.dp, if (isOver) Color(0xFFF1B0B0) else Color(0xFFF1DAB0))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = alert,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOver) Color(0xFF410002) else Color(0xFF5D4000),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Total Month's Spend Card
                        item {
                            BudgetSummaryDashboardCard(
                                totalSpent = totalSpentCurrentMonth,
                                budgetLimit = budgetLimit,
                                ratio = budgetRatio,
                                onEditBudgetClick = { showBudgetDialog = true }
                            )
                        }

                        // Asymmetric Middle Bento Grid Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RemainingBudgetBentoCard(
                                    modifier = Modifier.weight(1f),
                                    remainingAmount = budgetLimit - totalSpentCurrentMonth,
                                    budgetRatio = budgetRatio,
                                    budgetLimit = budgetLimit
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HighestCategoryBentoCard(
                                        categoryName = topCategoryAndAmount.first,
                                        categoryValue = topCategoryAndAmount.second
                                    )
                                    AIEngineStatusBentoCard(
                                        isCategorizing = isCategorizing
                                    )
                                }
                            }
                        }

                        // AI NLP Quick Assistant Card
                        item {
                            AISmartAssistantCard(
                                descriptionText = descriptionInput,
                                amountText = amountInput,
                                categoryText = selectedCategory,
                                notesText = notesInput,
                                isCategorizing = isCategorizing,
                                onDescriptionChange = { descriptionInput = it },
                                onAmountChange = { amountInput = it },
                                onCategorySelect = { selectedCategory = it },
                                onNotesChange = { notesInput = it },
                                categories = categoriesList,
                                onAutoCategorize = {
                                    viewModel.autoCategorizeDescription(descriptionInput) { predictedCategory, predictedAmount ->
                                        selectedCategory = predictedCategory
                                        if (predictedAmount != null) {
                                            amountInput = String.format(Locale.US, "%.2f", predictedAmount)
                                        }
                                    }
                                },
                                onSaveExpense = {
                                    val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
                                    if (descriptionInput.isNotBlank() && parsedAmount > 0.0) {
                                        viewModel.addExpense(
                                            description = descriptionInput,
                                            amount = parsedAmount,
                                            category = selectedCategory,
                                            dateMillis = System.currentTimeMillis(),
                                            isAutoCategorized = !isCategorizing,
                                            notes = notesInput
                                        )
                                        descriptionInput = ""
                                        amountInput = ""
                                        selectedCategory = "Food & Dining"
                                        notesInput = ""
                                        keyboardController?.hide()
                                    }
                                }
                            )
                        }

                        // Automatic Intelligent Analysis & Insights (Adding/Spending advisory)
                        item {
                            AutomaticAnalysisHubCard(
                                totalSpent = totalSpentCurrentMonth,
                                budgetLimit = budgetLimit,
                                topCategoryAndAmount = topCategoryAndAmount,
                                expenses = currentMonthExpenses,
                                onAddMoneyClick = { showBudgetDialog = true }
                            )
                        }

                        // Google Sheets Cloud Integration
                        item {
                            GoogleSheetsSyncCard(
                                currentUser = currentUser,
                                googleSheetId = googleSheetId,
                                googleSheetName = googleSheetName,
                                googleAccessToken = googleAccessToken,
                                isSyncing = isSyncingSheets,
                                lastSyncTime = lastSyncTime,
                                expensesCount = allExpensesList.size,
                                onSyncClick = {
                                    viewModel.syncToGoogleSheets(allExpensesList) { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                onConnectClick = { showAuthDialog = true },
                                onSaveToken = { viewModel.saveGoogleAccessToken(it) }
                            )
                        }

                        // Google Drive Automatic Ledger Backup
                        item {
                            GoogleDriveBackupCard(
                                isBackupEnabled = isGoogleDriveBackupEnabled,
                                backupStatus = googleDriveBackupStatus,
                                isBackingUp = isBackingUpDrive,
                                isGoogleConnected = currentUser?.signInType == "GOOGLE" && !googleAccessToken.isNullOrBlank(),
                                onToggleBackup = { viewModel.toggleGoogleDriveBackup(it) },
                                onBackupNowClick = {
                                    viewModel.backupNowToGoogleDrive { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }

                        // Google Sheets Dashboard visualization component using native charts
                        item {
                            GoogleSheetsDashboardCard(
                                isLive = isViewingLiveDashboard,
                                liveSpending = sheetsCategorySpending,
                                liveTotal = sheetsTotalSpending,
                                offlineExpenses = allExpensesList,
                                isFetchingLive = isFetchingSheetsData,
                                fetchError = sheetsDashboardError,
                                isGoogleConnected = currentUser?.signInType == "GOOGLE" && !googleSheetId.isNullOrBlank() && !googleAccessToken.isNullOrBlank(),
                                onFetchLiveClick = {
                                    viewModel.fetchAndAnalyzeSheetsData { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                onToggleMode = { viewModel.toggleDashboardMode(it) }
                            )
                        }

                        // Ledger Header
                        item {
                            Text(
                                text = "Recent Transactions Ledger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // Filters and Ledger Search Block
                        item {
                            SearchAndCategoryFilters(
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                selectedFilter = selectedFilter,
                                onFilterChange = { viewModel.selectCategoryFilter(it) },
                                categories = categoriesList
                            )
                        }

                        // Transactions items
                        if (expenses.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    hasFiltersActive = searchQuery.isNotEmpty() || selectedFilter != null,
                                    onClearFiltersClick = {
                                        viewModel.setSearchQuery("")
                                        viewModel.selectCategoryFilter(null)
                                    }
                                )
                            }
                        } else {
                            items(expenses, key = { it.id }) { expense ->
                                ExpenseLedgerItem(
                                    expense = expense,
                                    onDeleteClick = { expenseToDelete = expense }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }
                1 -> {
                    // TAB 1: Category Budgets Hub
                    var setCategory by remember { mutableStateOf("Food & Dining") }
                    var setLimitAmount by remember { mutableStateOf("") }
                    var areAlertsChecked by remember { mutableStateOf(true) }
                    var budgetDropdownExpanded by remember { mutableStateOf(false) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = "Define Category Budget Limits",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Keep spending aligned with envelopes. Enable alerts to get highlighted warnings upon approach.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        // Set/Edit Category Limit Box
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                border = CardDefaults.outlinedCardBorder(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "SET CATEGORY LIMIT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.8.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // Dropdown Selector for Category to Limit
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedCard(
                                            onClick = { budgetDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val (emoji, color) = getCategoryMetadata(setCategory)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(emoji, fontSize = 16.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(setCategory, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = budgetDropdownExpanded,
                                            onDismissRequest = { budgetDropdownExpanded = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            categoriesList.forEach { cat ->
                                                val (emoji, _) = getCategoryMetadata(cat)
                                                DropdownMenuItem(
                                                    text = { Text("$emoji $cat", fontWeight = FontWeight.Bold) },
                                                    onClick = {
                                                        setCategory = cat
                                                        budgetDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Amount Input TextField
                                    OutlinedTextField(
                                        value = setLimitAmount,
                                        onValueChange = { setLimitAmount = it },
                                        placeholder = { Text("e.g. 15000") },
                                        label = { Text("Monthly Envelope Limit (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Under-threshold optional alerts toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Enable alerts at 85% spent thresholds", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Switch(
                                            checked = areAlertsChecked,
                                            onCheckedChange = { areAlertsChecked = it }
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val validLimit = setLimitAmount.toDoubleOrNull() ?: 0.0
                                            if (validLimit > 0.0) {
                                                viewModel.addOrUpdateCategoryBudget(
                                                    category = setCategory,
                                                    limitAmount = validLimit,
                                                    isAlertEnabled = areAlertsChecked
                                                )
                                                setLimitAmount = ""
                                                keyboardController?.hide()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        enabled = setLimitAmount.toDoubleOrNull() != null
                                    ) {
                                        Text("APPLY ENVELOPE BUDGET", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }

                        // Title lists
                        item {
                            Text(
                                text = "Envelope Adherence Overview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (categoryBudgetsList.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(20.dp),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("🎨", fontSize = 36.sp)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("No Category Specific Budgets Set", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            "Envelope budgets help restrict spending on individual categories like groceries or shopping.",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(categoryBudgetsList, key = { it.category }) { budget ->
                                val spent = currentMonthSpentByCategory[budget.category] ?: 0.0
                                val adherenceRatio = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
                                val isOverBudget = spent >= budget.limitAmount
                                val isNearBudget = !isOverBudget && spent >= budget.limitAmount * 0.85

                                val (emoji, color) = getCategoryMetadata(budget.category)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(24.dp),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(emoji, fontSize = 16.sp)
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(budget.category, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(
                                                        text = "Monthly limit: ${formatToINR(budget.limitAmount)}",
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                // Alert toggler icon
                                                IconButton(
                                                    onClick = {
                                                        viewModel.addOrUpdateCategoryBudget(
                                                            category = budget.category,
                                                            limitAmount = budget.limitAmount,
                                                            isAlertEnabled = !budget.isAlertEnabled
                                                        )
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = "Toggle alerts",
                                                        tint = if (budget.isAlertEnabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                // Deletion icon
                                                IconButton(
                                                    onClick = { viewModel.deleteCategoryBudget(budget) }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete budget limit",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Horizontal bar gauge
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFE6E0E9))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(adherenceRatio.coerceIn(0f, 1f))
                                                    .background(
                                                        when {
                                                            isOverBudget -> Color(0xFFB3261E)
                                                            isNearBudget -> Color(0xFFFF9800)
                                                            else -> Color(0xFF4CAF50)
                                                        }
                                                    )
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Spent ${formatToINR(spent)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = when {
                                                    isOverBudget -> Color(0xFFB3261E)
                                                    isNearBudget -> Color(0xFFFF9800)
                                                    else -> Color(0xFF4CAF50)
                                                }
                                            )

                                            val percentage = (adherenceRatio * 100).toInt()
                                            Text(
                                                text = "$percentage% exhausted",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }
                2 -> {
                    // TAB 2: Dynamic Insights & Reporting Visualizer
                    var timeFilter by remember { mutableStateOf("Monthly") } // "Weekly" / "Monthly" / "Yearly"

                    // Calculate expenses for selected period
                    val sortedSelectedPeriodExpenses = remember(allExpensesList, timeFilter) {
                        val durationLimit = when (timeFilter) {
                            "Weekly" -> 7 * 24 * 60 * 60 * 1000L
                            "Monthly" -> 30 * 24 * 60 * 60 * 1000L
                            "Yearly" -> 365 * 24 * 60 * 60 * 1000L
                            else -> 30 * 24 * 60 * 60 * 1000L
                        }
                        allExpensesList.filter { (System.currentTimeMillis() - it.dateMillis) <= durationLimit }
                    }

                    // Distribution of category spending for report
                    val periodSpentByCategory = remember(sortedSelectedPeriodExpenses) {
                        val distribution = mutableMapOf<String, Double>()
                        sortedSelectedPeriodExpenses.forEach { exp ->
                            val prev = distribution[exp.category] ?: 0.0
                            distribution[exp.category] = prev + exp.amount
                        }
                        // Sort by spending descending
                        distribution.entries.sortedByDescending { it.value }
                            .associate { it.key to it.value }
                    }

                    val totalSpentPeriod = remember(periodSpentByCategory) {
                        periodSpentByCategory.values.sum()
                    }

                    val maxSpendInPeriod = remember(periodSpentByCategory) {
                        periodSpentByCategory.values.maxOrNull() ?: 0.0
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Spend Distribution Visual Hub",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Analyze category concentrations and visual statistics over periods of time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }

                                val pdfLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                                ) { uri ->
                                    if (uri != null) {
                                        viewModel.exportPdfReportToUri(
                                            context = context,
                                            uri = uri,
                                            timeFilter = timeFilter,
                                            filteredExpenses = sortedSelectedPeriodExpenses
                                        ) { success, message ->
                                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        pdfLauncher.launch("Finance_${timeFilter}_Report_${System.currentTimeMillis()}.pdf")
                                    },
                                    modifier = Modifier.testTag("export_pdf_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Export PDF Report",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Weekly, Monthly, Yearly duration selector
                        item {
                            TabRow(
                                selectedTabIndex = when (timeFilter) {
                                    "Weekly" -> 0
                                    "Monthly" -> 1
                                    else -> 2
                                },
                                containerColor = Color(0xFFF3EDF7),
                                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            ) {
                                Tab(
                                    selected = timeFilter == "Weekly",
                                    onClick = { timeFilter = "Weekly" },
                                    text = { Text("Weekly", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                )
                                Tab(
                                    selected = timeFilter == "Monthly",
                                    onClick = { timeFilter = "Monthly" },
                                    text = { Text("Monthly", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                )
                                Tab(
                                    selected = timeFilter == "Yearly",
                                    onClick = { timeFilter = "Yearly" },
                                    text = { Text("Yearly", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                )
                            }
                        }

                        // Pie/Donut Visual Card Bento Block
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                border = CardDefaults.outlinedCardBorder(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "${timeFilter.uppercase(Locale.US)} CATEGORICAL PROPORTIONS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    BentoDonutChart(
                                        categoryTotals = periodSpentByCategory,
                                        total = totalSpentPeriod
                                    )
                                }
                            }
                        }

                        // Bar Graph Visual Card Bento Block
                        if (totalSpentPeriod > 0.0) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp),
                                    border = CardDefaults.outlinedCardBorder(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            text = "SPENDING INTENSITY GRAPH",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.5.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        BentoBarChart(
                                            categoryTotals = periodSpentByCategory,
                                            maxAmount = maxSpendInPeriod
                                        )
                                    }
                                }
                            }
                        }

                        // Algorithmic Insights Card matching spending concentrations
                        if (totalSpentPeriod > 0.0) {
                            item {
                                val topCatName = periodSpentByCategory.keys.firstOrNull() ?: ""
                                val topCatAmt = periodSpentByCategory.values.firstOrNull() ?: 0.0
                                val topPct = (topCatAmt / totalSpentPeriod) * 100

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color(0xFF21005D)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "AI SPENDING INSIGHT",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp,
                                                color = Color(0xFF21005D),
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "Your main expenditure this week is $topCatName indicating ${topPct.toInt()}% of the total. Consider tracking item micro-transactions or limits to suppress impulses.",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF21005D)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }
                3 -> {
                    // TAB 3: Recurring Expenses Autoplay Scheduler Planner
                    var recDescription by remember { mutableStateOf("") }
                    var recAmount by remember { mutableStateOf("") }
                    var recCategory by remember { mutableStateOf("Food & Dining") }
                    var recFrequency by remember { mutableStateOf("Monthly") } // "Weekly", "Monthly", "Yearly"
                    var recNotes by remember { mutableStateOf("") }

                    var categoryDropdownRec by remember { mutableStateOf(false) }
                    var frequencyDropdownRec by remember { mutableStateOf(false) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text(
                                text = "Autopay Recurring Expenses Scheduler",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Define automatic bills, subscription schedules, or custom regular transactions. The background daemon logs transactions on respective due timings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        // Create Recurring Form Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                border = CardDefaults.outlinedCardBorder(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "NEW SCHEDULED RECURRING TRANSACTION",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // Description OutlinedTextField
                                    OutlinedTextField(
                                        value = recDescription,
                                        onValueChange = { recDescription = it },
                                        placeholder = { Text("e.g. Netflix Subscription") },
                                        label = { Text("Subscription / Transaction Details") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Row with Amount and Frequency
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = recAmount,
                                            onValueChange = { recAmount = it },
                                            placeholder = { Text("₹ 499") },
                                            label = { Text("Amount (₹)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1.1f)
                                        )

                                        // Frequency Dropdown
                                        Box(modifier = Modifier.weight(0.9f)) {
                                            OutlinedCard(
                                                onClick = { frequencyDropdownRec = true },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.padding(top = 8.dp) // align visually
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 14.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(recFrequency, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = frequencyDropdownRec,
                                                onDismissRequest = { frequencyDropdownRec = false }
                                            ) {
                                                listOf("Weekly", "Monthly", "Yearly").forEach { freq ->
                                                    DropdownMenuItem(
                                                        text = { Text(freq, fontWeight = FontWeight.Bold) },
                                                        onClick = {
                                                            recFrequency = freq
                                                            frequencyDropdownRec = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Category dropdown selector
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedCard(
                                            onClick = { categoryDropdownRec = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val (emoji, _) = getCategoryMetadata(recCategory)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(emoji)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(recCategory, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = categoryDropdownRec,
                                            onDismissRequest = { categoryDropdownRec = false },
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            categoriesList.forEach { cat ->
                                                val (emoji, _) = getCategoryMetadata(cat)
                                                DropdownMenuItem(
                                                    text = { Text("$emoji $cat", fontWeight = FontWeight.Bold) },
                                                    onClick = {
                                                        recCategory = cat
                                                        categoryDropdownRec = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Notes Optional
                                    OutlinedTextField(
                                        value = recNotes,
                                        onValueChange = { recNotes = it },
                                        placeholder = { Text("Auto-debited from Credit Card") },
                                        label = { Text("Schedule Notes (Optional)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            val validAmount = recAmount.toDoubleOrNull() ?: 0.0
                                            if (recDescription.isNotBlank() && validAmount > 0.0) {
                                                viewModel.addRecurringExpense(
                                                    description = recDescription,
                                                    amount = validAmount,
                                                    category = recCategory,
                                                    frequency = recFrequency,
                                                    notes = recNotes
                                                )
                                                recDescription = ""
                                                recAmount = ""
                                                recNotes = ""
                                                keyboardController?.hide()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        enabled = recDescription.isNotBlank() && recAmount.toDoubleOrNull() != null
                                    ) {
                                        Text("SCHEDULE AUTOPAY TRANSACTION", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }

                        // List active schedulers
                        item {
                            Text(
                                text = "Active Scheduled Envelopes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (recurringExpensesList.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(20.dp),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("⏳", fontSize = 36.sp)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("No Active Subscriptions Scheduled", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            "Any bills or mutual funds added here automatically log expenses as soon as respective periods pass.",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(recurringExpensesList, key = { it.id }) { rec ->
                                val (emoji, color) = getCategoryMetadata(rec.category)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(24.dp),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(emoji, fontSize = 20.sp)
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column {
                                                Text(rec.description, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                                Text(
                                                    text = "${rec.frequency} Billing • next soon",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                                if (rec.notes.isNotBlank()) {
                                                    Text(
                                                        text = rec.notes,
                                                        fontSize = 11.sp,
                                                        color = Color.Gray.copy(alpha = 0.8f),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = formatToINR(rec.amount),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp
                                            )

                                            IconButton(
                                                onClick = { viewModel.deleteRecurringExpense(rec) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Cancel Schedule",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }
                4 -> {
                    AiAdvisorScreen(viewModel)
                }
            }
        }
    }

    // Modal Dialog: Delete Confirmation
    if (expenseToDelete != null) {
        val exp = expenseToDelete!!
        DeleteConfirmationDialog(
            expense = exp,
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                viewModel.deleteExpense(exp)
                expenseToDelete = null
                android.widget.Toast.makeText(context, "Transaction deleted", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal Dialog: Standalone Finance Calculator
    if (showCalculatorDialog) {
        FinanceCalculatorDialog(
            initialValue = amountInput,
            onDismiss = { showCalculatorDialog = false },
            onApply = { evaluatedResult ->
                amountInput = evaluatedResult
                showCalculatorDialog = false
                android.widget.Toast.makeText(context, "Calculated amount applied to Quick Add!", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal Dialog: Update Monthly Limit
    if (showBudgetDialog) {
        BudgetLimitDialog(
            currentLimit = budgetLimit,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { newLimit ->
                viewModel.updateMonthlyBudgetLimit(newLimit)
                showBudgetDialog = false
            }
        )
    }

    // Modal Dialog: Backup & Restore
    if (showBackupDialog) {
        BackupRestoreDialog(
            expensesCount = allExpensesList.size,
            budgetsCount = categoryBudgetsList.size,
            recurringCount = recurringExpensesList.size,
            onDismiss = { showBackupDialog = false },
            onExportBackup = {
                exportBackupLauncher.launch("financial_backup_${System.currentTimeMillis()}.json")
            },
            onImportBackup = {
                importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream"))
            }
        )
    }

    // Modal Dialog: Google Account Sign-In / Sign-Up profile settings
    if (showAuthDialog) {
        AuthModalDialog(
            currentUser = currentUser,
            onDismiss = { showAuthDialog = false },
            onGoogleSignIn = { email, name -> viewModel.signInWithGoogle(email, name) },
            onSignUp = { email, name, pword -> viewModel.signUp(email, name, pword) },
            onSignIn = { email, pword -> viewModel.signIn(email, pword) },
            onLogOut = { viewModel.logOut() }
        )
    }

    // Modal Dialog: Add/Edit Manually
    if (showAddManualDialog) {
        AddManualExpenseDialog(
            initialDescription = descriptionInput,
            initialAmount = amountInput,
            initialCategory = selectedCategory,
            initialNotes = notesInput,
            categories = categoriesList,
            onDismiss = { showAddManualDialog = false },
            onConfirm = { desc, amt, cat, notes ->
                viewModel.addExpense(
                    description = desc,
                    amount = amt,
                    category = cat,
                    dateMillis = System.currentTimeMillis(),
                    isAutoCategorized = false,
                    notes = notes
                )
                showAddManualDialog = false
            }
        )
    }

    // Modal Dialog: Scan Choose Platform (Camera or Gallery)
    if (showScanOptionsDialog) {
        Dialog(onDismissRequest = { showScanOptionsDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Scan Receipt Image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Select scan source to scan your paper receipt. Gemini will automatically extract description, amount, category, and logging notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Camera Option Button
                        Button(
                            onClick = {
                                showScanOptionsDialog = false
                                val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CAMERA
                                )
                                if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        val uri = createImageUri(context)
                                        cameraImageUri = uri
                                        takePictureLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Camera")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Gallery Option Button
                        OutlinedButton(
                            onClick = {
                                showScanOptionsDialog = false
                                pickImageLauncher.launch("image/*")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Gallery", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Cancel button
                    TextButton(
                        onClick = { showScanOptionsDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Scanning Analysis Overlay
    if (isScanningReceipt) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Analyzing Receipt with Gemini AI...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Extracting merchant, total amount, category, and items...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Modal Confirmation Dialog: Confirm Scanned Data
    if (showScanConfirmationDialog && scannedReceiptResult != null) {
        val result = scannedReceiptResult!!
        var confirmDesc by remember(result) { mutableStateOf(result.description) }
        var confirmAmt by remember(result) { mutableStateOf(result.amount.toString()) }
        var confirmCat by remember(result) { mutableStateOf(result.category) }
        var confirmNotes by remember(result) { mutableStateOf(result.notes) }

        Dialog(onDismissRequest = { showScanConfirmationDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE8DEF8), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF6750A4),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Confirm Scanned Data",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Gemini AI extracted these transaction details from your receipt snapshot. Tap to edit any inaccuracies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = confirmDesc,
                            onValueChange = { confirmDesc = it },
                            label = { Text("Merchant / Description") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = confirmAmt,
                            onValueChange = { confirmAmt = it },
                            label = { Text("Total Amount (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(
                            text = "Category",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categoriesList.forEach { categoryName ->
                                val (emoji, _) = getCategoryMetadata(categoryName)
                                val isSelected = confirmCat == categoryName
                                SuggestionChip(
                                    onClick = { confirmCat = categoryName },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(emoji, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(categoryName, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    colors = if (isSelected) {
                                        SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    } else {
                                        SuggestionChipDefaults.suggestionChipColors()
                                    }
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = confirmNotes,
                            onValueChange = { confirmNotes = it },
                            label = { Text("Receipt Details / Notes") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showScanConfirmationDialog = false }
                            ) {
                                Text("Discard", fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val finalAmount = confirmAmt.toDoubleOrNull() ?: 0.0
                                    viewModel.addExpense(
                                        description = confirmDesc,
                                        amount = finalAmount,
                                        category = confirmCat,
                                        dateMillis = System.currentTimeMillis(),
                                        isAutoCategorized = true,
                                        notes = confirmNotes
                                    )
                                    val isGoogleUser = currentUser?.signInType == "GOOGLE" && !googleAccessToken.isNullOrBlank()
                                    if (isGoogleUser) {
                                        android.widget.Toast.makeText(context, "Receipt saved! Auto-syncing row to Google Sheets cloud ledger in the background...", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Receipt saved locally! Connect Google Sheets in settings to sync.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    showScanConfirmationDialog = false
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Approve & Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetSummaryDashboardCard(
    totalSpent: Double,
    budgetLimit: Double,
    ratio: Float,
    onEditBudgetClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL MONTH'S SPEND",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                
                Box(
                    modifier = Modifier
                        .background(Color(0xFFD0BCFF), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "+12% vs last month",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatToINR(totalSpent),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    letterSpacing = (-0.5).sp
                )
                
                IconButton(
                    onClick = onEditBudgetClick,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Edit budget limit",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RemainingBudgetBentoCard(
    modifier: Modifier = Modifier,
    remainingAmount: Double,
    budgetRatio: Float,
    budgetLimit: Double
) {
    val remaining = remainingAmount
    val isOver = remaining < 0
    Card(
        modifier = modifier.height(136.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFFDAD6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = Color(0xFF410002),
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Column {
                Text(
                    text = if (isOver) "Over Budget" else "Remaining Budget",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatToINR(Math.abs(remaining)),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOver) MaterialTheme.colorScheme.error else Color(0xFF6750A4)
                )
            }

            // Elegant capsule progress bar
            val progress = (1f - budgetRatio).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE6E0E9))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (isOver) 1.0f else progress)
                        .background(
                            if (isOver) MaterialTheme.colorScheme.error 
                            else Color(0xFFB3261E)
                        )
                )
            }
        }
    }
}

@Composable
fun HighestCategoryBentoCard(
    modifier: Modifier = Modifier,
    categoryName: String,
    categoryValue: Double
) {
    val (emoji, color) = getCategoryMetadata(categoryName)
    Card(
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EDF7)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "TOP EXPENSE",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = if (categoryName == "None") "No spend yet" else "$categoryName (${formatToINR(categoryValue)})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D192B),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AIEngineStatusBentoCard(
    modifier: Modifier = Modifier,
    isCategorizing: Boolean
) {
    Card(
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EDF7)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFE8DEF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isCategorizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF1D192B)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF1D192B),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "AI SMART TAG",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = if (isCategorizing) "Classifying..." else "Engine Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D192B)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISmartAssistantCard(
    descriptionText: String,
    amountText: String,
    categoryText: String,
    notesText: String,
    isCategorizing: Boolean,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    categories: List<String>,
    onAutoCategorize: () -> Unit,
    onSaveExpense: () -> Unit
) {
    var exCategoryDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_assistant_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "AI helper star",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "AI NATURAL INPUT ASSISTANT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Write description, AI fills category & amount",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text input details
            OutlinedTextField(
                value = descriptionText,
                onValueChange = onDescriptionChange,
                label = { Text("What did you buy? / description") },
                placeholder = { Text("e.g. bought organic paneer for ₹350") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_description_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // AI Predict smart trigger button
            OutlinedButton(
                onClick = onAutoCategorize,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("ai_predict_button"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCategorizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI is processing details...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Star, contentDescription = "Auto-tag", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AUTO FILL CATEGORY & AMOUNT", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Predicted Outputs rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Predicted Amount field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_amount_output"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Predicted Category field dropdown
                Box(
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedCard(
                        onClick = { exCategoryDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp), // match label alignment visually
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (emoji, _) = getCategoryMetadata(categoryText)
                            Text(
                                text = "$emoji $categoryText",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                modifier = Modifier.size(16.dp)
                              )
                        }
                    }

                    DropdownMenu(
                        expanded = exCategoryDropdown,
                        onDismissRequest = { exCategoryDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.5f)
                    ) {
                        categories.forEach { category ->
                            val (emoji, _) = getCategoryMetadata(category)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(emoji, modifier = Modifier.padding(4.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    onCategorySelect(category)
                                    exCategoryDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notes field expansion
            OutlinedTextField(
                value = notesText,
                onValueChange = onNotesChange,
                label = { Text("Add Notes (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_notes_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSaveExpense,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ai_save_expense_button"),
                enabled = descriptionText.isNotBlank() && amountText.toDoubleOrNull() != null,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADD TRANSACTION", fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        }
    }
}

@Composable
fun SearchAndCategoryFilters(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: String?,
    onFilterChange: (String?) -> Unit,
    categories: List<String>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search Input TextField
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_text_field"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        // Select Category Scroll Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First item is 'All'
            item {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onFilterChange(null) },
                    label = { Text("All Ledger") },
                    modifier = Modifier.testTag("filter_chip_all")
                )
            }

            items(categories) { category ->
                val (emoji, _) = getCategoryMetadata(category)
                FilterChip(
                    selected = selectedFilter == category,
                    onClick = { onFilterChange(category) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(category)
                        }
                    },
                    modifier = Modifier.testTag("filter_chip_$category")
                )
            }
        }
    }
}

@Composable
fun ExpenseLedgerItem(
    expense: Expense,
    onDeleteClick: () -> Unit
) {
    val (emoji, color) = getCategoryMetadata(expense.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_item_${expense.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category icon badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = expense.description,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (expense.isAutoCategorized) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "AI",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        text = "${expense.category} • ${formatMillisToDate(expense.dateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (expense.notes.isNotBlank()) {
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Amount & Delete block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatToINR(expense.amount),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_expense_${expense.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete expense record",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    hasFiltersActive: Boolean,
    onClearFiltersClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📭", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasFiltersActive) "No matching expenses" else "No expense records found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasFiltersActive) {
                    "Try refining your search terms or selecting a different category filter."
                } else {
                    "Log an expense above with the AI Quick Assistant to start tracking your spendings!"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (hasFiltersActive) {
                OutlinedButton(
                    onClick = onClearFiltersClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear All Filters", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BudgetLimitDialog(
    currentLimit: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var limitInput by remember { mutableStateOf(String.format(Locale.US, "%.0f", currentLimit)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("budget_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Update Monthly limit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )

                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Monthly Budget (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val limitDouble = limitInput.toDoubleOrNull() ?: currentLimit
                            onConfirm(limitDouble)
                        },
                        modifier = Modifier.testTag("budget_confirm_button"),
                        enabled = limitInput.toDoubleOrNull() != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddManualExpenseDialog(
    initialDescription: String,
    initialAmount: String,
    initialCategory: String,
    initialNotes: String,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String) -> Unit
) {
    var desc by remember { mutableStateOf(initialDescription) }
    var amt by remember { mutableStateOf(initialAmount) }
    var cat by remember { mutableStateOf(initialCategory) }
    var notes by remember { mutableStateOf(initialNotes) }
    var showCalculatorInDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("manual_add_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Log Custom Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Expense Details") },
                    placeholder = { Text("e.g. Weekly Groceries") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_desc_input")
                )

                OutlinedTextField(
                    value = amt,
                    onValueChange = { amt = it },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = { showCalculatorInDialog = true },
                            modifier = Modifier.testTag("dialog_calculator_button")
                        ) {
                            Text("🧮", fontSize = 16.sp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_amount_input")
                )

                // Simple flow layout selector for Categories in standard dialog
                Text(
                    "Select Category",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { categoryName ->
                        val (emoji, _) = getCategoryMetadata(categoryName)
                        val isSelected = cat == categoryName
                        SuggestionChip(
                            onClick = { cat = categoryName },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(emoji, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(categoryName, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            colors = if (isSelected) {
                                SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                SuggestionChipDefaults.suggestionChipColors()
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_notes_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val parsedAmount = amt.toDoubleOrNull() ?: 0.0
                            if (desc.isNotBlank() && parsedAmount > 0.0) {
                                onConfirm(desc, parsedAmount, cat, notes)
                            }
                        },
                        modifier = Modifier.testTag("manual_confirm_button"),
                        enabled = desc.isNotBlank() && amt.toDoubleOrNull() != null
                    ) {
                        Text("Add Transaction")
                    }
                }
            }
        }
    }

    if (showCalculatorInDialog) {
        FinanceCalculatorDialog(
            initialValue = amt,
            onDismiss = { showCalculatorInDialog = false },
            onApply = { result ->
                amt = result
                showCalculatorInDialog = false
            }
        )
    }
}

// Custom built, interactive Donut/Pie Chart with pure Compose Canvas
@Composable
fun BentoDonutChart(
    categoryTotals: Map<String, Double>,
    total: Double,
    modifier: Modifier = Modifier
) {
    if (total == 0.0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No transaction records logged.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                categoryTotals.forEach { (cat, amt) ->
                    val sweep = ((amt / total) * 360f).toFloat()
                    val (_, color) = getCategoryMetadata(cat)
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text(formatToINR(total), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categoryTotals.forEach { (cat, amt) ->
                val (emoji, color) = getCategoryMetadata(cat)
                val percent = (amt / total) * 100
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$emoji $cat",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f%%", percent),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Custom built Bar Chart displaying levels proportionally
@Composable
fun BentoBarChart(
    categoryTotals: Map<String, Double>,
    maxAmount: Double,
    modifier: Modifier = Modifier
) {
    if (maxAmount == 0.0) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categoryTotals.forEach { (cat, amt) ->
            val (emoji, color) = getCategoryMetadata(cat)
            val ratio = if (maxAmount > 0) (amt / maxAmount).toFloat() else 0f

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$emoji $cat", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatToINR(amt), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE6E0E9))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(ratio.coerceAtLeast(0.01f))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
fun BackupRestoreDialog(
    expensesCount: Int,
    budgetsCount: Int,
    recurringCount: Int,
    onDismiss: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("backup_restore_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Backup & Restore",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Save your financial ledger, category budgets, and recurring schedules to a backup file, or restore a previous session seamlessly.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                // Detailed inventory status card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CURRENT LEDGER STATUS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Expense Logs", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$expensesCount records", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Monthly Budgets", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$budgetsCount categories", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recurring Schedules", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$recurringCount active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_backup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create JSON Backup File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_backup_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Restore from Backup File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("close_backup_dialog_button")
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val (emoji, color) = getCategoryMetadata(expense.category)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("delete_confirmation_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Delete Transaction?",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Are you sure you want to permanently remove this transaction from your history? This action cannot be undone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                // Transaction summary preview inside
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = color.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = expense.description,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = expense.category,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = formatToINR(expense.amount),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("delete_cancel_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("delete_confirm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AutomaticAnalysisHubCard(
    totalSpent: Double,
    budgetLimit: Double,
    topCategoryAndAmount: Pair<String, Double>,
    expenses: List<Expense>,
    onAddMoneyClick: () -> Unit
) {
    val remainingDays = getRemainingDaysInCurrentMonth()
    val remainingBudget = budgetLimit - totalSpent
    val dailyLimit = if (remainingBudget > 0 && budgetLimit > 0) remainingBudget / remainingDays else 0.0
    val pctSpent = if (budgetLimit > 0) (totalSpent / budgetLimit * 100).toInt() else 0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("automatic_analysis_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Auto-Analysis & Insights",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "LIVE FEEDBACK",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 1. ADDING MONEY (Capital Allocations Advisor)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "💼 ADDING MONEY (MONTHLY ALLOCATION)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
                
                Text(
                    text = if (budgetLimit <= 0.0) {
                        "You haven't allocated a monthly budget yet. Click 'Manage Allocation' below to add capital resources. Setting a budget helps predict healthy spending habits."
                    } else if (remainingBudget <= 0.0) {
                        "Your current monthly allocation of ${formatToINR(budgetLimit)} is fully exhausted. Every added transaction will push you negative. We recommend increasing your allocation/adding money to secure emergency reserves if needed."
                    } else if (pctSpent >= 85) {
                        "Your remaining allocation is thin (only ${formatToINR(remainingBudget)} left). Increasing your limit now will immediately raise your daily threshold and prevent critical budget alerts."
                    } else {
                        "With your current resource limit of ${formatToINR(budgetLimit)} and ${formatToINR(remainingBudget)} remaining, you can safely spend up to ${formatToINR(dailyLimit)} daily for the next $remainingDays days of this month."
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onAddMoneyClick,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage Allocation (Add Money)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. SPENDING MONEY (Expense & Outflow Audit)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "💸 SPENDING MONEY (EXPENSE AUDIT)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    color = Color(0xFFC05621), 
                    letterSpacing = 0.5.sp
                )
                
                val countText = if (expenses.isEmpty()) "0" else expenses.size.toString()
                Text(
                    text = buildString {
                        append("You have logged $countText transaction${if (expenses.size == 1) "" else "s"} totaling ${formatToINR(totalSpent)} this calendar month. ")
                        if (pctSpent > 0) {
                            append("This represents $pctSpent% of your budget pool used. ")
                        }
                        if (topCategoryAndAmount.first != "None" && topCategoryAndAmount.second > 0.0) {
                            val catPct = if (totalSpent > 0) (topCategoryAndAmount.second / totalSpent * 100).toInt() else 0
                            append("Your heaviest spending is on ${topCategoryAndAmount.first} which takes up $catPct% (totaling ${formatToINR(topCategoryAndAmount.second)}) of this month's entire budget outlay. ")
                        }
                        
                        // Actionable advisory
                        if (pctSpent >= 100) {
                            append("❌ CRITICAL: You are over-limit by ${formatToINR(totalSpent - budgetLimit)}. Freeze discretionary card purchases immediately.")
                        } else if (pctSpent >= 80) {
                            append("⚠️ CAUTION: Burn rate is extremely high. Consider avoiding optional expenses in the remaining $remainingDays days.")
                        } else if (pctSpent > 0) {
                            append("🟢 EXCELLENT: Your pacing is healthy and remaining daily budget is a comfortable ${formatToINR(dailyLimit)}.")
                        } else {
                            append("Start adding expense records to see automated warnings and budget suggestions in real-time.")
                        }
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getRemainingDaysInCurrentMonth(): Int {
    val cal = Calendar.getInstance()
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentDay = cal.get(Calendar.DAY_OF_MONTH)
    val remaining = maxDay - currentDay + 1
    return if (remaining > 0) remaining else 1
}

@Composable
fun FinanceCalculatorDialog(
    initialValue: String = "",
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var expression by remember { mutableStateOf(initialValue) }
    var resultText by remember { mutableStateOf("") }

    // Live Math Evaluator
    LaunchedEffect(expression) {
        if (expression.isNotBlank()) {
            val evaluated = try {
                val clean = expression.replace("×", "*").replace("÷", "/")
                SimpleMathParser(clean).parse()
            } catch (e: Exception) {
                null
            }
            if (evaluated != null) {
                resultText = String.format(Locale.US, "%.2f", evaluated)
            } else {
                resultText = ""
            }
        } else {
            resultText = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("calculator_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧮", fontSize = 16.sp)
                        }
                        Text(
                            text = "Finance Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }

                // Display Screen
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        // Math Expression
                        Text(
                            text = expression.ifEmpty { "0" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        // Live Result Preview
                        Text(
                            text = if (resultText.isNotEmpty()) "= ₹$resultText" else "",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                // Keypad Layout
                val buttons = listOf(
                    listOf("C", "(", ")", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "-"),
                    listOf("1", "2", "3", "+"),
                    listOf("0", ".", "⌫", "=")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    buttons.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { char ->
                                val isOperator = char in listOf("÷", "×", "-", "+", "=")
                                val isClear = char == "C" || char == "⌫"
                                
                                Button(
                                    onClick = {
                                        when (char) {
                                            "C" -> {
                                                expression = ""
                                                resultText = ""
                                            }
                                            "⌫" -> {
                                                if (expression.isNotEmpty()) {
                                                    expression = expression.dropLast(1)
                                                }
                                            }
                                            "=" -> {
                                                if (resultText.isNotEmpty()) {
                                                    expression = resultText
                                                    resultText = ""
                                                }
                                            }
                                            else -> {
                                                expression += char
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isClear -> MaterialTheme.colorScheme.errorContainer
                                            isOperator -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = when {
                                            isClear -> MaterialTheme.colorScheme.onErrorContainer
                                            isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = char,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom CTA action button
                Button(
                    onClick = {
                        val finalVal = resultText.ifEmpty { expression.ifEmpty { "0" } }
                        onApply(finalVal)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("calculator_apply_button"),
                    enabled = resultText.isNotEmpty() || expression.toDoubleOrNull() != null
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply Calculated Amount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

class SimpleMathParser(private val str: String) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            if (eat('+'.code)) x += parseTerm() 
            else if (eat('-'.code)) x -= parseTerm() 
            else return x
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            if (eat('*'.code)) x *= parseFactor() 
            else if (eat('/'.code)) {
                val divisor = parseFactor()
                if (divisor == 0.0) throw ArithmeticException("Division by zero")
                x /= divisor 
            }
            else return x
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor() 
        if (eat('-'.code)) return -parseFactor() 

        var x: Double
        val startPos = this.pos
        if (eat('('.code)) { 
            x = parseExpression()
            eat(')'.code)
        } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) { 
            while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
            x = str.substring(startPos, this.pos).toDouble()
        } else {
            throw RuntimeException("Unexpected: " + ch.toChar())
        }

        return x
    }
}

@Composable
fun GoogleSheetsSyncCard(
    currentUser: UserProfile?,
    googleSheetId: String?,
    googleSheetName: String?,
    googleAccessToken: String?,
    isSyncing: Boolean,
    lastSyncTime: Long,
    expensesCount: Int,
    onSyncClick: () -> Unit,
    onConnectClick: () -> Unit,
    onSaveToken: (String?) -> Unit
) {
    var tokenInput by remember(googleAccessToken) { mutableStateOf(googleAccessToken ?: "") }
    var isEditingToken by remember { mutableStateOf(googleAccessToken.isNullOrBlank()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google_sheets_sync_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row with Icon and Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFE2F0D9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📊", fontSize = 16.sp)
                    }
                    Text(
                        text = "Google Sheets Sync",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Cloud connected status chip
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentUser != null) Color(0xFFE2F0D9) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (currentUser != null) "CONNECTED" else "OFFLINE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (currentUser != null) Color(0xFF1E4620) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (currentUser == null) {
                // Not connected State
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Connect a Google or local account to create, update, and back up your transaction ledger to Google Sheets spreadsheets automatically.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = onConnectClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(40.dp)
                    ) {
                        Text("Connect Account & Sheets", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Connected State
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Google Access Token Management field
                    if (isEditingToken) {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Google OAuth Access Token") },
                            placeholder = { Text("ya29.a0Ax...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sheets_token_input"),
                            supportingText = {
                                Text(
                                    "Paste token with https://www.googleapis.com/auth/sheets scope (e.g. from Google OAuth 2.0 Playground)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (tokenInput.isNotBlank()) {
                                            onSaveToken(tokenInput.trim())
                                            isEditingToken = false
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                        contentDescription = "Save Access Token",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("OAuth Token Status", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("••••••••••••" + (googleAccessToken?.takeLast(6) ?: "Active"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            TextButton(
                                onClick = { isEditingToken = true }
                            ) {
                                Text("Update Token", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Spreadsheet File:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = googleSheetName ?: "Not Created",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sheet ID Reference:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = googleSheetId ?: "None",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last synchronized timestamp:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (lastSyncTime > 0) formatMillisToDate(lastSyncTime) else "Never Synced",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rows Syncable:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$expensesCount lines",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("sheets_sync_action_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Writing to Google Sheets...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                text = if (googleSheetId == null) "Create Google Sheet & Sync" else "Sync to Google Sheets Now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthModalDialog(
    currentUser: UserProfile?,
    onDismiss: () -> Unit,
    onGoogleSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Boolean,
    onSignIn: (String, String) -> String?,
    onLogOut: () -> Unit
) {
    var isRegisterState by remember { mutableStateOf(false) }
    
    // Form Inputs
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    
    var loginError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val suggestedEmails = listOf(
        Pair("ranojoy017@gmail.com", "Ranojoy")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("auth_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (currentUser != null) "Your Profile" else if (isRegisterState) "Create Account" else "Sign In",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (currentUser != null) "Manage your cloud identity" else "Sync expenses across spreadsheets",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (currentUser != null) {
                    // Profile/Logged-In State
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFFE2F0D9), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUser.name.take(1).uppercase(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1E4620)
                                    )
                                }
                                Column {
                                    Text(
                                        text = currentUser.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currentUser.email,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (currentUser.signInType == "GOOGLE") Color(0xFFE8F0FE) else Color(0xFFF3EDF7)
                                        ),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (currentUser.signInType == "GOOGLE") "GOOGLE ACCOUNT" else "EMAIL ACCOUNT",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentUser.signInType == "GOOGLE") Color(0xFF1967D2) else Color(0xFF6750A4),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onLogOut()
                                onDismiss()
                                android.widget.Toast.makeText(context, "Logged out successfully", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("logout_button")
                        ) {
                            Text("Sign Out of Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else if (isRegisterState) {
                    // Sign-Up registration fields
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Display Name") },
                            leadingIcon = { Text("👤 ", fontSize = 15.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("signup_name_field")
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Text("✉️ ", fontSize = 15.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("signup_email_field")
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Set Password") },
                            leadingIcon = { Text("🔒 ", fontSize = 15.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("signup_password_field")
                        )

                        if (loginError != null) {
                            Text(
                                text = loginError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (nameInput.isBlank() || emailInput.isBlank() || passwordInput.isBlank()) {
                                    loginError = "Please fill in all details to create an account."
                                } else {
                                    val success = onSignUp(emailInput.trim(), nameInput.trim(), passwordInput)
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Account Created & Logged In!", android.widget.Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        loginError = "Email already registered. Try signing in instead."
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("signup_submit_button")
                        ) {
                            Text("Register Account (Create Password)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { 
                                isRegisterState = false 
                                loginError = null
                            }) {
                                Text("Already have an account? Sign In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Sign-In Fields
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Text("✉️ ", fontSize = 15.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("signin_email_field")
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            leadingIcon = { Text("🔒 ", fontSize = 15.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("signin_password_field")
                        )

                        if (loginError != null) {
                            Text(
                                text = loginError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (emailInput.isBlank() || passwordInput.isBlank()) {
                                    loginError = "Please fill in email and password."
                                } else {
                                    val err = onSignIn(emailInput.trim(), passwordInput)
                                    if (err == null) {
                                        android.widget.Toast.makeText(context, "Welcome Back!", android.widget.Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        loginError = err
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("signin_submit_button")
                        ) {
                            Text("Sign In with Password", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Divider line separating local creds and Google single-sign-on
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "OR", 
                                fontSize = 9.sp, 
                                fontWeight = FontWeight.Black, 
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        // Authentic Google Sign-In Selection Choice block
                        Text(
                            text = "FAST SIGN IN WITH GOOGLE PROFILE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        suggestedEmails.forEach { (email, name) ->
                            OutlinedCard(
                                onClick = {
                                    onGoogleSignIn(email, name)
                                    onDismiss()
                                    android.widget.Toast.makeText(context, "Logged in via Google as $name!", android.widget.Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("google_profile_option_$name"),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFFE8F0FE), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "G",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Color(0xFF1967D2)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sign in as $name ($email)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text("➔", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { 
                                isRegisterState = true 
                                loginError = null
                            }) {
                                Text("Don't have an account? Sign Up First", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSheetsDashboardCard(
    isLive: Boolean,
    liveSpending: Map<String, Double>,
    liveTotal: Double,
    offlineExpenses: List<com.example.data.model.Expense>,
    isFetchingLive: Boolean,
    fetchError: String?,
    isGoogleConnected: Boolean,
    onFetchLiveClick: () -> Unit,
    onToggleMode: (Boolean) -> Unit
) {
    // Offline category spending calculation
    val offlineSpending = remember(offlineExpenses) {
        offlineExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }
    val offlineTotal = remember(offlineSpending) {
        offlineSpending.values.sum()
    }

    val activeSpending = if (isLive) liveSpending else offlineSpending
    val activeTotal = if (isLive) liveTotal else offlineTotal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("google_sheets_dashboard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isLive) Color(0xFF34A853) else Color(0xFF607D8B), CircleShape)
                        )
                        Text(
                            text = "Cloud Analytics Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Aggregated category metrics from your Google Sheet ledger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isFetchingLive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF34A853)
                    )
                }
            }

            // Tabs to alternate source: "Live Sheet Data" vs "Local Cache"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { onToggleMode(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isLive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!isLive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("tab_local_cache")
                ) {
                    Text("Local Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { 
                        if (isLive) {
                            onToggleMode(true)
                        } else {
                            if (isGoogleConnected) {
                                onToggleMode(true)
                                onFetchLiveClick()
                            } else {
                                onToggleMode(true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLive) Color(0xFF34A853) else Color.Transparent,
                        contentColor = if (isLive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("tab_live_sheets")
                ) {
                    Text("Live Sheet Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Main Display Area
            if (isLive) {
                if (!isGoogleConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFFAF0), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFFBE9E7), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("☁️", fontSize = 36.sp)
                            Text(
                                "Live Spreadsheet Disconnected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE64A19)
                            )
                            Text(
                                "Please connect a Google Account, save an Access Token, and sync your first batch of ledger records above to fetch live spreadsheet rows.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else if (fetchError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚠️", fontSize = 28.sp)
                            Text(
                                "Connection Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                fetchError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = onFetchLiveClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                        contentDescription = "Retry Fetch",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Retry Fetch Row Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (activeSpending.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("📊", fontSize = 36.sp)
                            Text(
                                "Waiting for row aggregation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "The connected sheet looks clean or hasn't loaded. Click the fetch button below to query Google Sheets server cells.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = onFetchLiveClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(40.dp).testTag("sheets_fetch_button_empty")
                            ) {
                                Text("Fetch From Google Sheets now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Render Gorgeous Active Visual Data
                    AnalyticsDashboardRenderBox(
                        categorySpending = activeSpending,
                        total = activeTotal,
                        isLiveMode = true,
                        onRefreshClick = onFetchLiveClick
                    )
                }
            } else {
                // Offline Local Cache State
                if (activeSpending.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No offline records saved to build visualization. Record a transaction to see immediate analytics previews!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    AnalyticsDashboardRenderBox(
                        categorySpending = activeSpending,
                        total = activeTotal,
                        isLiveMode = false,
                        onRefreshClick = {}
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsDashboardRenderBox(
    categorySpending: Map<String, Double>,
    total: Double,
    isLiveMode: Boolean,
    onRefreshClick: () -> Unit
) {
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = categorySpending) {
        animationPlayed = false
        delay(50) // subtle delay to reset track growth on state update
        animationPlayed = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // High level stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total Account Spend",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                // Smooth ticker animation for the total spent
                val animatedTotal by animateFloatAsState(
                    targetValue = if (animationPlayed) total.toFloat() else 0f,
                    animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    label = "TotalSpendAnimation"
                )
                Text(
                    text = formatToINR(animatedTotal.toDouble()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isLiveMode) {
                TextButton(
                    onClick = onRefreshClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF34A853))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                            contentDescription = "Sync",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF34A853)
                        )
                        Text("Refresh Rows", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34A853))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Cached", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Recharts horizontal stacked bar visual analog with premium entrance animations
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categorySpending.entries.sortedByDescending { it.value }.forEachIndexed { index, (cat, amt) ->
                val (indicator, color) = getCategoryMetadata(cat)
                val fraction = if (total > 0.0) (amt / total).toFloat() else 0.0f
                val percentStr = String.format("%.1f%%", fraction * 100)

                // Elegant progress track growth animation
                val animatedFraction by animateFloatAsState(
                    targetValue = if (animationPlayed) fraction else 0f,
                    animationSpec = tween(
                        durationMillis = 1000,
                        delayMillis = index * 80, // premium staggered progress grow
                        easing = FastOutSlowInEasing
                    ),
                    label = "BarProgressTrack"
                )

                // Native staggered fade-in animations for each category track Row
                val rowAlpha by animateFloatAsState(
                    targetValue = if (animationPlayed) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 500,
                        delayMillis = index * 80, // gorgeous staggered entry
                        easing = LinearOutSlowInEasing
                    ),
                    label = "RowAlpha"
                )

                Column(
                    modifier = Modifier
                        .graphicsLayer { alpha = rowAlpha }
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(indicator, fontSize = 13.sp)
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ticking counter animation for individual amounts too!
                            val animatedAmount by animateFloatAsState(
                                targetValue = if (animationPlayed) amt.toFloat() else 0f,
                                animationSpec = tween(
                                    durationMillis = 1000,
                                    delayMillis = index * 80,
                                    easing = FastOutSlowInEasing
                                ),
                                label = "CategoryAmount"
                            )
                            Text(
                                text = formatToINR(animatedAmount.toDouble()),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "($percentStr)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Rounded visual track bar inspired by Recharts premium dashboard layout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedFraction)
                                .background(color, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleDriveBackupCard(
    isBackupEnabled: Boolean,
    backupStatus: String,
    isBackingUp: Boolean,
    isGoogleConnected: Boolean,
    onToggleBackup: (Boolean) -> Unit,
    onBackupNowClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("google_drive_backup_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFFFE0B2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☁️", fontSize = 16.sp)
                    }
                    Column {
                        Text(
                            text = "Google Drive Auto-Backup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatic data save to sign-up Google Drive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Enabled / Disabled Switch
                Switch(
                    checked = isBackupEnabled,
                    onCheckedChange = onToggleBackup,
                    enabled = isGoogleConnected,
                    modifier = Modifier.testTag("google_drive_backup_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backup Status",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (!isGoogleConnected) "Disconnected (Connect Google Account above)" else backupStatus,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGoogleConnected && !backupStatus.contains("failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.primary
                        } else if (isGoogleConnected) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (isGoogleConnected) {
                    Button(
                        onClick = onBackupNowClick,
                        enabled = !isBackingUp,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.height(36.dp).testTag("backup_now_button")
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Share,
                                    contentDescription = "Upload",
                                    modifier = Modifier.size(15.dp)
                                )
                                Text("Backup Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiAdvisorScreen(viewModel: com.example.ui.viewmodel.ExpenseViewModel) {
    var subTab by remember { mutableStateOf(0) }
    
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isExpertGenerating by viewModel.isExpertGenerating.collectAsStateWithLifecycle()
    
    val investmentRoadmap by viewModel.investmentRoadmap.collectAsStateWithLifecycle()
    val isInvestmentGenerating by viewModel.isInvestmentGenerating.collectAsStateWithLifecycle()
    
    val problemSolvingPlan by viewModel.problemSolvingPlan.collectAsStateWithLifecycle()
    val isProblemSolvingGenerating by viewModel.isProblemSolvingGenerating.collectAsStateWithLifecycle()
    
    val financialAuditReport by viewModel.financialAuditReport.collectAsStateWithLifecycle()
    val isAuditGenerating by viewModel.isAuditGenerating.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selector bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf("💬 Chat", "📈 Invest", "🎯 Solver", "📋 Auditor")
            tabs.forEachIndexed { index, label ->
                val selected = subTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { subTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Render sections
        when (subTab) {
            0 -> ChatAdvisorySection(
                messages = chatMessages,
                isGenerating = isExpertGenerating,
                onSendMessage = { viewModel.submitUserQuery(it) },
                onClearChat = { viewModel.clearChat() }
            )
            1 -> InvestmentAdvisorySection(
                roadmap = investmentRoadmap,
                isGenerating = isInvestmentGenerating,
                onGenerateRoadmap = { viewModel.generateInvestmentRoadmap() }
            )
            2 -> ProblemSolvingAdvisorySection(
                plan = problemSolvingPlan,
                isGenerating = isProblemSolvingGenerating,
                onGeneratePlan = { goal, amt, months -> viewModel.generateProblemSolvingPlan(goal, amt, months) }
            )
            3 -> AuditReportsAdvisorySection(
                report = financialAuditReport,
                isGenerating = isAuditGenerating,
                onGenerateReport = { viewModel.generateFinancialAuditReport() }
            )
        }
    }
}

@Composable
fun ChatAdvisorySection(
    messages: List<com.example.ui.viewmodel.ExpenseViewModel.ChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Google AI Co-Pilot Partner",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onClearChat,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Chat history",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message board ledger
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val alignEnd = msg.isUser
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (alignEnd) 16.dp else 4.dp,
                            bottomEnd = if (alignEnd) 4.dp else 16.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (alignEnd) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (alignEnd) "You" else "Google Financial Expert",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alignEnd) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (alignEnd) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                MarkdownTextSimple(text = msg.text)
                            }
                        }
                    }
                }
            }

            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Google AI is analyzing your cash flows...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Suggestions horizontal pile
        val suggestions = listOf(
            "How can I cut non-essential spending?",
            "What is the best investment for ₹5000/mo?",
            "Is my utilities category too high?",
            "Explain PPF vs passive Mutual Funds"
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(suggestions) { label ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .clickable {
                            onSendMessage(label)
                        }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // TextInput row controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Ask your financial expert...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input"),
                shape = RoundedCornerShape(16.dp),
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp).testTag("ai_send_button"),
                enabled = !isGenerating && textInput.isNotBlank()
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send message")
            }
        }
    }
}

@Composable
fun InvestmentAdvisorySection(
    roadmap: String?,
    isGenerating: Boolean,
    onGenerateRoadmap: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💼 Google AI Wealth Adviser",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unlock a customized, mathematically optimized Asset Allocation roadmap. Our AI analyzes your transaction margins and recommends risk-adjusted allocations over mutual funds, fixed income, debt, and liquid reserves.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onGenerateRoadmap,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("ai_generate_investment_button"),
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulating Modern Portfolio curves...")
                    } else {
                        Text(if (roadmap == null) "Compute Personal Investment Roadmap" else "Re-Compute Asset Allocation Plan")
                    }
                }
            }
        }

        if (roadmap != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Strategic Asset Map",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    MarkdownTextSimple(text = roadmap)
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text("Interactive Portfolio Mix Projection:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .background(Color(0xFF4CAF50), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Growth 60%", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(0.25f)
                                .fillMaxHeight()
                                .background(Color(0xFF2196F3)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Debt 25%", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(0.15f)
                                .fillMaxHeight()
                                .background(Color(0xFFFFC107), RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Gold 15%", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProblemSolvingAdvisorySection(
    plan: String?,
    isGenerating: Boolean,
    onGeneratePlan: (String, Double, Int) -> Unit
) {
    var goalName by remember { mutableStateOf("") }
    var targetCost by remember { mutableStateOf("") }
    var durationMonths by remember { mutableStateOf(3) }
    
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "🎯 Target Savings Task Solver",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Input a specific goal (e.g. buying a laptop, down payment, emergency buffer) and let the co-pilot calculate a precise savings funnels with targeted spending cuts to achieve it.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                
                OutlinedTextField(
                    value = goalName,
                    onValueChange = { goalName = it },
                    label = { Text("What are you saving for?") },
                    placeholder = { Text("e.g. MacBook Air, Goa holiday, Emergency fund") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_goal_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = targetCost,
                    onValueChange = { targetCost = it },
                    label = { Text("Target Capital Amount (₹)") },
                    placeholder = { Text("e.g. 80000") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_saving_amount_input"),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Timeline Goal: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("$durationMonths Months", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = durationMonths.toFloat(),
                        onValueChange = { durationMonths = it.toInt() },
                        valueRange = 1f..24f,
                        steps = 23,
                        modifier = Modifier.testTag("ai_timeline_slider")
                    )
                }

                Button(
                    onClick = {
                        val amt = targetCost.toDoubleOrNull() ?: 10000.0
                        onGeneratePlan(goalName.ifBlank { "Personal Goal" }, amt, durationMonths)
                    },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("ai_generate_plan_button"),
                    enabled = !isGenerating && goalName.isNotBlank() && targetCost.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Formulating monthly milestone checklists...")
                    } else {
                        Text("Draft Custom Savings Playbook")
                    }
                }
            }
        }

        if (plan != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🏆", fontSize = 24.sp)
                        Text(
                            text = "Playbook: Save for $goalName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    MarkdownTextSimple(text = plan)
                }
            }
        }
    }
}

@Composable
fun AuditReportsAdvisorySection(
    report: String?,
    isGenerating: Boolean,
    onGenerateReport: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📋 AI Financial Health Evaluation & Audit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Compile an end-to-end audit of your financial behavior. The auditor calculates a score out of 100, detects high concentrations, flags overspending risks, and provides critical corrective suggestions.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onGenerateReport,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("ai_generate_report_button"),
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing ledger records & warnings...")
                    } else {
                        Text(if (report == null) "Run Deep Financial Health Audit" else "Re-Run Audit Evaluation Report")
                    }
                }
            }
        }

        if (report != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Auditor Verification Report",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("Real-time telemetry analysis", fontSize = 11.sp, color = Color.Gray)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(25.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "82",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    MarkdownTextSimple(text = report)
                }
            }
        }
    }
}

@Composable
fun MarkdownTextSimple(text: String, modifier: Modifier = Modifier) {
    val lines = text.lineSequence()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                continue
            }
            when {
                trimmed.startsWith("###") || trimmed.startsWith("####") -> {
                    val cleanText = trimmed.removePrefix("####").removePrefix("###").replace("**", "").replace("*", "").trim()
                    Text(
                        text = cleanText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("*") || trimmed.startsWith("-") -> {
                    val cleanText = trimmed.removePrefix("*").removePrefix("-").trim()
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•  ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = formatBoldSpans(cleanText),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                trimmed.startsWith("|") -> {
                    val cleanText = trimmed.removePrefix("|").removeSuffix("|").trim()
                    if (cleanText.isNotEmpty()) {
                        Text(
                            text = formatBoldSpans(cleanText),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                }
                else -> {
                    Text(
                        text = formatBoldSpans(trimmed),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun formatBoldSpans(text: String): androidx.compose.ui.text.AnnotatedString {
    val parts = text.split("**")
    return androidx.compose.ui.text.buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}


