package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Expense
import com.example.ui.CategoryIconHelper
import com.example.ui.components.SetBudgetDialog
import com.example.ui.TrackerViewModel
import com.example.ui.CurrencyFormatter
import com.example.ui.FinanceText
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    viewModel: TrackerViewModel,
    categoryId: Int,
    onBack: () -> Unit,
    onEditExpense: (Expense) -> Unit
) {
    val allExpenses by viewModel.expenses.collectAsState()
    val reportMonth by viewModel.reportMonth.collectAsState()
    val reportYear by viewModel.reportYear.collectAsState()
    val categoriesTrigger by viewModel.categoriesTrigger.collectAsState()
    
    val categoriesFlow = remember(reportMonth, reportYear) {
        viewModel.getCategoriesForMonth(reportMonth, reportYear)
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    
    val category = remember(categories, categoryId, categoriesTrigger) {
        categories.find { it.id == categoryId }
    }
    
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val currencySymbol = remember(selectedCurrency) {
        viewModel.getCurrencySymbol()
    }
    
    var showSetBudgetDialog by remember { mutableStateOf(false) }

    // Sort states
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showSortDropdown by remember { mutableStateOf(false) }

    // Search states
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    // Filter expenses belonging to this category and the current report month & year
    val categoryExpenses = remember(allExpenses, categoryId, reportMonth, reportYear) {
        allExpenses.filter { exp ->
            exp.categoryId == categoryId && !exp.isIncome && !exp.isSavings &&
            Calendar.getInstance().apply { timeInMillis = exp.date }.let { cal ->
                (cal.get(Calendar.MONTH) + 1) == reportMonth && cal.get(Calendar.YEAR) == reportYear
            }
        }
    }

    val sortedExpenses = remember(categoryExpenses, sortOrder, searchQuery) {
        val sortedList = when (sortOrder) {
            SortOrder.DATE_DESC -> categoryExpenses.sortedByDescending { it.date }
            SortOrder.AMOUNT_DESC -> categoryExpenses.sortedByDescending { it.amount }
            SortOrder.AMOUNT_ASC -> categoryExpenses.sortedBy { it.amount }
        }
        if (searchQuery.isNotBlank()) {
            sortedList.filter { exp ->
                exp.description.contains(searchQuery, ignoreCase = true) ||
                exp.amount.toString().contains(searchQuery)
            }
        } else {
            sortedList
        }
    }

    val groupedExpenses = remember(sortedExpenses) {
        sortedExpenses.groupBy { getGroupDateString(it.date) }
    }

    val totalSpent = remember(categoryExpenses) {
        categoryExpenses.sumOf { it.amount }
    }

    val monthName = remember(reportMonth) {
        val shortMonths = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths
        shortMonths[reportMonth - 1]
    }

    val iconColor = remember(category) {
        category?.colorHex?.let { CategoryIconHelper.parseColor(it) } ?: Color.Gray
    }

    fun formatCurrency(amount: Double): String {
        return CurrencyFormatter.formatPlain(currencySymbol, amount)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = category?.name?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Category Detail",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                shape = CircleShape
                            )
                            .testTag("category_detail_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    if (category != null) {
                        // Edit Budget Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { showSetBudgetDialog = true }
                                .testTag("category_detail_top_edit_budget_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Budget",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Search Toggle Button
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(44.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { 
                                    showSearchBar = !showSearchBar 
                                    if (!showSearchBar) {
                                        searchQuery = ""
                                    }
                                }
                                .testTag("category_detail_top_search_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search Transactions",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (category == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Category not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                val catBudget = category.monthlyBudget ?: 0.0
                val hasBudget = catBudget > 0.0
                val progress = if (hasBudget) (totalSpent / catBudget).toFloat() else 0f
                val isOverBudget = totalSpent > catBudget

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    AnimatedVisibility(
                        visible = showSearchBar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "Search transactions...",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "SearchIcon",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
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
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                            )
                        )
                    }
                    // Budget Overview Card (cleaned with progress levels color code and no budget headers)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Spent",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FinanceText(
                                        currencySymbol = currencySymbol,
                                        amount = totalSpent,
                                        baseFontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = if (hasBudget && isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Budget Limit",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (hasBudget) {
                                        FinanceText(
                                            currencySymbol = currencySymbol,
                                            amount = catBudget,
                                            baseFontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        Text(
                                            text = "None",
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            if (hasBudget) {
                                 Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val barColor = when {
                                        progress < 0.70f -> Color(0xFF10B981) // Safe -> Green
                                        progress < 1.00f -> Color(0xFFF59E0B) // Verge of end -> Yellow/Orange
                                        else -> Color(0xFFEF4444)             // Completed -> Red
                                    }

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
                                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(barColor)
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val pctText = "${(progress * 100).toInt()}%"
                                        val remainingText = if (isOverBudget) {
                                            "${formatCurrency(totalSpent - catBudget)} Over limit"
                                        } else {
                                            "${formatCurrency(catBudget - totalSpent)} Available"
                                        }
                                        
                                        Text(
                                            text = remainingText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = pctText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = when {
                                                progress < 0.70f -> Color(0xFF10B981)
                                                progress < 1.00f -> Color(0xFFF59E0B)
                                                else -> Color(0xFFEF4444)
                                            }
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.05f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No budget limit defined. Keeping track of plain expenses.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Transactions section header with Sorting controllers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ALL TRANSACTIONS",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )

                        Box {
                             IconButton(
                                onClick = { showSortDropdown = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("transactions_sort_icon_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Transactions",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                listOf(
                                    SortOrder.DATE_DESC to "Latest",
                                    SortOrder.AMOUNT_DESC to "High to Low",
                                    SortOrder.AMOUNT_ASC to "Low to High"
                                ).forEach { (order, label) ->
                                    val isSelected = sortOrder == order
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            sortOrder = order
                                            showSortDropdown = false
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Transactions List
                    if (sortedExpenses.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 40.dp, start = 32.dp, end = 32.dp, bottom = 32.dp)
                            ) {
                                Icon(
                                    imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.ReceiptLong,
                                    contentDescription = "No receipts found",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No data found for \"$searchQuery\"" else "No transactions this month",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Try searching different terms." else "Any non-income / non-savings item logged in this category for $monthName will display here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            groupedExpenses.forEach { (dateHeader, expenses) ->
                                item(key = "header_$dateHeader") {
                                    val parts = dateHeader.split(", ")
                                    val (dayLabel, dateLabel) = if (parts.size == 2) {
                                        parts[0] to parts[1]
                                    } else {
                                        val firstExpenseDate = expenses.firstOrNull()?.date ?: System.currentTimeMillis()
                                        val formattedDate = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(firstExpenseDate))
                                        dateHeader to formattedDate
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, bottom = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 3.dp, height = 14.dp)
                                                .background(
                                                    color = iconColor,
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = dayLabel,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = dateLabel,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 11.sp,
                                                letterSpacing = 0.2.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                items(expenses, key = { it.id }) { expense ->
                                    CategoryTransactionRowItem(
                                        expense = expense,
                                        category = category,
                                        currencySymbol = currencySymbol,
                                        onEdit = { onEditExpense(expense) },
                                        onDelete = {
                                            viewModel.deleteExpense(expense)
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 50.dp),
                                        thickness = 0.8.dp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showSetBudgetDialog) {
                    val isRecInitial = viewModel.isCategoryBudgetRecurringForMonth(category.id, reportMonth, reportYear)
                    val isSettledInitial = viewModel.isCategoryBudgetSettledForMonth(category.id, reportMonth, reportYear)
                    
                    // Simple balance check
                    val otherBudgetsSum = categories
                        .filter { !it.isIncome && !it.isSavings && it.id != category.id }
                        .sumOf { it.monthlyBudget ?: 0.0 }
                    val reportMonthExpenses = allExpenses.filter { exp ->
                        val cal = Calendar.getInstance().apply { timeInMillis = exp.date }
                        val expMonth = cal.get(Calendar.MONTH) + 1
                        val expYear = cal.get(Calendar.YEAR)
                        expMonth == reportMonth && expYear == reportYear
                    }
                    val incomeThisMonthAmount = reportMonthExpenses.filter { it.isIncome && !it.isSavings }.sumOf { it.amount }
                    val savingsThisMonthAmount = reportMonthExpenses.filter { it.isSavings }.sumOf { it.amount }
                    val reportRemainingBalance = (incomeThisMonthAmount - savingsThisMonthAmount) - otherBudgetsSum

                    SetBudgetDialog(
                        category = category,
                        initialIsRecurring = isRecInitial,
                        initialIsSettled = isSettledInitial,
                        availableBalance = reportRemainingBalance,
                        currencySymbol = currencySymbol,
                        onDismiss = { showSetBudgetDialog = false },
                        onSave = { newBudget, isRecurring, isSettled ->
                            viewModel.updateCategoryForMonth(
                                category = category.copy(monthlyBudget = newBudget),
                                month = reportMonth,
                                year = reportYear,
                                isRecurring = isRecurring,
                                isSettled = isSettled
                            )
                            showSetBudgetDialog = false
                        }
                    )
                }
            }
        }
    }
}

enum class SortOrder {
    DATE_DESC,
    AMOUNT_DESC,
    AMOUNT_ASC
}

@Composable
fun CategoryTransactionRowItem(
    expense: Expense,
    category: Category?,
    currencySymbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            title = { Text("Transaction Options") },
            text = { Text("What action would you like to perform for this transaction of ${CurrencyFormatter.formatPlain(currencySymbol, expense.amount)}: \"${expense.description}\"?") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showOptionsDialog = false }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onEdit()
                            showOptionsDialog = false
                        }
                    ) {
                        Text("Edit", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onDelete()
                            showOptionsDialog = false
                        }
                    ) {
                        Text("Delete", color = Color(0xFFEF4444))
                    }
                }
            }
        )
    }

    val catColor = category?.colorHex?.let { CategoryIconHelper.parseColor(it) } ?: MaterialTheme.colorScheme.primary
    val iconVec = category?.iconName?.let { CategoryIconHelper.getIconForName(it) } ?: Icons.Default.ReceiptLong
    val catName = category?.name ?: "Expense"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showOptionsDialog = true }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(catColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVec,
                contentDescription = null,
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
                text = expense.description.ifBlank { "Untitled Expense" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = catName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f)
            )
        }

        // Amount on the right side
        FinanceText(
            currencySymbol = currencySymbol,
            amount = expense.amount,
            baseFontSize = 14.sp,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFEF4444),
            prefix = "-"
        )
    }
}

private fun getGroupDateString(timestamp: Long): String {
    val calItem = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val calToday = java.util.Calendar.getInstance()
    val calYesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -1) }

    val isSameDay = { c1: java.util.Calendar, c2: java.util.Calendar ->
        c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
        c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    return when {
        isSameDay(calItem, calToday) -> "Today"
        isSameDay(calItem, calYesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
