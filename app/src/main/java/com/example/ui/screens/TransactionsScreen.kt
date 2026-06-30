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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TrackerViewModel,
    onEditExpense: (Expense) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    val allExpenses by viewModel.reportExpenses.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val currencySymbol = remember(selectedCurrency) {
        viewModel.getCurrencySymbol()
    }
    val categories by viewModel.categories.collectAsState()
    val categoriesMap = remember(categories) { categories.associateBy { it.id } }

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: All, 1: Expenses, 2: Income, 3: Savings
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    // Filter transactions based on category tab & search query
    val filteredExpenses = remember(allExpenses, selectedTabIndex, searchQuery, categoriesMap) {
        var baseList = allExpenses.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })

        // Apply Tab Filter
        baseList = when (selectedTabIndex) {
            1 -> baseList.filter { !it.isIncome && !it.isSavings } // Expenses Section
            2 -> baseList.filter { it.isIncome && !it.isSavings }  // Income Section
            3 -> baseList.filter { it.isSavings }                  // Savings Section
            else -> baseList
        }

        // Apply Search Query
        if (searchQuery.isNotBlank()) {
            baseList = baseList.filter { exp ->
                val categoryName = categoriesMap[exp.categoryId]?.name ?: ""
                exp.description.contains(searchQuery, ignoreCase = true) ||
                        categoryName.contains(searchQuery, ignoreCase = true) ||
                        exp.amount.toString().contains(searchQuery)
            }
        }

        baseList
    }

    val groupedExpenses = remember(filteredExpenses) {
        filteredExpenses.groupBy { getGroupDateString(it.date) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Row: Centered screen title & Month Selector on Left, Circular search button Right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Left: Month Selector (Dropdown Menu)
                var showMonthDropdown by remember { mutableStateOf(false) }
                val reportMonth by viewModel.reportMonth.collectAsState()
                val reportYear by viewModel.reportYear.collectAsState()
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
                            .height(44.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                shape = CircleShape
                            )
                            .clickable { showMonthDropdown = true }
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

                // Centered Screen Title
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                // Right: Circular Search Button (matching home screen layout style)
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

            // Search input field with smooth expand overlay reveal
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
                            text = "Search transactions...",
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
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                )
            }

            // Custom modern circular pill capsule tab selectors inspired by mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isDark) Color(0xFF1E1E24) else Color(0xFFEAEBF0))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Expense", "Income", "Savings").forEachIndexed { index, label ->
                    val isSelected = selectedTabIndex == index
                    val activeBgColor = MaterialTheme.colorScheme.primary
                    val activeTextColor = MaterialTheme.colorScheme.onPrimary
                    val inactiveTextColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (isSelected) activeBgColor else Color.Transparent)
                            .clickable { selectedTabIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeTextColor else inactiveTextColor,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            // Transactions Listing LazyColumn
            if (groupedExpenses.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Icon(
                        imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.ReceiptLong,
                        contentDescription = "No receipts found",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No data found for \"$searchQuery\"" else "No transactions recorded in this context",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try searching different terms." else "Tap '+' below to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 180.dp)
                ) {
                    groupedExpenses.forEach { (dateHeader, expenses) ->
                        // Header item for the date
                        item(key = "header_$dateHeader") {
                            // Extract distinct Day (e.g. "Monday") and Date (e.g. "8 June 2026")
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
                                    .padding(top = 22.dp, bottom = 8.dp)
                            ) {
                                // Subtle colored indicator pill at the start to anchor the header section
                                Box(
                                    modifier = Modifier
                                        .size(width = 3.dp, height = 16.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Day Name (Today, Yesterday, Monday, etc.)
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Muted Bullet divider
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Exact Calendar Date (e.g., 8 June 2026)
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }

                        items(expenses, key = { it.id }) { expense ->
                            val category = categoriesMap[expense.categoryId]
                            TransactionScreenRowItem(
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
    }
}

@Composable
fun TransactionScreenRowItem(
    expense: Expense,
    category: Category?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    currencySymbol: String = "₹"
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    if (showOptionsDialog) {
        val typeLabel = when {
            expense.isSavings -> "Savings"
            expense.isIncome -> "Income"
            else -> "Expense"
        }
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            title = { Text("Transaction Options") },
            text = { Text("What action would you like to perform for this $typeLabel of ${CurrencyFormatter.formatPlain(currencySymbol, expense.amount)}: \"${expense.description}\"?") },
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

    // Determine transaction-specific color and icon
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

    val subTitleText = catName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showOptionsDialog = true }
            .padding(vertical = 12.dp)
            .testTag("expense_item_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Box with fully rounded CircleShape
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
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
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f)
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
