package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.TrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class MonthSpendingWidgetProvider : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as? TrackerApplication
                val repository = app?.repository
                
                var spentThisMonth = 0.0
                var incomeThisMonth = 0.0
                var savingsThisMonth = 0.0
                var spentToday = 0.0
                
                try {
                    val expenses = repository?.getAllExpenses() ?: emptyList()
                    val calNow = Calendar.getInstance()
                    val d = calNow.get(Calendar.DAY_OF_MONTH)
                    val m = calNow.get(Calendar.MONTH) + 1
                    val y = calNow.get(Calendar.YEAR)
                    
                    for (exp in expenses) {
                        val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
                        val expMonth = calExp.get(Calendar.MONTH) + 1
                        val expYear = calExp.get(Calendar.YEAR)
                        val expDay = calExp.get(Calendar.DAY_OF_MONTH)
                        
                        // Current month calculations
                        if (expMonth == m && expYear == y) {
                            if (exp.isIncome) {
                                incomeThisMonth += exp.amount
                            } else if (exp.isSavings) {
                                savingsThisMonth += exp.amount
                            } else {
                                spentThisMonth += exp.amount
                            }
                        }
                        
                        // Today's spends calculation (non-income, non-savings)
                        if (expDay == d && expMonth == m && expYear == y && !exp.isIncome && !exp.isSavings) {
                            spentToday += exp.amount
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val isBalanceHidden = prefs.getBoolean("balance_hidden", true)

                val remainingBalance = incomeThisMonth - savingsThisMonth - spentThisMonth

                val repoPrefs = context.getSharedPreferences("rupee_tracker_prefs", Context.MODE_PRIVATE)
                val isSelectedCurrency = repoPrefs.getString("selected_currency", "INR") ?: "INR"
                val currSymbol = when (isSelectedCurrency) {
                    "INR" -> "₹"
                    "USD" -> "$"
                    "GBP" -> "£"
                    "EUR" -> "€"
                    "AED" -> "AED"
                    else -> "₹"
                }

                val balanceFormatted = currSymbol + NumberFormat.getNumberInstance(Locale.US).format(Math.round(remainingBalance))
                val balanceTextToShow = if (isBalanceHidden) "${currSymbol}••••" else balanceFormatted
                val eyeIconRes = if (isBalanceHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility

                val spentFormatted = currSymbol + NumberFormat.getNumberInstance(Locale.US).format(Math.round(spentThisMonth))
                val spentTodayFormatted = currSymbol + NumberFormat.getNumberInstance(Locale.US).format(Math.round(spentToday))

                val spentTextToShow = if (isBalanceHidden) "${currSymbol}••••" else spentFormatted
                val spentTodayTextToShow = if (isBalanceHidden) "${currSymbol}••••" else spentTodayFormatted

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_month_spending)
                    
                    views.setTextViewText(R.id.widget_balance_amount, balanceTextToShow)
                    views.setImageViewResource(R.id.widget_eye_toggle, eyeIconRes)
                    views.setTextViewText(R.id.widget_month_spends_amount, spentTextToShow)
                    views.setTextViewText(R.id.widget_today_spends_amount, spentTodayTextToShow)
                    
                    // Add Expense Intent
                    val expenseIntent = Intent(context, MainActivity::class.java).apply {
                        action = "com.example.action.ADD_EXPENSE"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val expensePendingIntent = PendingIntent.getActivity(
                        context,
                        1001,
                        expenseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_add_expense, expensePendingIntent)
                    
                    // Toggle Balance Visibility Intent
                    val toggleIntent = Intent(context, MonthSpendingWidgetProvider::class.java).apply {
                        action = "com.example.action.TOGGLE_BALANCE_VISIBILITY"
                    }
                    val togglePendingIntent = PendingIntent.getBroadcast(
                        context,
                        1003,
                        toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_eye_toggle, togglePendingIntent)
                    
                    // Open App Intent when clicking anywhere else on the widget card body (Preview detail)
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val openAppPendingIntent = PendingIntent.getActivity(
                        context,
                        1002,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                    
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.action.TOGGLE_BALANCE_VISIBILITY") {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val current = prefs.getBoolean("balance_hidden", true)
            prefs.edit().putBoolean("balance_hidden", !current).apply()
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context, MonthSpendingWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        job.cancel()
    }
}
