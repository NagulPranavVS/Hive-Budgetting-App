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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.ui.CurrencyFormatter
import com.example.ui.FinanceText
import com.example.ui.components.SetBudgetDialog
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    viewModel: TrackerViewModel,
    onCategoryClick: (Int) -> Unit = {}
) {
    val allExpenses by viewModel.expenses.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val currencySymbol = remember(selectedCurrency) {
        viewModel.getCurrencySymbol()
    }
    val reportMonth by viewModel.reportMonth.collectAsState()
    val reportYear by viewModel.reportYear.collectAsState()
    val categoriesTrigger by viewModel.categoriesTrigger.collectAsState()
    
    val categoriesFlow = remember(reportMonth, reportYear) {
        viewModel.getCategoriesForMonth(reportMonth, reportYear)
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val selectedAnalyticsTab by viewModel.selectedAnalyticsTab.collectAsState()
    val isTransactions = selectedAnalyticsTab == "transactions"

    var currentFilter by remember { mutableStateOf<ReportFilter>(ReportFilter.MonthAndYear) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var tempFilter by remember(showFilterBottomSheet) { mutableStateOf(currentFilter) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var customEndDate by remember { mutableStateOf(System.currentTimeMillis()) }

    var showSetBudgetDialogForCategory by remember { mutableStateOf<Category?>(null) }

    // List of months spanned by the filter
    val spannedMonths = remember(currentFilter, reportMonth, reportYear, allExpenses) {
        val list = mutableListOf<Pair<Int, Int>>()
        when (currentFilter) {
            is ReportFilter.MonthAndYear -> {
                list.add(Pair(reportMonth, reportYear))
            }
            is ReportFilter.Last3Months -> {
                for (i in 0..2) {
                    val tempCal = Calendar.getInstance()
                    tempCal.add(Calendar.MONTH, -i)
                    list.add(Pair(tempCal.get(Calendar.MONTH) + 1, tempCal.get(Calendar.YEAR)))
                }
            }
            is ReportFilter.Last6Months -> {
                for (i in 0..5) {
                    val tempCal = Calendar.getInstance()
                    tempCal.add(Calendar.MONTH, -i)
                    list.add(Pair(tempCal.get(Calendar.MONTH) + 1, tempCal.get(Calendar.YEAR)))
                }
            }
            is ReportFilter.AllTime -> {
                val monthsSet = mutableSetOf<Pair<Int, Int>>()
                allExpenses.forEach { exp ->
                    val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
                    monthsSet.add(Pair(calExp.get(Calendar.MONTH) + 1, calExp.get(Calendar.YEAR)))
                }
                if (monthsSet.isEmpty()) {
                    monthsSet.add(Pair(reportMonth, reportYear))
                }
                list.addAll(monthsSet)
            }
            is ReportFilter.CustomRange -> {
                val range = currentFilter as ReportFilter.CustomRange
                val startCal = Calendar.getInstance().apply { timeInMillis = range.startDate }
                val endCal = Calendar.getInstance().apply { timeInMillis = range.endDate }
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                while (!startCal.after(endCal)) {
                    list.add(Pair(startCal.get(Calendar.MONTH) + 1, startCal.get(Calendar.YEAR)))
                    startCal.add(Calendar.MONTH, 1)
                }
                if (list.isEmpty()) {
                    list.add(Pair(reportMonth, reportYear))
                }
            }
        }
        list
    }

    // Dynamic category mapping with summed budgets for multi-month spanned filters
    val categoriesWithBudgets = remember(categories, spannedMonths, categoriesTrigger) {
        categories.map { baseCat ->
            var totalBudget: Double? = null
            spannedMonths.forEach { (m, y) ->
                val b = viewModel.getCategoryBudgetForMonth(baseCat.id, m, y)
                if (b != null) {
                    totalBudget = (totalBudget ?: 0.0) + b
                }
            }
            baseCat.copy(monthlyBudget = totalBudget)
        }
    }

    val categoriesMap = remember(categoriesWithBudgets) {
        categoriesWithBudgets.associateBy { it.id }
    }

    val budgetedCategories = remember(categoriesWithBudgets) {
        categoriesWithBudgets.filter { !it.isIncome && !it.isSavings && (it.monthlyBudget ?: 0.0) > 0.0 }
    }

    val filteredIncome = remember(allExpenses, spannedMonths) {
        allExpenses.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            it.isIncome && !it.isSavings && (Pair(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)) in spannedMonths)
        }.sumOf { it.amount }
    }

    val filteredSavings = remember(allExpenses, spannedMonths) {
        allExpenses.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            it.isSavings && (Pair(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)) in spannedMonths)
        }.sumOf { it.amount }
    }

    val dialogCat = showSetBudgetDialogForCategory
    if (dialogCat != null) {
        val isRecInitial = viewModel.isCategoryBudgetRecurringForMonth(dialogCat.id, reportMonth, reportYear)
        val isSettledInitial = viewModel.isCategoryBudgetSettledForMonth(dialogCat.id, reportMonth, reportYear)
        
        val otherBudgetsSum = categoriesWithBudgets
            .filter { !it.isIncome && !it.isSavings && it.id != dialogCat.id }
            .sumOf { it.monthlyBudget ?: 0.0 }
        val reportRemainingBalance = (filteredIncome - filteredSavings) - otherBudgetsSum
        
        SetBudgetDialog(
            category = dialogCat,
            initialIsRecurring = isRecInitial,
            initialIsSettled = isSettledInitial,
            availableBalance = reportRemainingBalance,
            currencySymbol = currencySymbol,
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

    val filteredExpenses = remember(allExpenses, currentFilter, reportMonth, reportYear) {
        val cal = Calendar.getInstance()
        val nowTime = cal.timeInMillis
        when (currentFilter) {
            is ReportFilter.MonthAndYear -> {
                allExpenses.filter { exp ->
                    val calExp = Calendar.getInstance().apply { timeInMillis = exp.date }
                    (calExp.get(Calendar.MONTH) + 1) == reportMonth && calExp.get(Calendar.YEAR) == reportYear
                }
            }
            is ReportFilter.Last3Months -> {
                cal.add(Calendar.MONTH, -3)
                val startTime = cal.timeInMillis
                allExpenses.filter { it.date in startTime..nowTime }
            }
            is ReportFilter.Last6Months -> {
                cal.add(Calendar.MONTH, -6)
                val startTime = cal.timeInMillis
                allExpenses.filter { it.date in startTime..nowTime }
            }
            is ReportFilter.AllTime -> {
                allExpenses
            }
            is ReportFilter.CustomRange -> {
                val range = currentFilter as ReportFilter.CustomRange
                val startOfDay = Calendar.getInstance().apply {
                    timeInMillis = range.startDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val endOfDay = Calendar.getInstance().apply {
                    timeInMillis = range.endDate
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                allExpenses.filter { it.date in startOfDay..endOfDay }
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
        return CurrencyFormatter.formatPlain(currencySymbol, amount)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top Main Header Row with Analytics title styled centrally, month selectors/Period Left, filter button Right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left: Month / Date Range Selector
            var showMonthDropdown by remember { mutableStateOf(false) }
            val shortMonthsList = listOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            val currentMonthShortLabel = shortMonthsList.getOrNull(reportMonth - 1) ?: "Jan"
            val currentCalendar = remember { Calendar.getInstance() }
            val systemCurrentMonth = currentCalendar.get(Calendar.MONTH) + 1

            val formatDateSimple = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
            val currentPeriodLabel = when (currentFilter) {
                is ReportFilter.MonthAndYear -> currentMonthShortLabel
                is ReportFilter.Last3Months -> "Last 3M"
                is ReportFilter.Last6Months -> "Last 6M"
                is ReportFilter.AllTime -> "All Time"
                is ReportFilter.CustomRange -> {
                    val range = currentFilter as ReportFilter.CustomRange
                    "${formatDateSimple.format(Date(range.startDate))} - ${formatDateSimple.format(Date(range.endDate))}"
                }
            }

            Box(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .clickable {
                            showMonthDropdown = true
                        }
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Month Calendar",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentPeriodLabel,
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
                                currentFilter = ReportFilter.MonthAndYear
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

            // Right: Filter Button (replaces search button)
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
                        showFilterBottomSheet = true
                    }
                    .testTag("analytics_filter_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filter period",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
                if (currentFilter !is ReportFilter.MonthAndYear) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        val reportCategoriesRecurringMap = remember(categorySpendList, reportMonth, reportYear, categoriesTrigger) {
            categorySpendList.associate { spend ->
                spend.categoryId to viewModel.isCategoryBudgetRecurringForMonth(spend.categoryId, reportMonth, reportYear)
            }
        }
        val reportCategoriesSettledMap = remember(categorySpendList, reportMonth, reportYear, categoriesTrigger) {
            categorySpendList.associate { spend ->
                spend.categoryId to viewModel.isCategoryBudgetSettledForMonth(spend.categoryId, reportMonth, reportYear)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // REPORT SECTION CONTENT (Pie Chart + Category Breakdown)
            item {
                AnimatedVisibility(
                    visible = anyExpenses,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val totalBudget = budgetedCategories.sumOf { it.monthlyBudget ?: 0.0 }
                        val chartTotalAmount = if (totalBudget > 0.0) totalBudget else filteredIncome
                        
                        // Large, square-constrained donut chart that maximizes visual impact as a perfect full circle
                        DonutChartWidget(
                            categorySpendList = categorySpendList,
                            totalSpent = displayTotalSpent,
                            totalBudget = chartTotalAmount,
                            currencySymbol = currencySymbol,
                            modifier = Modifier
                                .size(240.dp)
                                .aspectRatio(1f)
                        )
                        
                        // Elegant spacing between chart and legend
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Categories Legend List with full proportional width to prevent text overflow
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categorySpendList.forEach { spend ->
                                val pct = if (displayTotalSpent > 0.0) (spend.amount / displayTotalSpent) * 100 else 0.0
                                val color = CategoryIconHelper.parseColor(spend.colorHex)
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Custom color dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${spend.categoryName} - ${pct.roundToInt()}%",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
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
                            currencySymbol = currencySymbol,
                            onCategoryClick = { categoryId -> onCategoryClick(categoryId) },
                            isCategoryRecurring = { catId -> reportCategoriesRecurringMap[catId] ?: false },
                            isCategorySettled = { catId -> reportCategoriesSettledMap[catId] ?: false },
                            onToggleCategorySettled = { catId, isSettled -> viewModel.setCategoryBudgetSettledForMonth(catId, reportMonth, reportYear, isSettled) },
                            categoriesTrigger = categoriesTrigger,
                            onSetBudgetClick = { category -> showSetBudgetDialogForCategory = category }
                        )
                    }
                }
            }
        }
    }

    if (showFilterBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showFilterBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Filter Analytics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val options = listOf(
                        Pair(ReportFilter.MonthAndYear, "This Month (${monthName} ${reportYear})"),
                        Pair(ReportFilter.Last3Months, "Last 3 Months"),
                        Pair(ReportFilter.Last6Months, "Last 6 Months"),
                        Pair(ReportFilter.AllTime, "All Time"),
                        Pair(ReportFilter.CustomRange(customStartDate, customEndDate), "Custom Date Range")
                    )
                    
                    options.forEach { (option, label) ->
                        val isSelected = when (option) {
                            ReportFilter.MonthAndYear -> tempFilter is ReportFilter.MonthAndYear
                            ReportFilter.Last3Months -> tempFilter is ReportFilter.Last3Months
                            ReportFilter.Last6Months -> tempFilter is ReportFilter.Last6Months
                            ReportFilter.AllTime -> tempFilter is ReportFilter.AllTime
                            is ReportFilter.CustomRange -> tempFilter is ReportFilter.CustomRange
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempFilter = if (option is ReportFilter.CustomRange) {
                                        ReportFilter.CustomRange(customStartDate, customEndDate)
                                    } else {
                                        option
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        tempFilter = if (option is ReportFilter.CustomRange) {
                                            ReportFilter.CustomRange(customStartDate, customEndDate)
                                        } else {
                                            option
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            if (option is ReportFilter.CustomRange && isSelected) {
                                val formatDateRange = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp, top = 2.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Start Date Field
                                    Surface(
                                        onClick = { showStartDatePicker = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Start Date",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = formatDateRange.format(Date(customStartDate)),
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = "Start Date",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
    
                                    // End Date Field
                                    Surface(
                                        onClick = { showEndDatePicker = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "End Date",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = formatDateRange.format(Date(customEndDate)),
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = "End Date",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            tempFilter = ReportFilter.MonthAndYear
                            currentFilter = ReportFilter.MonthAndYear
                            showFilterBottomSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Clear Filter",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Button(
                        onClick = {
                            currentFilter = tempFilter
                            showFilterBottomSheet = false
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Apply Filter",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customStartDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customStartDate = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    tempFilter = ReportFilter.CustomRange(customStartDate, customEndDate)
                    showStartDatePicker = false
                }) { Text("OK", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customEndDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customEndDate = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    tempFilter = ReportFilter.CustomRange(customStartDate, customEndDate)
                    showEndDatePicker = false
                }) { Text("OK", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

sealed class ReportFilter {
    object MonthAndYear : ReportFilter()
    object Last3Months : ReportFilter()
    object Last6Months : ReportFilter()
    object AllTime : ReportFilter()
    data class CustomRange(val startDate: Long, val endDate: Long) : ReportFilter()
}

@Composable
fun ReportExpenseRow(
    expense: Expense,
    category: Category?,
    currencySymbol: String = "₹"
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

            val prefix = when {
                expense.isSavings -> ""
                expense.isIncome -> "+"
                else -> "-"
            }
            val amountColor = when {
                expense.isSavings -> Color(0xFF3B82F6)
                expense.isIncome -> Color(0xFF10B981)
                else -> Color(0xFFF43F5E)
            }

            FinanceText(
                currencySymbol = currencySymbol,
                amount = expense.amount,
                baseFontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyLarge,
                color = amountColor,
                prefix = prefix
            )
        }
    }
}

@Composable
fun DonutChartWidget(
    categorySpendList: List<CategorySpend>,
    totalSpent: Double,
    totalBudget: Double,
    modifier: Modifier = Modifier,
    currencySymbol: String = "₹"
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
        modifier = modifier
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val labelColor = MaterialTheme.colorScheme.onBackground
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 18.dp.toPx()
            val diameter = kotlin.math.min(size.width, size.height)
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = (diameter - strokeWidthPx) / 2
            
            val topLeft = Offset(centerX - radius, centerY - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // Draw clean background grey track
            drawCircle(
                color = labelColor.copy(alpha = 0.05f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidthPx)
            )

            val activeSpends = categorySpendList.filter { it.amount > 0 }
            val N = activeSpends.size

            if (N == 1) {
                val spend = activeSpends[0]
                val color = CategoryIconHelper.parseColor(spend.colorHex)
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animateSweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                )
            } else if (N > 1) {
                val capExt = (strokeWidthPx / (2f * radius)) * (180f / kotlin.math.PI.toFloat())
                val targetGap = 5f // clean visual gap in degrees
                val minSweep = 3f // minimum visual sweep in degrees
                val minSegmentSpace = minSweep + 2f * capExt + targetGap

                // Check if total minimum space exceeds 340 degrees
                val totalMinSpaceNeeded = N * minSegmentSpace
                val (adjustedMinSweep, adjustedTargetGap) = if (totalMinSpaceNeeded > 340f) {
                    val factor = 340f / totalMinSpaceNeeded
                    Pair(minSweep * factor, targetGap * factor)
                } else {
                    Pair(minSweep, targetGap)
                }
                
                val finalMinSegmentSpace = adjustedMinSweep + 2f * capExt + adjustedTargetGap
                val remainingAngleToDistribute = 360f - (N * finalMinSegmentSpace)
                val totalActiveSpendsAmount = activeSpends.sumOf { it.amount }

                val allocatedAngles = activeSpends.map { spend ->
                    val proportion = if (totalActiveSpendsAmount > 0.0) spend.amount / totalActiveSpendsAmount else 0.0
                    val proportionalAngle = (proportion * remainingAngleToDistribute).toFloat()
                    finalMinSegmentSpace + proportionalAngle
                }

                var currentStartAngle = -90f
                for (i in activeSpends.indices) {
                    val spend = activeSpends[i]
                    val allocatedAngle = allocatedAngles[i] * animateSweep.value
                    val color = CategoryIconHelper.parseColor(spend.colorHex)

                    val drawSweepAngle = kotlin.math.max(0.1f, allocatedAngle - 2f * capExt - adjustedTargetGap)
                    val drawStartAngle = currentStartAngle + capExt + (adjustedTargetGap / 2f)

                    drawArc(
                        color = color,
                        startAngle = drawStartAngle,
                        sweepAngle = drawSweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    currentStartAngle += allocatedAngles[i]
                }
            }
        }

        // Center Content Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            val budgetPercent = if (totalBudget > 0.0) (totalSpent / totalBudget) * 100 else 0.0
            
            // Percentage Pill
            if (totalBudget > 0.0) {
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
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = "Spent",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Large, bold total spent display inside donut
            FinanceText(
                currencySymbol = currencySymbol,
                amount = totalSpent,
                baseFontSize = 22.sp,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = (-0.5).sp),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (totalBudget > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "of " + CurrencyFormatter.formatPlain(currencySymbol, totalBudget),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun BudgetProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) progress else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "BudgetProgress"
    )

    LaunchedEffect(progress) {
        animationPlayed = true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
fun CategoryLegendWidget(
    categorySpendList: List<CategorySpend>,
    totalSpent: Double,
    categoriesMap: Map<Int, Category>,
    onCategoryClick: (Int) -> Unit,
    isCategoryRecurring: (Int) -> Boolean,
    isCategorySettled: (Int) -> Boolean,
    onToggleCategorySettled: (Int, Boolean) -> Unit,
    categoriesTrigger: Long = 0L,
    currencySymbol: String = "₹",
    onSetBudgetClick: (Category) -> Unit = {}
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
        return CurrencyFormatter.formatPlain(currencySymbol, amount)
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

                    val catColor = iconColor

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCategoryClick(category.id)
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

                                val showPct = if (catBudget > 0.0) (spend.amount / catBudget) * 100 else 0.0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${showPct.roundToInt()}%",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = iconColor,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Navigate to category details",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Progress Bar (Height 8.dp)
                            BudgetProgressBar(
                                progress = catPct.coerceIn(0f, 1f),
                                color = catColor
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Remaining/Over details under the progress bar: remaining amount on the left, spent/budget fraction on the right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                        color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Normal
                                    )
                                )

                                Text(
                                    text = "${formatCurrency(spend.amount)} / ${formatCurrency(catBudget)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                )
                            }
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
                                    onCategoryClick(it.id)
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

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formatCurrency(spend.amount),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Navigate to category details",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                                onCategoryClick(category.id)
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
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Navigate to category details",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
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

@Composable
fun BarChartWidget(
    weekSpends: List<Double>,
    currencySymbol: String = "₹"
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
                    text = CurrencyFormatter.formatPlain(currencySymbol, spent),
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
