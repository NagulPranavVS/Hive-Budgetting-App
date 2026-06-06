package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Expense
import com.example.ui.CategoryIconHelper
import com.example.ui.TrackerViewModel
import com.example.ui.components.SetBudgetDialog
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MonthlyReportScreen(
    viewModel: TrackerViewModel
) {
    val reportExpenses by viewModel.reportExpenses.collectAsState()
    val totalReportSpent by viewModel.totalReportSpent.collectAsState()
    val reportMonth by viewModel.reportMonth.collectAsState()
    val reportYear by viewModel.reportYear.collectAsState()
    val categoriesTrigger by viewModel.categoriesTrigger.collectAsState()
    
    val categoriesFlow = remember(reportMonth, reportYear) {
        viewModel.getCategoriesForMonth(reportMonth, reportYear)
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val selectedAnalyticsTab by viewModel.selectedAnalyticsTab.collectAsState()
    val isTransactions = selectedAnalyticsTab == "transactions"

    val categoriesMap = remember(categories, categoriesTrigger) { categories.associateBy { it.id } }

    val budgetedCategories = remember(categories) {
        categories.filter { !it.isIncome && !it.isSavings && (it.monthlyBudget ?: 0.0) > 0.0 }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSetBudgetDialogForCategory by remember { mutableStateOf<Category?>(null) }

    val dialogCat = showSetBudgetDialogForCategory
    if (dialogCat != null) {
        val isRecInitial = viewModel.isCategoryBudgetRecurringForMonth(dialogCat.id, reportMonth, reportYear)
        val isSettledInitial = viewModel.isCategoryBudgetSettledForMonth(dialogCat.id, reportMonth, reportYear)
        val reportIncome = reportExpenses.filter { it.isIncome && !it.isSavings }.sumOf { it.amount }
        val reportSavings = reportExpenses.filter { it.isSavings }.sumOf { it.amount }
        val otherBudgetsSum = categories
            .filter { !it.isIncome && !it.isSavings && it.id != dialogCat.id }
            .sumOf { it.monthlyBudget ?: 0.0 }
        val reportRemainingBalance = (reportIncome - reportSavings) - otherBudgetsSum
        SetBudgetDialog(
            category = dialogCat,
            initialIsRecurring = isRecInitial,
            initialIsSettled = isSettledInitial,
            availableBalance = reportRemainingBalance,
            onDismiss = { showSetBudgetDialogForCategory = null },
            onSave = { newBudget, isRecurring, isSettled ->
                viewModel.updateCategoryForMonth(
                    category = dialogCat.copy(monthlyBudget = newBudget),
                    month = reportMonth,
                    year = reportYear,
                    isRecurring = isRecurring,
                    isSettled = isSettled
                )
                showSetBudgetDialogForCategory = null
            }
        )
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val filteredExpenses = remember(reportExpenses, searchQuery, categoriesMap) {
        if (searchQuery.isBlank()) {
            reportExpenses
        } else {
            reportExpenses.filter { exp ->
                val categoryName = categoriesMap[exp.categoryId]?.name ?: ""
                exp.description.contains(searchQuery, ignoreCase = true) ||
                        categoryName.contains(searchQuery, ignoreCase = true) ||
                        exp.amount.toString().contains(searchQuery)
            }
        }
    }

    val displayTotalSpent = remember(filteredExpenses) {
        filteredExpenses.filter { !it.isIncome && !it.isSavings }.sumOf { it.amount }
    }

    val anyExpenses = remember(filteredExpenses) {
        filteredExpenses.any { !it.isIncome && !it.isSavings }
    }

    val reportSpentByCategory = remember(filteredExpenses) {
        filteredExpenses.filter { !it.isIncome && !it.isSavings }
                      .groupBy { it.categoryId }
                      .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    // Calculate month name
    val monthName = remember(reportMonth) {
        val shortMonths = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths
        shortMonths[reportMonth - 1]
    }

    // Spend grouping by Category for Donut Chart
    val categorySpendList = remember(filteredExpenses, categoriesMap, categoriesTrigger) {
        val groups = filteredExpenses.filter { !it.isIncome && !it.isSavings }.groupBy { it.categoryId }
        groups.map { (catId, list) ->
            val cat = categoriesMap[catId]
            val total = list.sumOf { it.amount }
            CategorySpend(
                categoryId = catId,
                categoryName = cat?.name ?: "Other",
                colorHex = cat?.colorHex ?: "#6B7280",
                amount = total
            )
        }.sortedByDescending { it.amount }
    }

    // Chronological grouping
    val groupedExpenses = remember(filteredExpenses) {
        filteredExpenses.groupBy { getGroupHeader(it.date) }
    }

    fun formatCurrency(amount: Double): String {
        return "₹" + String.format(Locale.US, "%,.2f", amount)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top Main Header Row with Analytics title styled centrally, month selectors Left, search button Right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left: Month Selector (Dropdown Menu)
            var showMonthDropdown by remember { mutableStateOf(false) }
            val shortMonthsList = listOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            val currentMonthShortLabel = shortMonthsList.getOrNull(reportMonth - 1) ?: "Jan"
            val currentCalendar = remember { Calendar.getInstance() }
            val systemCurrentMonth = currentCalendar.get(Calendar.MONTH) + 1

            Box(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable { showMonthDropdown = true }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Month Calendar",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentMonthShortLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 0.1.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Month",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMonthDropdown,
                    onDismissRequest = { showMonthDropdown = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .heightIn(max = 240.dp)
                ) {
                    shortMonthsList.forEachIndexed { index, mName ->
                        val isSelected = reportMonth == index + 1
                        val isSystemCurrent = (index + 1) == systemCurrentMonth
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "$mName $reportYear",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    if (isSystemCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.setReportMonthAndYear(index + 1, reportYear)
                                showMonthDropdown = false
                            }
                        )
                    }
                }
            }

            // Center: Analytics Title
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.align(Alignment.Center)
            )

            // Right: Search Button in custom rounded box with stroke
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) {
                            searchQuery = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Toggle search",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Search Field with smooth reveal
        AnimatedVisibility(
            visible = isSearchExpanded,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search monthly categories...",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "SearchIcon",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                    focusedBorderColor = if (isDark) Color.White else Color.Black,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // REPORT SECTION CONTENT (Pie Chart + Category Breakdown)
            item {
                AnimatedVisibility(
                    visible = anyExpenses,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val totalBudget = budgetedCategories.sumOf { it.monthlyBudget ?: 0.0 }
                        DonutChartWidget(
                            categorySpendList = categorySpendList,
                            totalSpent = displayTotalSpent,
                            totalBudget = totalBudget
                        )
                    }
                }

                if (!anyExpenses) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = "Empty report",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No activities for this month",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // Category Breakdown Legend
            if (anyExpenses) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        CategoryLegendWidget(
                            categorySpendList = categorySpendList,
                            totalSpent = displayTotalSpent,
                            categoriesMap = categoriesMap,
                            onSetBudgetClick = { showSetBudgetDialogForCategory = it },
                            isCategoryRecurring = { catId -> viewModel.isCategoryBudgetRecurringForMonth(catId, reportMonth, reportYear) },
                            isCategorySettled = { catId -> viewModel.isCategoryBudgetSettledForMonth(catId, reportMonth, reportYear) },
                            onToggleCategorySettled = { catId, isSettled -> viewModel.setCategoryBudgetSettledForMonth(catId, reportMonth, reportYear, isSettled) },
                            categoriesTrigger = categoriesTrigger
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportExpenseRow(
    expense: Expense,
    category: Category?
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.12f else 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val catColor = category?.colorHex?.let { CategoryIconHelper.parseColor(it) } ?: when {
                expense.isSavings -> Color(0xFF3B82F6)
                expense.isIncome -> Color(0xFF10B981)
                else -> Color(0xFF6B7280)
            }
            val iconVec = category?.iconName?.let { CategoryIconHelper.getIconForName(it) } ?: when {
                expense.isSavings -> androidx.compose.material.icons.Icons.Default.AccountBalanceWallet
                expense.isIncome -> androidx.compose.material.icons.Icons.Default.Payments
                else -> CategoryIconHelper.getIconForName("category")
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(catColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVec,
                    contentDescription = category?.name ?: "Category Icon",
                    tint = catColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Category Chip Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(catColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = category?.name ?: "Other",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = catColor,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            val amountLabel = when {
                expense.isSavings -> "₹" + String.format(Locale.getDefault(), "%,.0f", expense.amount)
                expense.isIncome -> "+₹" + String.format(Locale.getDefault(), "%,.0f", expense.amount)
                else -> "-₹" + String.format(Locale.getDefault(), "%,.0f", expense.amount)
            }
            val amountColor = when {
                expense.isSavings -> Color(0xFF3B82F6)
                expense.isIncome -> Color(0xFF10B981)
                else -> Color(0xFFF43F5E)
            }

            Text(
                text = amountLabel,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = amountColor
                )
            )
        }
    }
}

@Composable
fun DonutChartWidget(
    categorySpendList: List<CategorySpend>,
    totalSpent: Double,
    totalBudget: Double
) {
    val animateSweep = remember { Animatable(0f) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    LaunchedEffect(categorySpendList) {
        animateSweep.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    Box(
        modifier = Modifier
            .size(230.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val labelColor = MaterialTheme.colorScheme.onBackground
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 20.dp.toPx()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = (size.width - strokeWidthPx) / 2

            // Draw clean background grey track
            drawCircle(
                color = labelColor.copy(alpha = 0.05f),
                radius = radius,
                style = Stroke(width = strokeWidthPx)
            )

            var startAngle = -90f
            for (spend in categorySpendList) {
                val percentage = if (totalSpent > 0.0) spend.amount / totalSpent else 0.0
                val sweepAngle = (percentage * 360f).toFloat() * animateSweep.value
                val color = CategoryIconHelper.parseColor(spend.colorHex)

                if (sweepAngle > 0.1f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        // Center Content Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            val budgetPercent = if (totalBudget > 0.0) (totalSpent / totalBudget) * 100 else 0.0
            
            // Percentage Pill (like the reference illustration percent pill inside the donut chart)
            if (budgetPercent > 0.0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${budgetPercent.roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = "You've Spent",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Large, bold total spent curreny display
            Text(
                text = "₹" + String.format(Locale.US, "%,.0f", totalSpent),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
            )

            if (totalBudget > 0.0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "of ₹" + String.format(Locale.US, "%,.0f", totalBudget),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun CategoryLegendWidget(
    categorySpendList: List<CategorySpend>,
    totalSpent: Double,
    categoriesMap: Map<Int, Category>,
    onSetBudgetClick: (Category) -> Unit,
    isCategoryRecurring: (Int) -> Boolean,
    isCategorySettled: (Int) -> Boolean,
    onToggleCategorySettled: (Int, Boolean) -> Unit,
    categoriesTrigger: Long = 0L
) {
    val (settledList, unsettledList) = remember(categorySpendList, categoriesMap, categoriesTrigger) {
        categorySpendList.partition { spend ->
            isCategorySettled(spend.categoryId)
        }
    }

    val (budgetedList, unbudgetedList) = remember(unsettledList, categoriesMap) {
        unsettledList.partition { spend ->
            val cat = categoriesMap[spend.categoryId]
            (cat?.monthlyBudget ?: 0.0) > 0.0
        }
    }

    val sortedBudgeted = remember(budgetedList, categoriesMap) {
        budgetedList.sortedByDescending { spend ->
            val budget = categoriesMap[spend.categoryId]?.monthlyBudget ?: 1.0
            spend.amount / budget
        }
    }

    val sortedUnbudgeted = remember(unbudgetedList) {
        unbudgetedList.sortedByDescending { it.amount }
    }

    val sortedSettled = remember(settledList) {
        settledList.sortedByDescending { it.amount }
    }

    fun formatCurrency(amount: Double): String {
        return "₹" + String.format(Locale.getDefault(), "%,.0f", amount)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. WITH BUDGET Section
        if (sortedBudgeted.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                     text = "WITH BUDGET",
                     style = MaterialTheme.typography.bodySmall.copy(
                         fontWeight = FontWeight.ExtraBold,
                         fontSize = 11.sp,
                         letterSpacing = 1.sp
                     ),
                     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                     modifier = Modifier.padding(bottom = 4.dp)
                )

                for (spend in sortedBudgeted) {
                    val category = categoriesMap[spend.categoryId] ?: continue
                    val iconColor = CategoryIconHelper.parseColor(spend.colorHex)
                    val catBudget = category.monthlyBudget ?: 0.0
                    val catPct = if (catBudget > 0.0) (spend.amount / catBudget).toFloat() else 0f
                    val isRecurring = isCategoryRecurring(category.id)
                    val isOverBudget = spend.amount > catBudget

                    val catColor = if (isRecurring) Color(0xFF3B82F6) else when {
                        catPct < 0.70f -> Color(0xFF10B981) // Green for good condition (<70%)
                        catPct < 1.00f -> Color(0xFFF59E0B) // Orange for about to exceed (70% - 99%)
                        else -> Color(0xFFEF4444)             // Red for reached/exceeded (>=100%)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSetBudgetClick(category)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = CategoryIconHelper.getIconForName(category.iconName),
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = spend.categoryName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (isRecurring) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Recurring",
                                            tint = Color(0xFF3B82F6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "${formatCurrency(spend.amount)} / ${formatCurrency(catBudget)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Progress Bar (Height 8.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(catPct.coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(catColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Remaining/Over label
                            val rem = kotlin.math.max(0.0, catBudget - spend.amount)
                            val remainingText = if (isOverBudget) {
                                "${formatCurrency(spend.amount - catBudget)} over budget"
                            } else {
                                "${formatCurrency(rem)} remaining"
                            }
                            Text(
                                text = remainingText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. NO BUDGET SET Section
        if (sortedUnbudgeted.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NO BUDGET SET",
                    style = MaterialTheme.typography.bodySmall.copy(
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 11.sp,
                      letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                for (spend in sortedUnbudgeted) {
                    val category = categoriesMap[spend.categoryId]
                    val iconColor = CategoryIconHelper.parseColor(spend.colorHex)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                category?.let {
                                    onSetBudgetClick(it)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = CategoryIconHelper.getIconForName(category?.iconName ?: "category"),
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = spend.categoryName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Text(
                                    text = formatCurrency(spend.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tracking only — no limit set",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                if (category != null) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .clickable {
                                                onSetBudgetClick(category)
                                            }
                                            .testTag("report_set_budget_btn_${category.id}")
                                    ) {
                                        Text(
                                            text = "+ Set Budget",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. SETTLED CATEGORY Section
        if (sortedSettled.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SETTLED CATEGORY",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                for (spend in sortedSettled) {
                    val category = categoriesMap[spend.categoryId] ?: continue
                    val iconColor = CategoryIconHelper.parseColor(spend.colorHex)
                    val catBudget = category.monthlyBudget ?: 0.0
                    val isRecurring = isCategoryRecurring(category.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSetBudgetClick(category)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = CategoryIconHelper.getIconForName(category.iconName),
                                        contentDescription = null,
                                        tint = iconColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = spend.categoryName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    if (isRecurring) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Recurring",
                                            tint = Color(0xFF3B82F6).copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${formatCurrency(spend.amount)} / ${formatCurrency(catBudget)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            fontSize = 13.sp
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            onToggleCategorySettled(category.id, false)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Settled (Click to Unsettle)",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarChartWidget(
    weekSpends: List<Double>
) {
    val animateVal = remember { Animatable(0f) }

    LaunchedEffect(weekSpends) {
        animateVal.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    val maxVal = remember(weekSpends) {
        val max = weekSpends.maxOrNull() ?: 1.0
        if (max == 0.0) 1.0 else max
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        weekSpends.forEachIndexed { index, spent ->
            val scaleFactor = (spent / maxVal).toFloat() * animateVal.value

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Label (Amount above the bar)
                Text(
                    text = "₹" + String.format(Locale.US, "%,.0f", spent),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // The animated column itself
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight(scaleFactor.coerceAtLeast(0.02f)) // ensure tiny line if is 0
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.primary) // Lime green accent bars
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Week Label
                Text(
                    text = "Week ${index + 1}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }
        }
    }
}

data class CategorySpend(
    val categoryId: Int,
    val categoryName: String,
    val colorHex: String,
    val amount: Double
)

private fun getGroupHeader(timestamp: Long): String {
    val dateCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCalendar = Calendar.getInstance()
    val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

    return when {
        isSameDay(dateCalendar, todayCalendar) -> "Today"
        isSameDay(dateCalendar, yesterdayCalendar) -> "Yesterday"
        else -> {
            val sdf = SimpleDateFormat("dd MMMM", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
