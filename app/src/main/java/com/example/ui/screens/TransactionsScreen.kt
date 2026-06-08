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
                        focusedBorderColor = if (isDark) Color.White else Color.Black,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Expense", "Income", "Savings").forEachIndexed { index, label ->
                    val isSelected = selectedTabIndex == index
                    
                    val contentColor = if (isSelected) {
                        if (isDark) Color.Black else Color.White
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(if (isDark) Color.White else Color(0xFF111827))
                                } else {
                                    Modifier
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                            RoundedCornerShape(24.dp)
                                        )
                                        .background(Color.Transparent)
                                }
                            )
                            .clickable { selectedTabIndex = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                color = contentColor,
                                letterSpacing = 0.1.sp
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
                        .padding(vertical = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "No receipts",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matches found for \"$searchQuery\"" else "No transactions recorded in this context",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
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
                    contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
                ) {
                    groupedExpenses.forEach { (dateHeader, expenses) ->
                        // Header item for the date
                        item(key = "header_$dateHeader") {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp, bottom = 6.dp)
                            )
                        }

                        items(expenses, key = { it.id }) { expense ->
                            val category = categoriesMap[expense.categoryId]
                            TransactionScreenRowItem(
                                expense = expense,
                                category = category,
                                onEdit = { onEditExpense(expense) },
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
    onDelete: () -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }

    if (showOptionsDialog) {
        val typeLabel = when {
            expense.isSavings -> "Savings"
            expense.isIncome -> "Income"
            else -> "Expense"
        }
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Transaction Options") },
            text = { Text("What action would you like to perform for this $typeLabel of ₹${String.format(Locale.US, "%,.0f", expense.amount)}: \"${expense.description}\"?") },
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
        val amountLabel = when {
            expense.isSavings -> "₹" + String.format(Locale.getDefault(), "%,.0f", expense.amount)
            expense.isIncome -> "+₹" + String.format(Locale.getDefault(), "%,.0f", expense.amount)
            else -> "-₹" + String.format(Locale.getDefault(), "%,.0f", expense.amount)
        }
        val amountColor = when {
            expense.isSavings -> Color(0xFF3B82F6)
            expense.isIncome -> Color(0xFF10B981)
            else -> Color(0xFFEF4444)
        }
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = amountColor
            )
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
