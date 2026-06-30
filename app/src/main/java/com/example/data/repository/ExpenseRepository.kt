package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.CategoryDao
import com.example.data.local.ExpenseDao
import com.example.data.model.Category
import com.example.data.model.Expense
import com.example.data.remote.GoogleSheetsService
import com.example.data.remote.ExportRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class ExpenseRepository(
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rupee_tracker_prefs", Context.MODE_PRIVATE)

    // Room Flow queries
    val allExpensesFlow: Flow<List<Expense>> = expenseDao.getAllExpensesFlow()
    val allCategoriesFlow: Flow<List<Category>> = categoryDao.getAllCategoriesFlow()

    // Room Standard queries
    suspend fun getAllExpenses(): List<Expense> = withContext(Dispatchers.IO) {
        expenseDao.getAllExpenses()
    }

    suspend fun getAllCategories(): List<Category> = withContext(Dispatchers.IO) {
        categoryDao.getAllCategories()
    }

    suspend fun getCategoryById(id: Int): Category? = withContext(Dispatchers.IO) {
        categoryDao.getCategoryById(id)
    }

    suspend fun getExpenseCountForCategory(categoryId: Int): Int = withContext(Dispatchers.IO) {
        expenseDao.getExpenseCountForCategory(categoryId)
    }

    // Room Write operations
    suspend fun insertExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        val id = expenseDao.insertExpense(expense)
        
        try {
            autoUnsettleCategory(expense.categoryId, expense.date)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Error auto-unsettling category", e)
        }

        try {
            checkBudgetNotifications(expense.categoryId, expense.date)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Error checking budget notification", e)
        }

        // Check if Google Sheets live sync is enabled
        if (isSheetsSyncEnabled()) {
            val token = getSavedAccessToken()
            var sheetId = getConnectedSheetId()
            if (token.isNotEmpty()) {
                val category = categoryDao.getCategoryById(expense.categoryId)
                val categoryName = category?.name ?: "Unknown"
                
                if (sheetId.isEmpty()) {
                    // Create new sheet
                    val newId = GoogleSheetsService.createSpreadsheet(token, "Rupee Tracker Expenses")
                    if (newId != null) {
                        sheetId = newId
                        setConnectedSheetId(newId)
                    }
                }
                
                if (sheetId.isNotEmpty()) {
                    GoogleSheetsService.appendExpense(
                        accessToken = token,
                        spreadsheetId = sheetId,
                        dateTimestamp = expense.date,
                        description = expense.description,
                        categoryName = categoryName,
                        amount = expense.amount
                    )
                }
            }
        }
        updateAllWidgets()
        id
    }

    suspend fun updateExpense(expense: Expense) = withContext(Dispatchers.IO) {
        expenseDao.updateExpense(expense)
        try {
            autoUnsettleCategory(expense.categoryId, expense.date)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Error auto-unsettling category", e)
        }
        try {
            checkBudgetNotifications(expense.categoryId, expense.date)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Error checking budget notification", e)
        }
        updateAllWidgets()
    }

    suspend fun deleteExpense(expense: Expense) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(expense)
        updateAllWidgets()
    }

    suspend fun deleteExpenseById(id: Int) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpenseById(id)
        updateAllWidgets()
    }

    suspend fun insertCategory(category: Category): Long = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategory(category)
    }

    // Export manually to Google Sheets
    suspend fun exportAllToGoogleSheets(): Boolean = withContext(Dispatchers.IO) {
        val token = getSavedAccessToken()
        if (token.isEmpty()) return@withContext false
        
        var sheetId = getConnectedSheetId()
        if (sheetId.isEmpty()) {
            val newId = GoogleSheetsService.createSpreadsheet(token, "Rupee Tracker Expenses")
            if (newId != null) {
                sheetId = newId
                setConnectedSheetId(newId)
            } else {
                return@withContext false
            }
        }

        val expenses = expenseDao.getAllExpenses()
        val categoriesMap = categoryDao.getAllCategories().associateBy { it.id }
        
        val itemsList = expenses.map { expense ->
            val catName = categoriesMap[expense.categoryId]?.name ?: "Unknown"
            ExportRow(expense.date, expense.description, catName, expense.amount)
        }

        GoogleSheetsService.exportAll(token, sheetId, itemsList)
    }

    // Preferences operations
    fun getUserName(): String = prefs.getString("user_name", "Guest User") ?: "Guest User"
    fun setUserName(name: String) = prefs.edit().putString("user_name", name).apply()

    fun getSelectedCurrency(): String = prefs.getString("selected_currency", "INR") ?: "INR"
    fun setSelectedCurrency(currency: String) = prefs.edit().putString("selected_currency", currency).apply()

    fun getCurrencySymbol(): String {
        return when (getSelectedCurrency()) {
            "INR" -> "₹"
            "USD" -> "$"
            "GBP" -> "£"
            "EUR" -> "€"
            "AED" -> "AED"
            else -> "₹"
        }
    }

    fun getThemeMode(): String = prefs.getString("theme_mode", "system") ?: "system"
    fun setThemeMode(mode: String) = prefs.edit().putString("theme_mode", mode).apply()

    fun isSheetsSyncEnabled(): Boolean = prefs.getBoolean("sheets_sync_enabled", false)
    fun setSheetsSyncEnabled(enabled: Boolean) = prefs.edit().putBoolean("sheets_sync_enabled", enabled).apply()

    fun getConnectedSheetId(): String = prefs.getString("connected_sheet_id", "") ?: ""
    fun setConnectedSheetId(sheetId: String) = prefs.edit().putString("connected_sheet_id", sheetId).apply()

    fun getSavedAccessToken(): String = prefs.getString("saved_access_token", "") ?: ""
    fun setSavedAccessToken(token: String) = prefs.edit().putString("saved_access_token", token).apply()

    fun getSavedEmail(): String = prefs.getString("saved_email", "") ?: ""
    fun setSavedEmail(email: String) = prefs.edit().putString("saved_email", email).apply()

    fun getCategoryBudgetForMonth(categoryId: Int, month: Int, year: Int): Double? {
        val clearKey = "category_budget_cleared_${categoryId}_${month}_${year}"
        if (prefs.getBoolean(clearKey, false)) {
            return null
        }

        val key = "category_budget_${categoryId}_${month}_${year}"
        if (prefs.contains(key)) {
            val value = prefs.getFloat(key, -1f)
            return if (value < 0f) null else value.toDouble()
        }

        // Search backwards up to 36 months helper
        var currentM = month
        var currentY = year
        for (i in 1..36) {
            currentM--
            if (currentM < 1) {
                currentM = 12
                currentY--
            }

            val pastKey = "category_budget_${categoryId}_${currentM}_${currentY}"
            val pastClearKey = "category_budget_cleared_${categoryId}_${currentM}_${currentY}"
            if (prefs.contains(pastKey)) {
                val isRecurring = prefs.getBoolean("category_recurring_${categoryId}_${currentM}_${currentY}", false)
                if (isRecurring) {
                    val value = prefs.getFloat(pastKey, -1f)
                    return if (value < 0f) null else value.toDouble()
                } else {
                    // Manual/non-recurring past budget halts inheritance
                    return null
                }
            } else if (prefs.getBoolean(pastClearKey, false)) {
                // If explicitly cleared/deleted in an intermediate month, it is manual none, so halts inheritance
                return null
            }
        }
        return null
    }

    fun isCategoryBudgetRecurringForMonth(categoryId: Int, month: Int, year: Int): Boolean {
        val clearKey = "category_budget_cleared_${categoryId}_${month}_${year}"
        if (prefs.getBoolean(clearKey, false)) {
            return false
        }

        val key = "category_recurring_${categoryId}_${month}_${year}"
        if (prefs.contains("category_budget_${categoryId}_${month}_${year}")) {
            return prefs.getBoolean(key, false)
        }

        // Backwards lookup (up to 36 months) to determine if inherited budget is recurring
        var currentM = month
        var currentY = year
        for (i in 1..36) {
            currentM--
            if (currentM < 1) {
                currentM = 12
                currentY--
            }

            val pastKey = "category_budget_${categoryId}_${currentM}_${currentY}"
            val pastClearKey = "category_budget_cleared_${categoryId}_${currentM}_${currentY}"
            if (prefs.contains(pastKey)) {
                return prefs.getBoolean("category_recurring_${categoryId}_${currentM}_${currentY}", false)
            } else if (prefs.getBoolean(pastClearKey, false)) {
                return false
            }
        }
        return false
    }

    fun setCategoryBudgetForMonth(categoryId: Int, month: Int, year: Int, budget: Double?, isRecurring: Boolean = false) {
        val key = "category_budget_${categoryId}_${month}_${year}"
        val recKey = "category_recurring_${categoryId}_${month}_${year}"
        val clearKey = "category_budget_cleared_${categoryId}_${month}_${year}"
        if (budget == null) {
            prefs.edit()
                .remove(key)
                .remove(recKey)
                .putBoolean(clearKey, true)
                .apply()
        } else {
            prefs.edit()
                .putFloat(key, budget.toFloat())
                .putBoolean(recKey, isRecurring)
                .remove(clearKey)
                .apply()
        }
    }

    fun isCategoryDeletedForMonth(categoryId: Int, month: Int, year: Int): Boolean {
        val key = "category_deleted_${categoryId}_${month}_${year}"
        return prefs.getBoolean(key, false)
    }

    fun isCategoryBudgetSettledForMonth(categoryId: Int, month: Int, year: Int): Boolean {
        val key = "category_settled_${categoryId}_${month}_${year}"
        return prefs.getBoolean(key, false)
    }

    fun setCategoryBudgetSettledForMonth(categoryId: Int, month: Int, year: Int, settled: Boolean) {
        val key = "category_settled_${categoryId}_${month}_${year}"
        if (!settled) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    fun isBudgetPromptDismissed(month: Int, year: Int): Boolean {
        val key = "budget_prompt_dismissed_${month}_${year}"
        return prefs.getBoolean(key, false)
    }

    fun setBudgetPromptDismissed(month: Int, year: Int, dismissed: Boolean) {
        val key = "budget_prompt_dismissed_${month}_${year}"
        if (dismissed) {
            prefs.edit().putBoolean(key, true).apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }

    suspend fun copyMonthlyBudgets(srcMonth: Int, srcYear: Int, targetMonth: Int, targetYear: Int) {
        val categoriesList = getAllCategories()
        val editor = prefs.edit()
        for (cat in categoriesList) {
            val resolvedBudget = getCategoryBudgetForMonth(cat.id, srcMonth, srcYear)
            val resolvedRecurring = isCategoryBudgetRecurringForMonth(cat.id, srcMonth, srcYear)
            val resolvedSettled = isCategoryBudgetSettledForMonth(cat.id, srcMonth, srcYear)
            val resolvedDeleted = isCategoryDeletedForMonth(cat.id, srcMonth, srcYear)

            val targetBudgetKey = "category_budget_${cat.id}_${targetMonth}_${targetYear}"
            val targetRecKey = "category_recurring_${cat.id}_${targetMonth}_${targetYear}"
            val targetClearKey = "category_budget_cleared_${cat.id}_${targetMonth}_${targetYear}"
            val targetSettledKey = "category_settled_${cat.id}_${targetMonth}_${targetYear}"
            val targetDeletedKey = "category_deleted_${cat.id}_${targetMonth}_${targetYear}"

            if (resolvedBudget != null) {
                editor.putFloat(targetBudgetKey, resolvedBudget.toFloat())
                editor.putBoolean(targetRecKey, resolvedRecurring)
                editor.remove(targetClearKey)
            } else {
                editor.remove(targetBudgetKey)
                editor.remove(targetRecKey)
                editor.putBoolean(targetClearKey, true)
            }

            if (resolvedSettled) {
                editor.putBoolean(targetSettledKey, true)
            } else {
                editor.remove(targetSettledKey)
            }

            if (resolvedDeleted) {
                editor.putBoolean(targetDeletedKey, true)
            } else {
                editor.remove(targetDeletedKey)
            }
        }
        editor.putBoolean("budget_prompt_dismissed_${targetMonth}_${targetYear}", true)
        editor.apply()
    }

    private fun autoUnsettleCategory(categoryId: Int, date: Long) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = date }
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val year = cal.get(java.util.Calendar.YEAR)
        setCategoryBudgetSettledForMonth(categoryId, month, year, false)
    }

    fun setCategoryDeletedForMonth(categoryId: Int, month: Int, year: Int, deleted: Boolean) {
        val key = "category_deleted_${categoryId}_${month}_${year}"
        if (!deleted) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    suspend fun getExpensesForMonthAndCategory(categoryId: Int, year: Int, month: Int): List<Expense> = withContext(Dispatchers.IO) {
        val all = expenseDao.getAllExpenses()
        all.filter { exp ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = exp.date }
            val m = cal.get(java.util.Calendar.MONTH) + 1
            val y = cal.get(java.util.Calendar.YEAR)
            exp.categoryId == categoryId && m == month && y == year
        }
    }

    suspend fun getExpenseCountForCategoryInMonth(categoryId: Int, month: Int, year: Int): Int {
        return getExpensesForMonthAndCategory(categoryId, year, month).size
    }

    suspend fun checkBudgetNotifications(categoryId: Int, expenseDate: Long) {
        if (!isBudgetAlertsEnabled()) return

        val category = categoryDao.getCategoryById(categoryId) ?: return
        val budget = category.monthlyBudget ?: return
        if (budget <= 0.0) return

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = expenseDate }
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val year = cal.get(java.util.Calendar.YEAR)
        val monthName = java.text.DateFormatSymbols().months[month - 1]

        val expenses = getExpensesForMonthAndCategory(categoryId, year, month)
        val totalSpent = expenses.sumOf { it.amount }

        val ratio = totalSpent / budget
        val monthYearStr = String.format(java.util.Locale.US, "%04d-%02d", year, month)

        if (ratio > 1.0) {
            val hasFired = hasAlertFired(categoryId, monthYearStr, 3)
            if (!hasFired) {
                val overAmount = totalSpent - budget
                val msg = "You're ${getCurrencySymbol()}${String.format(java.util.Locale.US, "%,.0f", overAmount)} over your ${category.name} budget."
                showNotification(category.name, msg, month, year)
                setAlertFired(categoryId, monthYearStr, 3)
            }
        } else if (ratio >= 1.0) {
            val hasFired = hasAlertFired(categoryId, monthYearStr, 2)
            if (!hasFired) {
                val msg = "You've reached your ${getCurrencySymbol()}${String.format(java.util.Locale.US, "%,.0f", budget)} ${category.name} budget for $monthName."
                showNotification(category.name, msg, month, year)
                setAlertFired(categoryId, monthYearStr, 2)
            }
        } else if (ratio >= 0.8) {
            val hasFired = hasAlertFired(categoryId, monthYearStr, 1)
            if (!hasFired) {
                val msg = "Heads up! You've used 80% of your ${getCurrencySymbol()}${String.format(java.util.Locale.US, "%,.0f", budget)} ${category.name} budget this month."
                showNotification(category.name, msg, month, year)
                setAlertFired(categoryId, monthYearStr, 1)
            }
        }
    }

    @android.annotation.SuppressLint("NotificationPermission")
    private fun showNotification(categoryName: String, message: String, month: Int, year: Int) {
        createNotificationChannel()

        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reports")
            putExtra("filter_month", month)
            putExtra("filter_year", year)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            (categoryName.hashCode() + month + year).and(0xffff),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, "budget_alerts_channel")
            .setSmallIcon(com.example.R.drawable.ic_app_logo)
            .setContentTitle("⚠️ Budget Alert: $categoryName")
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle()
                .bigText("$message\n\nTake control of your spending items inside Monthly Reports to manage your limit.")
                .setBigContentTitle("⚠️ Budget Limit Exceeded")
                .setSummaryText("Budget Monitor")
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFFE53935.toInt()) // High-Contrast warnings red
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "📊 View Analytics", pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify((categoryName.hashCode() + month + year).and(0xffff), builder.build())
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Budget Alerts"
            val descriptionText = "Notifications for category monthly budget alerts"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel("budget_alerts_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun isBudgetAlertsEnabled(): Boolean = prefs.getBoolean("budget_alerts_enabled", true)
    fun setBudgetAlertsEnabled(enabled: Boolean) = prefs.edit().putBoolean("budget_alerts_enabled", enabled).apply()

    fun isDailySpendNotificationEnabled(): Boolean = prefs.getBoolean("daily_spend_notification_enabled", false)
    fun setDailySpendNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("daily_spend_notification_enabled", enabled).apply()

    fun getDailySpendNotificationTime(): String = prefs.getString("daily_spend_notification_time", "23:00") ?: "23:00"
    fun setDailySpendNotificationTime(time: String) = prefs.edit().putString("daily_spend_notification_time", time).apply()

    fun getGlobalMonthlyBudget(): Double = prefs.getFloat("global_monthly_budget", 15000f).toDouble()
    fun setGlobalMonthlyBudget(budget: Double) = prefs.edit().putFloat("global_monthly_budget", budget.toFloat()).apply()

    fun hasAlertFired(categoryId: Int, monthYear: String, threshold: Int): Boolean {
        val key = "alert_fired_${categoryId}_${monthYear}_${threshold}"
        return prefs.getBoolean(key, false)
    }

    fun setAlertFired(categoryId: Int, monthYear: String, threshold: Int) {
        val key = "alert_fired_${categoryId}_${monthYear}_${threshold}"
        prefs.edit().putBoolean(key, true).apply()
    }

    fun updateAllWidgets() {
        try {
            val updateIntent = android.content.Intent(context, com.example.widget.MonthSpendingWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val widgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                val ids = widgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, com.example.widget.MonthSpendingWidgetProvider::class.java)
                )
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(updateIntent)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Failed to update widgets", e)
        }
    }

    suspend fun getBackupJson(): String = withContext(Dispatchers.IO) {
        val categories = getAllCategories()
        val expenses = getAllExpenses()
        
        val json = org.json.JSONObject()
        json.put("backupVersion", 1)
        json.put("userName", getUserName())
        json.put("globalMonthlyBudget", getGlobalMonthlyBudget())
        json.put("themeMode", getThemeMode())
        
        // Export additional monthly/historical budget and settings preferences
        val prefsObj = org.json.JSONObject()
        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            if (key == "user_name" || 
                key == "theme_mode" || 
                key == "selected_currency" ||
                key == "global_monthly_budget" || 
                key == "budget_alerts_enabled" ||
                key == "daily_spend_notification_enabled" ||
                key == "daily_spend_notification_time" ||
                key == "sheets_sync_enabled" ||
                key == "connected_sheet_id" ||
                key == "balance_hidden" ||
                key.startsWith("category_budget_") ||
                key.startsWith("category_recurring_") ||
                key.startsWith("category_budget_cleared_") ||
                key.startsWith("category_settled_") ||
                key.startsWith("category_deleted_") ||
                key.startsWith("alert_fired_")
            ) {
                prefsObj.put(key, value ?: org.json.JSONObject.NULL)
            }
        }
        json.put("preferences", prefsObj)
        
        val catsArray = org.json.JSONArray()
        for (cat in categories) {
            val catObj = org.json.JSONObject()
            catObj.put("id", cat.id)
            catObj.put("name", cat.name)
            catObj.put("iconName", cat.iconName)
            catObj.put("colorHex", cat.colorHex)
            catObj.put("monthlyBudget", cat.monthlyBudget ?: org.json.JSONObject.NULL)
            catObj.put("isIncome", cat.isIncome)
            catObj.put("isSavings", cat.isSavings)
            catsArray.put(catObj)
        }
        json.put("categories", catsArray)
        
        val expsArray = org.json.JSONArray()
        for (exp in expenses) {
            val expObj = org.json.JSONObject()
            expObj.put("id", exp.id)
            expObj.put("amount", exp.amount)
            expObj.put("description", exp.description)
            expObj.put("date", exp.date)
            expObj.put("categoryId", exp.categoryId)
            expObj.put("isIncome", exp.isIncome)
            expObj.put("isSavings", exp.isSavings)
            expObj.put("createdAt", exp.createdAt)
            expsArray.put(expObj)
        }
        json.put("expenses", expsArray)
        
        json.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = org.json.JSONObject(jsonString)
            
            if (!json.has("categories") || !json.has("expenses")) {
                return@withContext false
            }

            val savedToken = getSavedAccessToken()
            val savedEmail = getSavedEmail()
            val editor = prefs.edit()
            editor.clear()
            if (savedToken.isNotEmpty()) {
                editor.putString("saved_access_token", savedToken)
            }
            if (savedEmail.isNotEmpty()) {
                editor.putString("saved_email", savedEmail)
            }

            if (json.has("userName")) {
                editor.putString("user_name", json.getString("userName"))
            }
            if (json.has("globalMonthlyBudget")) {
                editor.putFloat("global_monthly_budget", json.getDouble("globalMonthlyBudget").toFloat())
            }
            if (json.has("themeMode")) {
                editor.putString("theme_mode", json.getString("themeMode"))
            }

            // Restore custom category budgets and check settings
            if (json.has("preferences")) {
                val prefsObj = json.getJSONObject("preferences")
                val keys = prefsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    try {
                        if (prefsObj.isNull(key)) {
                            editor.remove(key)
                        } else {
                            val value = prefsObj.get(key)
                            when {
                                key == "user_name" || key == "theme_mode" || key == "selected_currency" || key == "connected_sheet_id" || key == "daily_spend_notification_time" -> {
                                    editor.putString(key, value.toString())
                                }
                                key == "global_monthly_budget" || (key.startsWith("category_budget_") && !key.startsWith("category_budget_cleared_")) -> {
                                    val floatVal = when (value) {
                                        is Number -> value.toFloat()
                                        is String -> value.toFloatOrNull() ?: 0f
                                        else -> 0f
                                    }
                                    editor.putFloat(key, floatVal)
                                }
                                key == "budget_alerts_enabled" || 
                                key == "daily_spend_notification_enabled" ||
                                key == "sheets_sync_enabled" ||
                                key == "balance_hidden" ||
                                key.startsWith("category_recurring_") || 
                                key.startsWith("category_budget_cleared_") || 
                                key.startsWith("category_settled_") || 
                                key.startsWith("category_deleted_") ||
                                key.startsWith("alert_fired_") -> {
                                    val boolVal = when (value) {
                                        is Boolean -> value
                                        is String -> value.toBoolean()
                                        is Number -> value.toInt() != 0
                                        else -> false
                                    }
                                    editor.putBoolean(key, boolVal)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Error processing preference key: $key", e)
                    }
                }
            }

            expenseDao.deleteAllExpenses()
            categoryDao.deleteAllCategories()

            val catsArray = json.getJSONArray("categories")
            val categoriesList = mutableListOf<Category>()
            val calNow = java.util.Calendar.getInstance()
            val currentM = calNow.get(java.util.Calendar.MONTH) + 1
            val currentY = calNow.get(java.util.Calendar.YEAR)

            for (i in 0 until catsArray.length()) {
                val catObj = catsArray.getJSONObject(i)
                val id = catObj.getInt("id")
                val name = catObj.getString("name")
                val iconName = catObj.getString("iconName")
                val colorHex = catObj.getString("colorHex")
                val monthlyBudget = if (catObj.isNull("monthlyBudget")) null else catObj.getDouble("monthlyBudget")
                val isIncome = catObj.optBoolean("isIncome", false)
                val isSavings = catObj.optBoolean("isSavings", false)
                
                categoriesList.add(
                    Category(
                        id = id,
                        name = name,
                        iconName = iconName,
                        colorHex = colorHex,
                        monthlyBudget = monthlyBudget,
                        isIncome = isIncome,
                        isSavings = isSavings
                    )
                )

                // Legacy budget migration into SharedPreferences recurring budget
                if (monthlyBudget != null) {
                    val key = "category_budget_${id}_${currentM}_${currentY}"
                    val recKey = "category_recurring_${id}_${currentM}_${currentY}"
                    val clearKey = "category_budget_cleared_${id}_${currentM}_${currentY}"
                    
                    // Only populate if not already defined in the imported preferences or explicitly cleared
                    val inImportedPrefs = json.has("preferences") && json.getJSONObject("preferences").has(key)
                    val inImportedCleared = json.has("preferences") && json.getJSONObject("preferences").has(clearKey)
                    
                    if (!inImportedPrefs && !inImportedCleared) {
                        editor.putFloat(key, monthlyBudget.toFloat())
                        editor.putBoolean(recKey, true)
                    }
                }
            }

            // Commit all SharedPreferences synchronously before database write so that Room Flows find updated values immediately!
            editor.commit()
            
            val expsArray = json.getJSONArray("expenses")
            val expensesList = mutableListOf<Expense>()
            for (i in 0 until expsArray.length()) {
                val expObj = expsArray.getJSONObject(i)
                val id = expObj.getInt("id")
                val amount = expObj.getDouble("amount")
                val description = expObj.getString("description")
                val date = expObj.getLong("date")
                val categoryId = expObj.getInt("categoryId")
                val isIncome = expObj.optBoolean("isIncome", false)
                val isSavings = expObj.optBoolean("isSavings", false)
                val createdAt = expObj.optLong("createdAt", date)
                
                expensesList.add(
                    Expense(
                        id = id,
                        amount = amount,
                        description = description,
                        date = date,
                        categoryId = categoryId,
                        isIncome = isIncome,
                        isSavings = isSavings,
                        createdAt = createdAt
                    )
                )
            }

            categoryDao.insertCategories(categoriesList)
            expenseDao.insertExpenses(expensesList)
            updateAllWidgets()

            true
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Error restoring backup JSON", e)
            false
        }
    }
}
