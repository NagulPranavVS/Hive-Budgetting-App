package com.example

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DailySpendNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule alarm on boot
            reschedule(context)
            return
        }

        val app = context.applicationContext as? TrackerApplication ?: return
        val repo = app.repository

        if (!repo.isDailySpendNotificationEnabled()) {
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                // Get all expenses
                val expenses = repo.getAllExpenses()

                // Calculate today's spent amount
                val calToday = Calendar.getInstance()
                val d = calToday.get(Calendar.DAY_OF_MONTH)
                val m = calToday.get(Calendar.MONTH) + 1
                val y = calToday.get(Calendar.YEAR)

                val spentTodayVal = expenses.filter { exp ->
                    if (exp.isIncome) return@filter false
                    if (exp.isSavings) return@filter false
                    val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
                    calExp.get(Calendar.DAY_OF_MONTH) == d &&
                            (calExp.get(Calendar.MONTH) + 1) == m &&
                            calExp.get(Calendar.YEAR) == y
                }.sumOf { it.amount }

                val currencySymbol = repo.getCurrencySymbol()
                val formattedAmount = String.format(java.util.Locale.US, "%,.2f", spentTodayVal)

                val notificationTitle = "📊 Daily Spend Balance"
                val notificationMessage = "💰 Total Spent Today: $currencySymbol$formattedAmount"

                showNotification(
                    context,
                    notificationTitle,
                    notificationMessage
                )
            } catch (e: Exception) {
                Log.e("DailySpendNotification", "Error calculating daily spent", e)
            } finally {
                // Schedule next day's alarm
                reschedule(context)
                pendingResult.finish()
            }
        }
    }

    private fun reschedule(context: Context) {
        val app = context.applicationContext as? TrackerApplication ?: return
        val repo = app.repository
        if (repo.isDailySpendNotificationEnabled()) {
            val time = repo.getDailySpendNotificationTime()
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            NotificationScheduler.scheduleDailyNotification(context, hour, minute)
        }
    }

    @SuppressLint("NotificationPermission")
    private fun showNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context)

        // General click on the notification launches MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "home")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2026,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct action 1: Add Expense
        val addIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.action.ADD_EXPENSE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addPendingIntent = PendingIntent.getActivity(
            context,
            2027,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct action 2: View Analytics Reports
        val reportsIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reports")
        }
        val reportsPendingIntent = PendingIntent.getActivity(
            context,
            2028,
            reportsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionTitle = SpannableString("View Reports ›").apply {
            setSpan(UnderlineSpan(), 0, this.length, 0)
            setSpan(StyleSpan(Typeface.BOLD), 0, this.length, 0)
        }

        val builder = NotificationCompat.Builder(context, "daily_spends_channel")
            .setSmallIcon(com.example.R.drawable.ic_app_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(message)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF5F56FF.toInt()) // Premium brand indigo accent
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, actionTitle, reportsPendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2026, builder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Spend Summary"
            val descriptionText = "Summary of your total modern expenditures at chosen time daily"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("daily_spends_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
