package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.example.data.model.Category
import com.example.data.model.Expense
import com.example.ui.CategoryIconHelper
import com.example.ui.TrackerViewModel
import com.example.ui.CurrencyFormatter
import com.example.ui.FinanceText
import com.example.ui.components.SetBudgetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TrackerViewModel,
    onSeeAllClicked: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigateToProfile: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onSetBudgetClick: () -> Unit = {},
    onCopyBudgetClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val userName by viewModel.userName.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val currencySymbol = remember(selectedCurrency) {
        viewModel.getCurrencySymbol()
    }
    val spentTodayAmount by viewModel.spentToday.collectAsState()
    val spentThisWeekAmount by viewModel.spentThisWeek.collectAsState()
    val recentExpenses by viewModel.expenses.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoriesTrigger by viewModel.categoriesTrigger.collectAsState()

    // Map categories for rapid lookup
    val categoriesMap = remember(categories) { categories.associateBy { it.id } }

    val homeMonth by viewModel.homeMonth.collectAsState()
    val homeYear by viewModel.homeYear.collectAsState()

    val homeMonthExpenses = remember(recentExpenses, homeMonth, homeYear) {
        val cal = java.util.Calendar.getInstance()
        recentExpenses.filter { exp ->
            cal.timeInMillis = exp.date
            val expMonth = cal.get(java.util.Calendar.MONTH) + 1
            val expYear = cal.get(java.util.Calendar.YEAR)
            expMonth == homeMonth && expYear == homeYear
        }
    }

    val spentThisMonthAmount = remember(homeMonthExpenses) {
        homeMonthExpenses.filter { !it.isIncome && !it.isSavings }.sumOf { it.amount }
    }

    val incomeThisMonthAmount = remember(homeMonthExpenses) {
        homeMonthExpenses.filter { it.isIncome && !it.isSavings }.sumOf { it.amount }
    }

    val savingsThisMonthAmount = remember(homeMonthExpenses) {
        homeMonthExpenses.filter { it.isSavings }.sumOf { it.amount }
    }

    val monthlySpentByCategory = remember(homeMonthExpenses) {
        homeMonthExpenses.filter { !it.isIncome && !it.isSavings }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val budgetedCategories = remember(categories) {
        categories.filter { !it.isIncome && !it.isSavings && (it.monthlyBudget ?: 0.0) > 0.0 }
    }

    val categoriesWithSpends = remember(monthlySpentByCategory, categories, homeMonth, homeYear, categoriesTrigger) {
        categories.filter { !it.isIncome && !it.isSavings }.map { cat ->
            val spent = monthlySpentByCategory[cat.id] ?: 0.0
            cat to spent
        }.filter { it.second > 0 }
            .filter { (cat, _) -> !viewModel.isCategoryBudgetSettledForMonth(cat.id, homeMonth, homeYear) }
    }

    val homeCategoriesRecurringMap = remember(categories, homeMonth, homeYear, categoriesTrigger) {
        categories.associate { cat ->
            cat.id to viewModel.isCategoryBudgetRecurringForMonth(cat.id, homeMonth, homeYear)
        }
    }

    val budgetedNonRecurring = remember(categoriesWithSpends, homeCategoriesRecurringMap) {
        categoriesWithSpends.filter { (cat, _) ->
            (cat.monthlyBudget ?: 0.0) > 0.0 && !(homeCategoriesRecurringMap[cat.id] ?: false)
        }.sortedByDescending { it.second / (it.first.monthlyBudget ?: 1.0) }
    }

    val budgetedRecurring = remember(categoriesWithSpends, homeCategoriesRecurringMap) {
        categoriesWithSpends.filter { (cat, _) ->
            (cat.monthlyBudget ?: 0.0) > 0.0 && (homeCategoriesRecurringMap[cat.id] ?: false)
        }.sortedByDescending { it.second / (it.first.monthlyBudget ?: 1.0) }
    }

    val noBudgetSpends = remember(categoriesWithSpends) {
        categoriesWithSpends.filter { (it.first.monthlyBudget ?: 0.0) <= 0.0 }
            .sortedByDescending { it.second }
    }

    val budgetedToShow = remember(budgetedNonRecurring) {
        budgetedNonRecurring.take(5)
    }

    val unbudgetedToShow = remember(budgetedToShow, noBudgetSpends) {
        val remainingSlots = maxOf(0, 5 - budgetedToShow.size)
        noBudgetSpends.take(remainingSlots)
    }

    val recurringToShow = remember(budgetedToShow, unbudgetedToShow, budgetedRecurring) {
        val remainingSlots = maxOf(0, 5 - budgetedToShow.size - unbudgetedToShow.size)
        budgetedRecurring.take(remainingSlots)
    }

    val totalNonRecurringCount = remember(budgetedNonRecurring, noBudgetSpends) {
        budgetedNonRecurring.size + noBudgetSpends.size
    }

    val showRecurring = remember(totalNonRecurringCount) {
        totalNonRecurringCount <= 5
    }

    val withBudgetSpends = budgetedNonRecurring

    // Pull to Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    var showSetBudgetDialogForCategory by remember { mutableStateOf<Category?>(null) }

    val dialogCat = showSetBudgetDialogForCategory
    if (dialogCat != null) {
        val isRecInitial = viewModel.isCategoryBudgetRecurringForMonth(dialogCat.id, homeMonth, homeYear)
        val isSettledInitial = viewModel.isCategoryBudgetSettledForMonth(dialogCat.id, homeMonth, homeYear)
        val otherBudgetsSum = categories
            .filter { !it.isIncome && !it.isSavings && it.id != dialogCat.id }
            .sumOf { it.monthlyBudget ?: 0.0 }
        val currentRemainingBalance = (incomeThisMonthAmount - savingsThisMonthAmount) - otherBudgetsSum
        SetBudgetDialog(
            category = dialogCat,
            initialIsRecurring = isRecInitial,
            initialIsSettled = isSettledInitial,
            availableBalance = currentRemainingBalance,
            currencySymbol = currencySymbol,
            onDismiss = { showSetBudgetDialogForCategory = null },
            onSave = { newBudget, isRecurring, isSettled ->
                viewModel.updateCategory(dialogCat.copy(monthlyBudget = newBudget), isRecurring, isSettled)
                showSetBudgetDialogForCategory = null
            }
        )
    }



    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val showFullDashboard = searchQuery.isBlank()

    val top10Transactions = remember(homeMonthExpenses, searchQuery, categoriesMap) {
        val filtered = if (searchQuery.isBlank()) {
            homeMonthExpenses
        } else {
            homeMonthExpenses.filter { exp ->
                val categoryName = categoriesMap[exp.categoryId]?.name ?: ""
                exp.description.contains(searchQuery, ignoreCase = true) ||
                        categoryName.contains(searchQuery, ignoreCase = true) ||
                        exp.amount.toString().contains(searchQuery)
            }
        }
        filtered.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }).take(10)
    }

    val greetingText = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val initials = remember(userName) {
        if (userName.isBlank()) "RH" else {
            val parts = userName.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                (parts[0].take(1) + parts[1].take(1)).uppercase()
            } else {
                userName.take(2).uppercase()
            }
        }
    }

    fun formatCurrency(amount: Double): String {
        return CurrencyFormatter.formatPlain(currencySymbol, amount)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Pull down refresh indicator simulator
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Outer scroll container (or lazy column)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 0.dp, bottom = 180.dp)
            ) {
                // Unified Modern Top Header Row
                item(key = "home_top_header") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left part: Elegant Greeting Section
                            Column {
                                Text(
                                    text = greetingText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(
                                                fontStyle = FontStyle.Italic,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append(userName.ifBlank { "Pranav" })
                                        }
                                        append(" 👋")
                                    },
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 25.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }

                            // Right part: Action Buttons Container
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Month Selector Pill Capsule Style
                                var isMonthMenuExpanded by remember { mutableStateOf(false) }
                                val shortMonthsList = listOf(
                                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                                )
                                val currentMonthShortLabel = shortMonthsList.getOrNull(homeMonth - 1) ?: "Jan"
                                val currentCalendar = remember { Calendar.getInstance() }
                                val systemCurrentMonth = currentCalendar.get(Calendar.MONTH) + 1

                                Box {
                                    Row(
                                        modifier = Modifier
                                            .height(44.dp)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(22.dp)
                                            )
                                            .clip(RoundedCornerShape(22.dp))
                                            .clickable {
                                                isMonthMenuExpanded = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                            .testTag("month_selector_pill"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "Select Month",
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = currentMonthShortLabel,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Dropdown Arrow",
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isMonthMenuExpanded,
                                        onDismissRequest = { isMonthMenuExpanded = false },
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                width = 1.0.dp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                        shortMonthsList.forEachIndexed { index, mName ->
                                            val isSelected = homeMonth == (index + 1)
                                            val isSystemCurrent = (index + 1) == systemCurrentMonth
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "$mName $homeYear",
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                            )
                                                        )
                                                        if (isSystemCurrent) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                                    .background(MaterialTheme.colorScheme.primary)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setHomeMonth(index + 1)
                                                    isMonthMenuExpanded = false
                                                },
                                                modifier = Modifier.testTag("month_item_${mName.lowercase()}")
                                            )
                                        }
                                    }
                                }

                                // Search Button in custom rounded box with stroke
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clip(androidx.compose.foundation.shape.CircleShape)
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
                        }

                        AnimatedVisibility(
                            visible = isSearchExpanded,
                            enter = fadeIn() + expandVertically() + slideInVertically(),
                            exit = fadeOut() + shrinkVertically() + slideOutVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    placeholder = {
                                        Text(
                                            text = "Search transactions or category...",
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
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        focusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                        focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                    )
                                )
                            }
                        }
                    }
                }

                // Balance Card
                if (showFullDashboard) item(key = "home_balance_card") {
    var isBalanceExpanded by remember { mutableStateOf(false) }
    var isBalanceAmountsVisible by remember { mutableStateOf(true) }
    val totalIncome = incomeThisMonthAmount
    val actualCal = remember { java.util.Calendar.getInstance() }
    val actualMonth = actualCal.get(java.util.Calendar.MONTH) + 1
    val actualYear = actualCal.get(java.util.Calendar.YEAR)
    val todaySpends = if (homeMonth == actualMonth && homeYear == actualYear) spentTodayAmount else 0.0
    val totalSavings = savingsThisMonthAmount
    val remainingBalance = totalIncome - totalSavings - spentThisMonthAmount

    val displaySpentThisMonth = if (isBalanceAmountsVisible) formatCurrency(spentThisMonthAmount) else "$currencySymbol •••••"
    val displayTodaySpends = if (isBalanceAmountsVisible) formatCurrency(todaySpends) else "$currencySymbol •••••"
    val displayTotalIncome = if (isBalanceAmountsVisible) formatCurrency(totalIncome) else "$currencySymbol •••••"
    val displayTotalSavings = if (isBalanceAmountsVisible) formatCurrency(totalSavings) else "$currencySymbol •••••"
    val displayRemainingBalance = if (isBalanceAmountsVisible) formatCurrency(remainingBalance) else "$currencySymbol •••••"

    val monthsListBack = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val currentMonthAbbr = monthsListBack.getOrNull(homeMonth - 1) ?: ""

    val isFutureMonth = (homeYear > actualYear) || (homeYear == actualYear && homeMonth > actualMonth)
    val isPromptDismissed = viewModel.isBudgetPromptDismissed(homeMonth, homeYear)
    
    // For testing purposes: show the copy prompt for any future month. 
    // Commented out the 3-day limitation below so you can test it easily right now.
    val showCopyPrompt = isFutureMonth && !isPromptDismissed
    /*
    val showCopyPrompt = remember(homeMonth, homeYear, actualMonth, actualYear) {
        if (!isFutureMonth) return@remember false
        val today = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, homeYear)
            set(java.util.Calendar.MONTH, homeMonth - 1)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val diffMillis = target.timeInMillis - today.timeInMillis
        val diffDays = diffMillis / (24 * 60 * 60 * 1000.0)
        diffDays <= 3.0
    } && !isPromptDismissed
    */

    if (showCopyPrompt) {
        val cardBgColor = if (isDark) Color(0xFF1E1F22) else Color(0xFFFFF8F6)
        val accentColor = if (isDark) Color(0xFFCE5A32) else Color(0xFFCE5A32)
        val cardBorderColor = if (isDark) Color(0xFFCE5A32).copy(alpha = 0.45f) else Color(0xFFCE5A32).copy(alpha = 0.35f)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("copy_budget_prompt_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBgColor
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.2.dp,
                color = cardBorderColor
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left side: Custom clipboard peach container icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = Color(0xFFFFEBE4), // Beautiful light peach background
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Icon",
                            tint = Color(0xFFCE5A32),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Middle/Right: Title, description, and action button
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Copy past budget details?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDark) Color.White else Color(0xFF1F2937),
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set up your month quicky by copying past month’s budget details",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF4B5563),
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onCopyBudgetClick,
                            shape = RoundedCornerShape(50.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("copy_budget_prompt_cta"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Copy budget",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                // Top Right: Simple elegant clear/close button
                IconButton(
                    onClick = {
                        viewModel.setBudgetPromptDismissed(homeMonth, homeYear, true)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .testTag("copy_budget_prompt_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Dismiss copy budget prompt",
                        tint = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF03020E), RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.22f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .drawWithCache {
                val w = size.width
                val h = size.height

                // Cache the radial gradients so they aren't re-allocated on every frame draw!
                val gradient1 = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF080645),
                        androidx.compose.ui.graphics.Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(w * 0.10f, -h * 0.10f),
                    radius = w * 1.40f
                )

                val gradient2 = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF120E8A).copy(alpha = 0.65f),
                        androidx.compose.ui.graphics.Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(w * 0.20f, -h * 0.05f),
                    radius = w * 0.95f
                )

                val gradient3 = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF2420F9).copy(alpha = 0.55f),
                        androidx.compose.ui.graphics.Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(w * 0.05f, -h * 0.02f),
                    radius = w * 0.70f
                )

                onDrawBehind {
                    if (w > 0f && h > 0f) {
                        clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                            drawCircle(
                                brush = gradient1,
                                radius = w * 1.40f,
                                center = androidx.compose.ui.geometry.Offset(w * 0.10f, -h * 0.10f)
                            )
                            drawCircle(
                                brush = gradient2,
                                radius = w * 0.95f,
                                center = androidx.compose.ui.geometry.Offset(w * 0.20f, -h * 0.05f)
                            )
                            drawCircle(
                                brush = gradient3,
                                radius = w * 0.70f,
                                center = androidx.compose.ui.geometry.Offset(w * 0.05f, -h * 0.02f)
                            )
                        }
                    }
                }
            }
    ) {

        // Content Column
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        ) {
                            append(currentMonthAbbr.uppercase(java.util.Locale.ROOT))
                        }
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        ) {
                            append(" MONTH SPENDS")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.6.sp,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Invisible spacer on the left to perfectly balance the eye button on the right
                    Spacer(modifier = Modifier.width(36.dp))

                    FinanceText(
                        currencySymbol = currencySymbol,
                        amount = spentThisMonthAmount,
                        baseFontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displayLarge.copy(
                            letterSpacing = (-0.03).em,
                            textAlign = TextAlign.Center
                        ),
                        color = Color.White,
                        isVisible = isBalanceAmountsVisible
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { isBalanceAmountsVisible = !isBalanceAmountsVisible },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("toggle_balance_visibility")
                    ) {
                        Icon(
                            imageVector = if (isBalanceAmountsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isBalanceAmountsVisible) "Hide amounts" else "Show amounts",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TODAY'S SPENDS",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FinanceText(
                            currencySymbol = currencySymbol,
                            amount = todaySpends,
                            baseFontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            isVisible = isBalanceAmountsVisible
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "INCOME",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FinanceText(
                            currencySymbol = currencySymbol,
                            amount = totalIncome,
                            baseFontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            isVisible = isBalanceAmountsVisible
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SAVINGS",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FinanceText(
                            currencySymbol = currencySymbol,
                            amount = totalSavings,
                            baseFontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            isVisible = isBalanceAmountsVisible
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isBalanceExpanded,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(initialAlpha = 0.3f),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A18))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0A18))
                            .padding(top = 18.dp, bottom = 18.dp, start = 24.dp, end = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val incomePct = if (totalIncome > 0.0) 1.0f else 0.0f
                        val savingsPct = if (totalIncome > 0.0) (totalSavings / totalIncome).toFloat().coerceIn(0f, 1f) else 0.0f
                        val spentPct = if (totalIncome > 0.0) (spentThisMonthAmount / totalIncome).toFloat().coerceIn(0f, 1f) else 0.0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Income",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.width(64.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(incomePct)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF10B981))
                                )
                            }
                            Text(
                                text = displayTotalIncome,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981),
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(90.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Savings",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.width(64.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(savingsPct)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF5B57FF))
                                )
                            }
                            Text(
                                text = displayTotalSavings,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF5B57FF),
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(90.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Spent",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.width(64.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(spentPct)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFEF4444))
                                )
                            }
                            Text(
                                text = displaySpentThisMonth,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444),
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(90.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = "Remaining Balance",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "After savings & expenses",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                            FinanceText(
                                currencySymbol = currencySymbol,
                                amount = remainingBalance,
                                baseFontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                isVisible = isBalanceAmountsVisible
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .clickable { isBalanceExpanded = !isBalanceExpanded }
                    .background(Color(0xFF0D0C17))
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isBalanceExpanded) "View Less" else "View Balance Breakdown",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp
                        )
                    )
                    Icon(
                        imageVector = if (isBalanceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Balance Breakdown",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

                // Budget Bar Card (Styled with standard-aligned cohesive Material 3 design)
                if (showFullDashboard) item(key = "home_budget_bar_card") {
                    val totalCategoriesBudget = remember(categories) {
                        categories.filter { !it.isIncome && !it.isSavings }
                            .sumOf { it.monthlyBudget ?: 0.0 }
                    }

                    if (totalCategoriesBudget == 0.0) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                            ),
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Categories Budget",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Setup budgets to limit spending.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                 if (!isDark) {
                                    OutlinedButton(
                                        onClick = onSetBudgetClick,
                                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary,
                                            containerColor = Color.Transparent
                                        ),
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("set_budget_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Set Budget",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = onSetBudgetClick,
                                        border = BorderStroke(1.2.dp, Color.White),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("set_budget_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Set Budget",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val budgetLimit = totalCategoriesBudget
                        val budgetPct = if (spentThisMonthAmount > 0) (spentThisMonthAmount / budgetLimit) else 0.0
                        val budgetPctFloat = budgetPct.coerceIn(0.0, 1.0).toFloat()
                        val budgetPctFormatted = (budgetPct * 100).toInt()

                        val conditionColor = when {
                            budgetPct < 0.70 -> Color(0xFF10B981) // Green for good condition (<70%)
                            budgetPct < 1.00 -> Color(0xFFF59E0B) // Orange for about to exceed (70% - 99%)
                            else -> Color(0xFFEF4444)             // Red for reached/exceeded (>=100%)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                            ),
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Categories Budget",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )

                                    IconButton(
                                        onClick = onSetBudgetClick,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit budget",
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Premium Rounded Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(budgetPctFloat)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(conditionColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Bottom labels section matching image
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Spent ",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            )
                                        )
                                        Text(
                                            text = formatCurrency(spentThisMonthAmount),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        )
                                        Text(
                                            text = " / " + formatCurrency(budgetLimit),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            )
                                        )
                                    }

                                    Text(
                                        text = "$budgetPctFormatted%",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = conditionColor
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic SPENDING BY CATEGORY list with progress bars
                if (showFullDashboard && (budgetedToShow.isNotEmpty() || unbudgetedToShow.isNotEmpty() || recurringToShow.isNotEmpty())) {
                    item(key = "home_spending_by_category") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 18.dp)
                            ) {
                                // Card Title Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Spending By Category",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .clickable { onNavigateToAnalytics() }
                                            .padding(start = 8.dp, top = 4.dp, end = 0.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "See all",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "See All",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 1. WITH BUDGET Section
                                if (budgetedToShow.isNotEmpty()) {
                                    budgetedToShow.forEachIndexed { index, (cat, amt) ->
                                        val catBudget = cat.monthlyBudget ?: 0.0
                                        val catPct = if (catBudget > 0.0) (amt / catBudget).toFloat() else 0f
                                         val isRecurring = homeCategoriesRecurringMap[cat.id] ?: false
                                        val isOverBudget = amt > catBudget
                                        
                                        val catColor = if (isRecurring) Color(0xFF3B82F6) else when {
                                            catPct < 0.70f -> Color(0xFF10B981) // Green for good condition (<70%)
                                            catPct < 1.00f -> Color(0xFFF59E0B) // Orange for about to exceed (70% - 99%)
                                            else -> Color(0xFFEF4444)             // Red for reached/exceeded (>=100%)
                                        }

                                        val iconColor = CategoryIconHelper.parseColor(cat.colorHex)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    
                                                    showSetBudgetDialogForCategory = cat
                                                }
                                                .padding(vertical = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    Icon(
                                                        imageVector = CategoryIconHelper.getIconForName(cat.iconName),
                                                        contentDescription = null,
                                                        tint = iconColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = cat.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                                    text = "${formatCurrency(amt)} / ${formatCurrency(catBudget)}",
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
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
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

                                            // Remaining Amount label below
                                            val rem = kotlin.math.max(0.0, catBudget - amt)
                                            val remainingText = if (isOverBudget) {
                                                "${formatCurrency(amt - catBudget)} over budget"
                                            } else {
                                                "${formatCurrency(rem)} remaining"
                                            }
                                            Text(
                                                text = remainingText,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    color = if (isOverBudget && !isRecurring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                                )
                                            )
                                        }

                                        if (index < budgetedToShow.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                                            )
                                        }
                                    }
                                }

                                if (budgetedToShow.isNotEmpty() && unbudgetedToShow.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // 2. NO BUDGET SET Section
                                if (unbudgetedToShow.isNotEmpty()) {
                                    Text(
                                        text = "NO BUDGET SET",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                    )

                                    unbudgetedToShow.forEachIndexed { index, (cat, amt) ->
                                        val iconColor = CategoryIconHelper.parseColor(cat.colorHex)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    
                                                    showSetBudgetDialogForCategory = cat
                                                }
                                                .padding(vertical = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = CategoryIconHelper.getIconForName(cat.iconName),
                                                        contentDescription = null,
                                                        tint = iconColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = cat.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }

                                                Text(
                                                    text = formatCurrency(amt),
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

                                                Surface(
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier
                                                        .clickable {
                                                            
                                                            showSetBudgetDialogForCategory = cat
                                                        }
                                                        .testTag("set_budget_btn_${cat.id}")
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

                                        if (index < unbudgetedToShow.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                                            )
                                        }
                                    }
                                }

                                // 3. RECURRING BUDGETS Section
                                if (recurringToShow.isNotEmpty()) {
                                    if (budgetedToShow.isNotEmpty() || unbudgetedToShow.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    Text(
                                        text = "RECURRING BUDGETS",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                    )

                                    recurringToShow.forEachIndexed { index, (cat, amt) ->
                                        val catBudget = cat.monthlyBudget ?: 0.0
                                        val catPct = if (catBudget > 0.0) (amt / catBudget).toFloat() else 0f
                                        val isOverBudget = amt > catBudget
                                        
                                        val catColor = Color(0xFF3B82F6) // Recurring color blue
                                        val iconColor = CategoryIconHelper.parseColor(cat.colorHex)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showSetBudgetDialogForCategory = cat
                                                }
                                                .padding(vertical = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    Icon(
                                                        imageVector = CategoryIconHelper.getIconForName(cat.iconName),
                                                        contentDescription = null,
                                                        tint = iconColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = cat.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    
                                                    Icon(
                                                        imageVector = Icons.Default.Sync,
                                                        contentDescription = "Recurring",
                                                        tint = Color(0xFF3B82F6),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Text(
                                                    text = "${formatCurrency(amt)} / ${formatCurrency(catBudget)}",
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
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
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

                                            // Remaining Amount label below
                                            val rem = kotlin.math.max(0.0, catBudget - amt)
                                            val remainingText = if (isOverBudget) {
                                                "${formatCurrency(amt - catBudget)} over budget"
                                            } else {
                                                "${formatCurrency(rem)} remaining"
                                            }
                                            Text(
                                                text = remainingText,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                                )
                                            )
                                        }

                                        if (index < recurringToShow.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent Transactions section
                item(key = "home_recent_transactions") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)) else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Transactions",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )

                                Row(
                                    modifier = Modifier
                                        .clickable { onSeeAllClicked() }
                                        .padding(start = 8.dp, top = 4.dp, end = 0.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "See all",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "View All Transactions",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (top10Transactions.isEmpty()) {
                                // Empty State Illustration inside the card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.ReceiptLong,
                                        contentDescription = "No receipts found",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No data found for \"$searchQuery\"" else "No transactions recorded yet",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "Try searching different terms." else "Tap '+' below to add one.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    top10Transactions.forEachIndexed { index, expense ->
                                        val category = categoriesMap[expense.categoryId]
                                        ExpenseItemRow(
                                            expense = expense,
                                            category = category,
                                            currencySymbol = currencySymbol, onEdit = { onEditExpense(expense) },
                                            onDelete = {
                                                val deletedItem = expense
                                                viewModel.deleteExpense(expense)
                                                coroutineScope.launch {
                                                    val snackbarResult = snackbarHostState.showSnackbar(
                                                        message = "Deleted: ${expense.description}",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                                        viewModel.addExpense(
                                                            amount = deletedItem.amount,
                                                            description = deletedItem.description,
                                                            date = deletedItem.date,
                                                            categoryId = deletedItem.categoryId,
                                                            isIncome = deletedItem.isIncome
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                        if (index < top10Transactions.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
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

        // Sliding Coach Panel Overlay
        AnimatedVisibility(
            visible = false,
            enter = slideInVertically(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
            ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)),
            exit = slideOutVertically(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
            ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)),
            modifier = Modifier.fillMaxSize()
        ) {}
        /*
            // A dark/light responsive backdrop layer to isolate context
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            ) {
                var selectedCoachTab by remember { mutableStateOf("report") } // "report" or "chat"
                val coachLoading by viewModel.coachLoading.collectAsState()
                val coachError by viewModel.coachError.collectAsState()
                val chatHistory by viewModel.coachChatHistory.collectAsState()
                val chatLoading by viewModel.chatLoading.collectAsState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Header Area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                coroutineScope.launch {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    delay(80)
                                    viewModel.setCoachPanelOpen(false) 
                                    viewModel.clearCoachError()
                                }
                            },
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hive AI Coach",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Powered by Gemini 3.5 Flash",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }

                        // Sparkles action or clear chat button
                        if (selectedCoachTab == "chat" && chatHistory.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearCoachChat() }
                            ) {
                                Text(
                                    "Clear Chat",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                        thickness = 1.dp
                    )

                    // Sub-tab Pill Switcher (Smart Report vs Ask Coach Chat)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (isDark) Color(0xFF1E1E24) else Color(0xFFEAEBF0))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("report" to "📊 Proactive Report", "chat" to "💬 Consult Coach").forEach { (tabKey, labelName) ->
                            val isSelected = selectedCoachTab == tabKey
                            val activeBgColor = MaterialTheme.colorScheme.primary
                            val activeTextColor = MaterialTheme.colorScheme.onPrimary
                            val inactiveTextColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (isSelected) activeBgColor else Color.Transparent)
                                    .clickable { selectedCoachTab = tabKey },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = labelName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) activeTextColor else inactiveTextColor
                                    )
                                )
                            }
                        }
                    }

                    // Display errors if any
                    if (coachError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error icon",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = coachError ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Content Area for selected Tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (selectedCoachTab == "report") {
                            // Smart Report View
                            if (coachLoading) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Hive Coach is analyzing your categories...",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Synthesizing budgets and transaction velocities",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                    )
                                }
                            } else if (coachInsights == null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "No insights",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Generate Financial Insights",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Ask Hive AI to scan your active transactions and budgets to check your saving projection and assign a financial health score.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.generateInsights() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Analytics,
                                            contentDescription = "Generate"
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Analyze & Coach Me", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                     }
                                 }
                             } else {
                                 val insights = coachInsights
                                 if (insights != null) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                                ) {
                                    // Health Score Statement Card (Unified with Score progress ring, status pill, summary text and real monthly metrics)
                                    item {
                                        val ratingLabel = when {
                                            insights.healthScore >= 80 -> "Excellent"
                                            insights.healthScore >= 50 -> "Stable"
                                            else -> "Needs Attention"
                                        }
                                        val ratingBg = when {
                                            insights.healthScore >= 80 -> Color(0xFF10B981).copy(alpha = 0.12f)
                                            insights.healthScore >= 50 -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                                            else -> Color(0xFFEF4444).copy(alpha = 0.12f)
                                        }
                                        val ratingColor = when {
                                            insights.healthScore >= 80 -> Color(0xFF10B981)
                                            insights.healthScore >= 50 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 16.dp),
                                            shape = RoundedCornerShape(24.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(20.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                    // Circular Score Ring Layout
                                                     Box(
                                                         contentAlignment = Alignment.Center,
                                                         modifier = Modifier.size(90.dp)
                                                     ) {
                                                         CircularProgressIndicator(
                                                             progress = 1f,
                                                             color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                                             strokeWidth = 8.dp,
                                                             modifier = Modifier.fillMaxSize()
                                                         )
                                                         CircularProgressIndicator(
                                                             progress = (insights.healthScore.toFloat() / 100f).coerceIn(0f, 1f),
                                                             color = ratingColor,
                                                             strokeWidth = 8.dp,
                                                             modifier = Modifier.fillMaxSize()
                                                         )
                                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                             Text(
                                                                 text = "${insights.healthScore}",
                                                                 style = MaterialTheme.typography.titleLarge.copy(
                                                                     fontWeight = FontWeight.Black, 
                                                                     fontSize = 26.sp,
                                                                     letterSpacing = (-1).sp
                                                                 ),
                                                                 color = MaterialTheme.colorScheme.onSurface
                                                             )
                                                             Text(
                                                                 text = "HEALTH",
                                                                 style = MaterialTheme.typography.labelSmall.copy(
                                                                     fontSize = 8.sp, 
                                                                     fontWeight = FontWeight.Bold,
                                                                     letterSpacing = 0.5.sp
                                                                 ),
                                                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                             )
                                                         }
                                                     }

                                                     Spacer(modifier = Modifier.width(16.dp))

                                                     Column(modifier = Modifier.weight(1f)) {
                                                         Row(
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             Text(
                                                                 text = "BUDGET HEALTH SCORE",
                                                                 style = MaterialTheme.typography.labelSmall.copy(
                                                                     fontWeight = FontWeight.Black, 
                                                                     letterSpacing = 0.8.sp,
                                                                     fontSize = 9.sp
                                                                 ),
                                                                 color = MaterialTheme.colorScheme.primary
                                                             )
                                                             Spacer(modifier = Modifier.width(6.dp))
                                                             Surface(
                                                                 color = ratingBg,
                                                                 shape = RoundedCornerShape(6.dp)
                                                             ) {
                                                                 Text(
                                                                     text = ratingLabel.uppercase(),
                                                                     style = MaterialTheme.typography.labelSmall.copy(
                                                                         fontSize = 8.sp,
                                                                         fontWeight = FontWeight.ExtraBold,
                                                                         color = ratingColor,
                                                                         letterSpacing = 0.5.sp
                                                                     ),
                                                                     modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                 )
                                                             }
                                                         }
                                                         Spacer(modifier = Modifier.height(6.dp))
                                                         Text(
                                                             text = insights.summary,
                                                             style = MaterialTheme.typography.bodyMedium.copy(
                                                                 fontWeight = FontWeight.SemiBold, 
                                                                 fontSize = 13.sp,
                                                                 lineHeight = 18.sp
                                                             ),
                                                             color = MaterialTheme.colorScheme.onSurface
                                                         )
                                                     }
                                                 }

                                                 HorizontalDivider(
                                                     modifier = Modifier.padding(vertical = 16.dp),
                                                     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                                                 )

                                                 // Dynamic Monthly Metrics Statement Rows
                                                 Text(
                                                     text = "REAL-TIME STATEMENT SUMMARY",
                                                     style = MaterialTheme.typography.labelSmall.copy(
                                                         fontWeight = FontWeight.Black, 
                                                         letterSpacing = 0.8.sp,
                                                         fontSize = 8.5.sp
                                                     ),
                                                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                     modifier = Modifier.padding(bottom = 10.dp)
                                                 )

                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     // Income
                                                     Column(modifier = Modifier.weight(1f)) {
                                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                                             Box(
                                                                 modifier = Modifier
                                                                     .size(6.dp)
                                                                     .background(Color(0xFF10B981), androidx.compose.foundation.shape.CircleShape)
                                                             )
                                                             Spacer(modifier = Modifier.width(4.dp))
                                                             Text(
                                                                 text = "Income",
                                                                 style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                             )
                                                         }
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Text(
                                                             text = "$currencySymbol${"%,.0f".format(incomeThisMonthAmount)}",
                                                             style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                                             color = Color(0xFF10B981)
                                                         )
                                                     }

                                                     // Expenses
                                                     Column(modifier = Modifier.weight(1f)) {
                                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                                             Box(
                                                                 modifier = Modifier
                                                                     .size(6.dp)
                                                                     .background(Color(0xFFEF4444), androidx.compose.foundation.shape.CircleShape)
                                                             )
                                                             Spacer(modifier = Modifier.width(4.dp))
                                                             Text(
                                                                 text = "Expenses",
                                                                 style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                             )
                                                         }
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Text(
                                                             text = "$currencySymbol${"%,.0f".format(spentThisMonthAmount)}",
                                                             style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                                             color = Color(0xFFEF4444)
                                                         )
                                                     }

                                                     // Savings
                                                     val savingsAmount = incomeThisMonthAmount - spentThisMonthAmount
                                                     Column(modifier = Modifier.weight(1f)) {
                                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                                             Box(
                                                                 modifier = Modifier
                                                                     .size(6.dp)
                                                                     .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                                             )
                                                             Spacer(modifier = Modifier.width(4.dp))
                                                             Text(
                                                                 text = "Net Saved",
                                                                 style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                             )
                                                         }
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Text(
                                                             text = "$currencySymbol${"%,.0f".format(savingsAmount)}",
                                                             style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                                             color = if (savingsAmount >= 0) MaterialTheme.colorScheme.primary else Color(0xFFEF4444)
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                    }

                                    // Saving Projections Card (Outlook - Beautified with high-fidelity vertical accent bar)
                                    item {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 20.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                // Left visual accent pill bar
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(48.dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            shape = RoundedCornerShape(2.dp)
                                                        )
                                                )
                                                
                                                Spacer(modifier = Modifier.width(12.dp))
                                                
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Timeline,
                                                            contentDescription = "Forecast",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "END-OF-MONTH OUTLOOK",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Black, 
                                                                letterSpacing = 0.8.sp,
                                                                fontSize = 9.5.sp
                                                            ),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = insights.projections,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontSize = 12.5.sp, 
                                                            lineHeight = 17.sp,
                                                            fontWeight = FontWeight.Medium
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Tips Heading with explicit subtitle styling
                                    item {
                                        Column(modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)) {
                                            Text(
                                                text = "Core Recommendations",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 15.sp,
                                                    letterSpacing = (-0.2).sp
                                                ),
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Actionable financial steps curated by Hive Coach.",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                    }

                                    // Actionable Tips Individual blocks (Restructured into individual recommendation cards)
                                    items(insights.financialTips.size) { index ->
                                        val tip = insights.financialTips[index]
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                // High-contrast tip position badge
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                            shape = androidx.compose.foundation.shape.CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.width(12.dp))
                                                
                                                Text(
                                                    text = tip,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 12.sp, 
                                                        lineHeight = 17.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }

                                    item { Spacer(modifier = Modifier.height(12.dp)) }

                                    // Micro target Actionable Directives (Styled as high-focus mission card)
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                                            shape = androidx.compose.foundation.shape.CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AdsClick,
                                                        contentDescription = "Target icon",
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.width(14.dp))
                                                
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "TODAY'S TARGET DIRECTIVE",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Black, 
                                                            fontSize = 9.sp, 
                                                            letterSpacing = 0.8.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = insights.quickRecommendations,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold, 
                                                            fontSize = 12.sp,
                                                            lineHeight = 16.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Re-analyze button inside the list (Refactored refresh option)
                                    item {
                                        Spacer(modifier = Modifier.height(20.dp))
                                        TextButton(
                                            onClick = { viewModel.generateInsights() },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Refresh Real-time Analysis", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                            }
                        } else {
                            // Chat Coach View
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                                LaunchedEffect(chatHistory.size) {
                                    if (chatHistory.isNotEmpty()) {
                                        listState.animateScrollToItem(chatHistory.lastIndex)
                                    }
                                }

                                // Conversational Bubbles List
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                                ) {
                                    if (chatHistory.isEmpty()) {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 40.dp, bottom = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                                            shape = androidx.compose.foundation.shape.CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ChatBubbleOutline,
                                                        contentDescription = "Chat prompt",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Text(
                                                    text = "Consult Your Smart Budget Coach",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Ask situational questions about your finances, shopping, or request specialized savings structures.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        // Quick Prompts Options
                                        item {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "POPULAR COACH INQUIRIES",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            
                                            val suggestions = listOf(
                                                "How can I improve my savings rate this month?",
                                                "Can I afford to purchase a ${currencySymbol}5,000 treat?",
                                                "Estimate a weekly restaurant budget limit based on my spending.",
                                                "Give me a simple 3-step action list to optimize my account."
                                            )
                                            
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                suggestions.forEach { prompt ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { viewModel.askBudgetCoach(prompt) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.QuestionAnswer,
                                                                contentDescription = "Prompt suggestion",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Text(
                                                                text = prompt,
                                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        items(chatHistory) { (userMsg, coachResp) ->
                                            // User query bubble
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp),
                                                    modifier = Modifier.widthIn(max = 280.dp)
                                                ) {
                                                    Text(
                                                        text = userMsg,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, color = Color.White),
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }

                                            // Coach response bubble
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 2.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                                                    modifier = Modifier.widthIn(max = 280.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        // Affirmation label for buying
                                                        if (coachResp.affordableStatus != null) {
                                                            val pillBg = when (coachResp.affordableStatus) {
                                                                "YES" -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                                "CAUTION" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                                else -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                                            }
                                                            val pillText = when (coachResp.affordableStatus) {
                                                                "YES" -> Color(0xFF10B981)
                                                                "CAUTION" -> Color(0xFFD97706)
                                                                else -> Color(0xFFEF4444)
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(bottom = 6.dp)
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(pillBg)
                                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "AFFORDABLE: ${coachResp.affordableStatus}",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 9.sp, color = pillText)
                                                                )
                                                            }
                                                        }
                                                        
                                                        Text(
                                                            text = coachResp.answer,
                                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Interactive Spinner inside the conversation list
                                    if (chatLoading) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            "Coach is reviewing details...",
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Interactive Text Query Input Bar
                                var userQueryText by remember { mutableStateOf("") }
                                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = userQueryText,
                                        onValueChange = { userQueryText = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = {
                                            Text(
                                                "Ask: can I afford a ${currencySymbol}1500 dinner?",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        enabled = userQueryText.isNotBlank() && !chatLoading,
                                        onClick = {
                                            val queryToSend = userQueryText.trim()
                                            userQueryText = ""
                                            viewModel.askBudgetCoach(queryToSend)
                                        },
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                if (userQueryText.isNotBlank() && !chatLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send advice query",
                                            tint = if (userQueryText.isNotBlank() && !chatLoading) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        */
    }
}

@Composable
fun ExpenseItemRow(
    expense: Expense,
    category: Category?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    currencySymbol: String = "₹"
) {
    var showDialog by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    if (showDialog) {
        val typeLabel = when {
            expense.isSavings -> "Savings"
            expense.isIncome -> "Income"
            else -> "Expense"
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            title = { Text("Transaction Options") },
            text = { Text("What action would you like to perform for this $typeLabel of ${CurrencyFormatter.formatPlain(currencySymbol, expense.amount)}: \"${expense.description}\"?") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onEdit()
                            showDialog = false
                        }
                    ) {
                        Text("Edit", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onDelete()
                            showDialog = false
                        }
                    ) {
                        Text("Delete", color = Color(0xFFEF4444))
                    }
                }
            }
        )
    }

    // Determine transaction-specific color and icon (Emerald green and Payments for income; Blue and Wallet for savings; parsed values for expense)
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

    // Relative date helper
    val relativeDateStr = remember(expense.date) {
        val calItem = java.util.Calendar.getInstance().apply { timeInMillis = expense.date }
        val calToday = java.util.Calendar.getInstance()
        val calYesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -1) }

        val isSameDay = { c1: java.util.Calendar, c2: java.util.Calendar ->
            c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
            c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
        }

        when {
            isSameDay(calItem, calToday) -> "Today"
            isSameDay(calItem, calYesterday) -> "Yesterday"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(expense.date))
        }
    }

    val catName = category?.name ?: when {
        expense.isSavings -> "Savings"
        expense.isIncome -> "Income"
        else -> "Other"
    }

    val subTitleText = "$catName • $relativeDateStr"

    Row(
         modifier = Modifier
             .fillMaxWidth()
             .clip(RoundedCornerShape(12.dp))
             .clickable { showDialog = true }
             .padding(vertical = 12.dp)
             .testTag("expense_item_card"),
         verticalAlignment = Alignment.CenterVertically
     ) {
        // Icon Box with fully rounded CircleShape (no stroke, as requested!)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(catColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVec,
                contentDescription = catName,
                tint = catColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Description and Details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = expense.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subTitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4e-1f.coerceAtLeast(0.40f)) // Soft gray
            )
        }

        // Amount on the right side
        val prefix = when {
            expense.isSavings -> ""
            expense.isIncome -> "+"
            else -> "-"
        }
        val amountColor = when {
            expense.isSavings -> Color(0xFF3B82F6)
            expense.isIncome -> Color(0xFF10B981)
            else -> Color(0xFFEF4444)
        }
        FinanceText(
            currencySymbol = currencySymbol,
            amount = expense.amount,
            baseFontSize = 14.sp,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.bodyLarge,
            color = amountColor,
            prefix = prefix
        )
    }
}

