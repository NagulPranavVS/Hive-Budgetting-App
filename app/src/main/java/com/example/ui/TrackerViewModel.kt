package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.TrackerApplication
import com.example.data.model.Category
import com.example.data.model.Expense
import com.example.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class TrackerViewModel(
    application: Application,
    private val repository: ExpenseRepository
) : AndroidViewModel(application) {

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    private val _homeMonth = MutableStateFlow(currentMonth)
    val homeMonth: StateFlow<Int> = _homeMonth.asStateFlow()

    private val _homeYear = MutableStateFlow(currentYear)
    val homeYear: StateFlow<Int> = _homeYear.asStateFlow()

    private val _reportMonth = _homeMonth
    val reportMonth: StateFlow<Int> = _reportMonth.asStateFlow()

    private val _reportYear = _homeYear
    val reportYear: StateFlow<Int> = _reportYear.asStateFlow()

    private val _categoriesTrigger = MutableStateFlow(0L)
    val categoriesTrigger: StateFlow<Long> = _categoriesTrigger.asStateFlow()

    // All categories flow
    val categories: StateFlow<List<Category>> = combine(repository.allCategoriesFlow, _homeMonth, _homeYear, _categoriesTrigger) { list, month, year, _ ->
        list.filter { !repository.isCategoryDeletedForMonth(it.id, month, year) }
            .map { category ->
                val monthBudget = repository.getCategoryBudgetForMonth(category.id, month, year)
                category.copy(monthlyBudget = monthBudget)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getCategoriesForMonth(month: Int, year: Int): Flow<List<Category>> {
        return combine(repository.allCategoriesFlow, _categoriesTrigger) { list, _ ->
            list.filter { !repository.isCategoryDeletedForMonth(it.id, month, year) }
                .map { category ->
                    val monthBudget = repository.getCategoryBudgetForMonth(category.id, month, year)
                    category.copy(monthlyBudget = monthBudget)
                }
        }
    }

    // All expenses flow
    val expenses: StateFlow<List<Expense>> = repository.allExpensesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Widget / Intent Navigation triggers
    val widgetActionTrigger = MutableStateFlow<String?>(null)
    val widgetRouteIsIncome = MutableStateFlow<Boolean?>(null)

    // Profiles & themes state
    private val _userName = MutableStateFlow(repository.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(repository.getSelectedCurrency())
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    fun saveCurrency(currency: String) {
        repository.setSelectedCurrency(currency)
        _selectedCurrency.value = currency
    }

    fun getCurrencySymbol(): String {
        return when (_selectedCurrency.value.uppercase()) {
            "INR" -> "₹"
            "POUNDS", "GBP" -> "£"
            "EUROS", "EUR" -> "€"
            "DOLLARS", "USD" -> "$"
            "DIRHAMS", "AED" -> "د.إ"
            else -> "₹"
        }
    }

    private val _themeMode = MutableStateFlow(repository.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _budgetAlertsEnabled = MutableStateFlow(repository.isBudgetAlertsEnabled())
    val budgetAlertsEnabled: StateFlow<Boolean> = _budgetAlertsEnabled.asStateFlow()

    fun toggleBudgetAlerts(enabled: Boolean) {
        repository.setBudgetAlertsEnabled(enabled)
        _budgetAlertsEnabled.value = enabled
    }

    private val _dailySpendNotificationEnabled = MutableStateFlow(repository.isDailySpendNotificationEnabled())
    val dailySpendNotificationEnabled: StateFlow<Boolean> = _dailySpendNotificationEnabled.asStateFlow()

    private val _dailySpendNotificationTime = MutableStateFlow(repository.getDailySpendNotificationTime())
    val dailySpendNotificationTime: StateFlow<String> = _dailySpendNotificationTime.asStateFlow()

    fun toggleDailySpendNotification(enabled: Boolean) {
        repository.setDailySpendNotificationEnabled(enabled)
        _dailySpendNotificationEnabled.value = enabled
        if (enabled) {
            val parts = _dailySpendNotificationTime.value.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            com.example.NotificationScheduler.scheduleDailyNotification(getApplication(), hour, minute)
        } else {
            com.example.NotificationScheduler.cancelDailyNotification(getApplication())
        }
    }

    fun updateDailySpendNotificationTime(time: String) {
        repository.setDailySpendNotificationTime(time)
        _dailySpendNotificationTime.value = time
        if (_dailySpendNotificationEnabled.value) {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            com.example.NotificationScheduler.scheduleDailyNotification(getApplication(), hour, minute)
        }
    }

    private val _sheetsSyncEnabled = MutableStateFlow(repository.isSheetsSyncEnabled())
    val sheetsSyncEnabled: StateFlow<Boolean> = _sheetsSyncEnabled.asStateFlow()

    private val _connectedSheetId = MutableStateFlow(repository.getConnectedSheetId())
    val connectedSheetId: StateFlow<String> = _connectedSheetId.asStateFlow()

    private val _googleEmail = MutableStateFlow(repository.getSavedEmail())
    val googleEmail: StateFlow<String> = _googleEmail.asStateFlow()

    private val _googleAccessToken = MutableStateFlow(repository.getSavedAccessToken())
    val googleAccessToken: StateFlow<String> = _googleAccessToken.asStateFlow()

    // Monthly selection for Report Screen
    init {
        viewModelScope.launch {
            try {
                val allCats = repository.getAllCategories()
                val existingSavings = allCats.filter { it.isSavings }
                if (existingSavings.isEmpty()) {
                    repository.insertCategory(Category(name = "Investment", iconName = "trending_up", colorHex = "#10B981", monthlyBudget = null, isIncome = false, isSavings = true))
                    repository.insertCategory(Category(name = "FD/RD", iconName = "description", colorHex = "#3B82F6", monthlyBudget = null, isIncome = false, isSavings = true))
                    repository.insertCategory(Category(name = "In-hand", iconName = "account_balance_wallet", colorHex = "#EC4899", monthlyBudget = null, isIncome = false, isSavings = true))
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackerViewModel", "Error populating default savings categories", e)
            }
        }
    }

    fun setHomeMonth(month: Int) {
        _homeMonth.value = month
    }

    fun setHomeYear(year: Int) {
        _homeYear.value = year
    }

    private val _selectedAnalyticsTab = MutableStateFlow("report")
    val selectedAnalyticsTab: StateFlow<String> = _selectedAnalyticsTab.asStateFlow()

    fun setSelectedAnalyticsTab(tab: String) {
        _selectedAnalyticsTab.value = tab
    }

    private val _editingExpense = MutableStateFlow<Expense?>(null)
    val editingExpense: StateFlow<Expense?> = _editingExpense.asStateFlow()

    fun startEditingExpense(expense: Expense) {
        _editingExpense.value = expense
    }

    fun clearEditingExpense() {
        _editingExpense.value = null
    }

    // Screen states & operations
    fun selectNextMonth() {
        if (_reportMonth.value == 12) {
            _reportMonth.value = 1
            _reportYear.value += 1
        } else {
            _reportMonth.value += 1
        }
    }

    fun selectPrevMonth() {
        if (_reportMonth.value == 1) {
            _reportMonth.value = 12
            _reportYear.value -= 1
        } else {
            _reportMonth.value -= 1
        }
    }

    fun setReportMonthAndYear(month: Int, year: Int) {
        _reportMonth.value = month
        _reportYear.value = year
    }

    /**
     * Home Screen Live Calculations derived from StateFlows
     */
    val spentToday: StateFlow<Double> = expenses.map { list ->
        val calToday = Calendar.getInstance()
        val d = calToday.get(Calendar.DAY_OF_MONTH)
        val m = calToday.get(Calendar.MONTH) + 1
        val y = calToday.get(Calendar.YEAR)

        list.filter { exp ->
            if (exp.isIncome) return@filter false
            if (exp.isSavings) return@filter false
            val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
            calExp.get(Calendar.DAY_OF_MONTH) == d &&
                    (calExp.get(Calendar.MONTH) + 1) == m &&
                    calExp.get(Calendar.YEAR) == y
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val spentThisWeek: StateFlow<Double> = expenses.map { list ->
        // Current 7 days (including today)
        val limit = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        list.filter { !it.isIncome && !it.isSavings && it.date >= limit && it.date <= System.currentTimeMillis() }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val spentThisMonth: StateFlow<Double> = expenses.map { list ->
        val calNow = Calendar.getInstance()
        val m = calNow.get(Calendar.MONTH) + 1
        val y = calNow.get(Calendar.YEAR)

        list.filter { exp ->
            if (exp.isIncome) return@filter false
            if (exp.isSavings) return@filter false
            val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
            (calExp.get(Calendar.MONTH) + 1) == m && calExp.get(Calendar.YEAR) == y
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val incomeThisMonth: StateFlow<Double> = expenses.map { list ->
        val calNow = Calendar.getInstance()
        val m = calNow.get(Calendar.MONTH) + 1
        val y = calNow.get(Calendar.YEAR)

        list.filter { exp ->
            if (!exp.isIncome) return@filter false
            val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
            (calExp.get(Calendar.MONTH) + 1) == m && calExp.get(Calendar.YEAR) == y
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val savingsThisMonth: StateFlow<Double> = expenses.map { list ->
        val calNow = Calendar.getInstance()
        val m = calNow.get(Calendar.MONTH) + 1
        val y = calNow.get(Calendar.YEAR)

        list.filter { exp ->
            if (!exp.isSavings) return@filter false
            val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
            (calExp.get(Calendar.MONTH) + 1) == m && calExp.get(Calendar.YEAR) == y
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /**
     * Calculations for Selected Report Month
     */
    val reportExpenses: StateFlow<List<Expense>> = combine(expenses, _reportMonth, _reportYear) { list, month, year ->
        list.filter { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.date }
            (cal.get(Calendar.MONTH) + 1) == month && cal.get(Calendar.YEAR) == year
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalReportSpent: StateFlow<Double> = reportExpenses.map { list ->
        list.filter { !it.isIncome && !it.isSavings }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Expense CRUD
    fun addExpense(amount: Double, description: String, date: Long, categoryId: Int, isIncome: Boolean = false, isSavings: Boolean = false) {
        viewModelScope.launch {
            repository.insertExpense(
                Expense(
                    amount = amount,
                    description = description,
                    date = date,
                    categoryId = categoryId,
                    isIncome = isIncome,
                    isSavings = isSavings,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun deleteExpenseById(id: Int) {
        viewModelScope.launch {
            repository.deleteExpenseById(id)
        }
    }

    // Category CRUD
    fun addCategory(name: String, iconName: String, colorHex: String, monthlyBudget: Double? = null, isIncome: Boolean = false, isSavings: Boolean = false, isRecurring: Boolean = false) {
        viewModelScope.launch {
            val newId = repository.insertCategory(
                Category(name = name, iconName = iconName, colorHex = colorHex, monthlyBudget = null, isIncome = isIncome, isSavings = isSavings)
            )
            if (monthlyBudget != null) {
                repository.setCategoryBudgetForMonth(newId.toInt(), _homeMonth.value, _homeYear.value, monthlyBudget, isRecurring)
            }
            _categoriesTrigger.value = System.currentTimeMillis()
        }
    }

    fun isBudgetPromptDismissed(month: Int, year: Int): Boolean {
        return repository.isBudgetPromptDismissed(month, year)
    }

    fun setBudgetPromptDismissed(month: Int, year: Int, dismissed: Boolean) {
        repository.setBudgetPromptDismissed(month, year, dismissed)
        _categoriesTrigger.value = System.currentTimeMillis()
    }

    fun copyMonthlyBudgets(srcMonth: Int, srcYear: Int, targetMonth: Int, targetYear: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.copyMonthlyBudgets(srcMonth, srcYear, targetMonth, targetYear)
            _categoriesTrigger.value = System.currentTimeMillis()
            repository.updateAllWidgets()
            showSnackbar("Budget copied successfully")
            onComplete()
        }
    }

    fun isCategoryBudgetRecurringForMonth(categoryId: Int, month: Int, year: Int): Boolean {
        return repository.isCategoryBudgetRecurringForMonth(categoryId, month, year)
    }

    fun isCategoryBudgetSettledForMonth(categoryId: Int, month: Int, year: Int): Boolean {
        return repository.isCategoryBudgetSettledForMonth(categoryId, month, year)
    }

    fun setCategoryBudgetSettledForMonth(categoryId: Int, month: Int, year: Int, settled: Boolean) {
        viewModelScope.launch {
            repository.setCategoryBudgetSettledForMonth(categoryId, month, year, settled)
            _categoriesTrigger.value = System.currentTimeMillis()
            repository.updateAllWidgets()
        }
    }

    fun updateCategory(category: Category, isRecurring: Boolean = false, isSettled: Boolean = false) {
        viewModelScope.launch {
            repository.setCategoryBudgetForMonth(category.id, _homeMonth.value, _homeYear.value, category.monthlyBudget, isRecurring)
            repository.setCategoryBudgetSettledForMonth(category.id, _homeMonth.value, _homeYear.value, isSettled)
            repository.updateCategory(category.copy(monthlyBudget = null))
            _categoriesTrigger.value = System.currentTimeMillis()
        }
    }

    fun updateCategoryForMonth(category: Category, month: Int, year: Int, isRecurring: Boolean = false, isSettled: Boolean = false) {
        viewModelScope.launch {
            repository.setCategoryBudgetForMonth(category.id, month, year, category.monthlyBudget, isRecurring)
            repository.setCategoryBudgetSettledForMonth(category.id, month, year, isSettled)
            repository.updateCategory(category.copy(monthlyBudget = null))
            _categoriesTrigger.value = System.currentTimeMillis()
        }
    }

    fun getCategoryBudgetForMonth(categoryId: Int, month: Int, year: Int): Double? {
        return repository.getCategoryBudgetForMonth(categoryId, month, year)
    }

    suspend fun canDeleteCategory(categoryId: Int): Boolean {
        return repository.getExpenseCountForCategory(categoryId) == 0
    }

    suspend fun getExpenseCountForCategory(categoryId: Int): Int {
        return repository.getExpenseCountForCategory(categoryId)
    }

    suspend fun getExpenseCountForCategoryInMonth(categoryId: Int, month: Int, year: Int): Int {
        return repository.getExpenseCountForCategoryInMonth(categoryId, month, year)
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            _categoriesTrigger.value = System.currentTimeMillis()
        }
    }

    fun deleteCategoryForMonth(category: Category, month: Int, year: Int) {
        viewModelScope.launch {
            val totalCount = repository.getExpenseCountForCategory(category.id)
            if (totalCount == 0) {
                repository.deleteCategory(category)
            } else {
                repository.setCategoryDeletedForMonth(category.id, month, year, true)
            }
            _categoriesTrigger.value = System.currentTimeMillis()
        }
    }

    // Profile & Settings Management
    fun saveName(name: String) {
        repository.setUserName(name)
        _userName.value = name
    }

    fun saveTheme(theme: String) {
        repository.setThemeMode(theme)
        _themeMode.value = theme
    }

    private val _globalMonthlyBudget = MutableStateFlow(repository.getGlobalMonthlyBudget())
    val globalMonthlyBudget: StateFlow<Double> = _globalMonthlyBudget.asStateFlow()

    fun saveGlobalMonthlyBudget(budget: Double) {
        repository.setGlobalMonthlyBudget(budget)
        _globalMonthlyBudget.value = budget
    }

    fun saveOAuthCredentials(email: String, accessToken: String, displayName: String = "") {
        repository.setSavedEmail(email)
        repository.setSavedAccessToken(accessToken)
        _googleEmail.value = email
        _googleAccessToken.value = accessToken
        if (displayName.isNotEmpty()) {
            saveName(displayName)
        } else if (email.isNotEmpty() && email.contains("@")) {
            val autoName = email.substringBefore("@").replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() 
            }
            saveName(autoName)
        }
    }

    fun clearOAuthCredentials() {
        repository.setSavedEmail("")
        repository.setSavedAccessToken("")
        repository.setConnectedSheetId("")
        repository.setSheetsSyncEnabled(false)
        _googleEmail.value = ""
        _googleAccessToken.value = ""
        _connectedSheetId.value = ""
        _sheetsSyncEnabled.value = false
    }

    fun toggleSheetsSync(enabled: Boolean) {
        repository.setSheetsSyncEnabled(enabled)
        _sheetsSyncEnabled.value = enabled
    }

    fun setConnectedSheet(sheetId: String) {
        repository.setConnectedSheetId(sheetId)
        _connectedSheetId.value = sheetId
    }

    // Batch Export to Google Sheets
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    // Manual Backup & Restore
    suspend fun exportBackupJson(): String {
        return repository.getBackupJson()
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        val success = repository.importBackupJson(jsonString)
        if (success) {
            _userName.value = repository.getUserName()
            _globalMonthlyBudget.value = repository.getGlobalMonthlyBudget()
            _themeMode.value = repository.getThemeMode()
            _selectedCurrency.value = repository.getSelectedCurrency()
            _budgetAlertsEnabled.value = repository.isBudgetAlertsEnabled()
            
            val dailyEnabled = repository.isDailySpendNotificationEnabled()
            val dailyTime = repository.getDailySpendNotificationTime()
            _dailySpendNotificationEnabled.value = dailyEnabled
            _dailySpendNotificationTime.value = dailyTime
            
            _sheetsSyncEnabled.value = repository.isSheetsSyncEnabled()
            _connectedSheetId.value = repository.getConnectedSheetId()
            _categoriesTrigger.value = System.currentTimeMillis()
            
            // Reschedule daily spend notification according to the imported profile settings
            if (dailyEnabled) {
                val parts = dailyTime.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                com.example.NotificationScheduler.scheduleDailyNotification(getApplication(), hour, minute)
            } else {
                com.example.NotificationScheduler.cancelDailyNotification(getApplication())
            }
            
            // Re-trigger widget update with a brief delay to ensure thread synchronization
            try {
                kotlinx.coroutines.delay(200)
                repository.updateAllWidgets()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return success
    }

    fun exportToGoogleSheets() {
        val token = _googleAccessToken.value
        if (token.isEmpty()) {
            _exportState.value = ExportState.Error("Sign-in required to export.")
            return
        }
        _exportState.value = ExportState.Loading
        viewModelScope.launch {
            val success = repository.exportAllToGoogleSheets()
            if (success) {
                // Refresh sheet state
                _connectedSheetId.value = repository.getConnectedSheetId()
                _exportState.value = ExportState.Success
            } else {
                _exportState.value = ExportState.Error("Failed to export. Please check connection.")
            }
        }
    }
}

sealed interface ExportState {
    object Idle : ExportState
    object Loading : ExportState
    object Success : ExportState
    data class Error(val message: String) : ExportState
}

class TrackerViewModelFactory(
    private val application: Application,
    private val repository: ExpenseRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackerViewModel::class.java)) {
            return TrackerViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
