package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Category
import com.example.ui.CategoryIconHelper
import com.example.ui.TrackerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(
    viewModel: TrackerViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val categories by viewModel.categories.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val currencySymbol = remember(selectedCurrency) {
        viewModel.getCurrencySymbol()
    }
    val prefix = remember(currencySymbol) { "$currencySymbol " }
    val expenses by viewModel.expenses.collectAsState()
    val editingExpense by viewModel.editingExpense.collectAsState()
    val homeMonth by viewModel.homeMonth.collectAsState()
    val homeYear by viewModel.homeYear.collectAsState()

    val homeMonthExpenses = remember(expenses, homeMonth, homeYear) {
        val cal = java.util.Calendar.getInstance()
        expenses.filter { exp ->
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
    val availableBalance = remember(incomeThisMonthAmount, savingsThisMonthAmount, spentThisMonthAmount) {
        incomeThisMonthAmount - savingsThisMonthAmount - spentThisMonthAmount
    }
    val totalExpenseCatBudget = remember(categories) {
        categories.filter { !it.isIncome && !it.isSavings }.sumOf { it.monthlyBudget ?: 0.0 }
    }

    // Form states (supported types: "expense", "income", "savings")
    var transactionType by remember { mutableStateOf("expense") }
    val widgetRouteIsIncome by viewModel.widgetRouteIsIncome.collectAsState()
    LaunchedEffect(widgetRouteIsIncome) {
        widgetRouteIsIncome?.let { isIncome ->
            transactionType = if (isIncome) "income" else "expense"
            viewModel.widgetRouteIsIncome.value = null
        }
    }
    var amountValue by remember(prefix) {
        mutableStateOf(
            TextFieldValue(
                text = "${prefix}0",
                selection = TextRange(prefix.length)
            )
        )
    }
    var description by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }

    // Dialog controllers
    var showDatePicker by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }

    // Collect editingExpense state and pre-populate
    LaunchedEffect(editingExpense) {
        editingExpense?.let { exp ->
            transactionType = when {
                exp.isSavings -> "savings"
                exp.isIncome -> "income"
                else -> "expense"
            }
            val amtText = "$prefix${exp.amount.toInt()}"
            amountValue = TextFieldValue(
                text = amtText,
                selection = TextRange(amtText.length)
            )
            description = exp.description
            selectedDateMillis = exp.date
            selectedCategoryId = exp.categoryId
        }
    }

    // Auto-select first matching category of current selection type if none selected, or when selection type toggles
    LaunchedEffect(transactionType, categories) {
        if (editingExpense != null) return@LaunchedEffect // Skip auto-select in edit mode
        val filtered = categories.filter {
            when (transactionType) {
                "income" -> it.isIncome && !it.isSavings
                "savings" -> it.isSavings
                else -> !it.isIncome && !it.isSavings
            }
        }
        if (filtered.isNotEmpty()) {
            if (selectedCategoryId == null || filtered.none { it.id == selectedCategoryId }) {
                selectedCategoryId = filtered.first().id
            }
        } else {
            selectedCategoryId = null
        }
    }

    val dateLabel = remember(selectedDateMillis) {
        val sdf = SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault())
        sdf.format(Date(selectedDateMillis))
    }

    fun isFormValid(): Boolean {
        val cleanAmountStr = amountValue.text.substringAfter(prefix).trim()
        val amount = cleanAmountStr.toDoubleOrNull()
        return amount != null && amount > 0.0 && selectedCategoryId != null
    }

    fun saveExpense() {
        if (!isFormValid()) return

        val cleanAmountStr = amountValue.text.substringAfter(prefix).trim()
        val amount = cleanAmountStr.toDoubleOrNull() ?: 0.0
        val categoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Expense"
        val finalDescription = if (description.trim().isBlank()) categoryName else description.trim()

        val editing = editingExpense
        if (editing != null) {
            viewModel.updateExpense(
                editing.copy(
                    amount = amount,
                    description = finalDescription,
                    date = selectedDateMillis,
                    categoryId = selectedCategoryId!!,
                    isIncome = (transactionType == "income"),
                    isSavings = (transactionType == "savings")
                )
            )
            viewModel.clearEditingExpense()
            Toast.makeText(context, "Transaction updated successfully!", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addExpense(
                amount = amount,
                description = finalDescription,
                date = selectedDateMillis,
                categoryId = selectedCategoryId!!,
                isIncome = (transactionType == "income"),
                isSavings = (transactionType == "savings")
            )
            val typeLabel = when (transactionType) {
                "income" -> "Income"
                "savings" -> "Savings"
                else -> "Expense"
            }
            Toast.makeText(context, "$typeLabel saved successfully!", Toast.LENGTH_SHORT).show()
        }

        // Haptics & Feedback
        

        // Clear Form fields & resets focus
        amountValue = TextFieldValue(text = "${prefix}0", selection = TextRange(prefix.length))
        description = ""
        selectedDateMillis = System.currentTimeMillis()
        val defaultCat = categories.filter {
            when (transactionType) {
                "income" -> it.isIncome && !it.isSavings
                "savings" -> it.isSavings
                else -> !it.isIncome && !it.isSavings
            }
        }.firstOrNull()
        selectedCategoryId = defaultCat?.id
        focusManager.clearFocus()
        if (editing != null) {
            onBack?.invoke()
        }
    }

    androidx.activity.compose.BackHandler {
        viewModel.clearEditingExpense()
        onBack?.invoke()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Row with Back Button on top left
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(
                            onClick = { 
                                viewModel.clearEditingExpense()
                                onBack?.invoke() 
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = if (editingExpense != null) {
                                when (transactionType) {
                                    "income" -> "Edit Income"
                                    "savings" -> "Edit Savings"
                                    else -> "Edit Expense"
                                }
                            } else {
                                when (transactionType) {
                                    "income" -> "Add Income"
                                    "savings" -> "Add Savings"
                                    else -> "Add Expense"
                                }
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Type Selector (Segmented Control style)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Expense Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (transactionType == "expense") MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    transactionType = "expense"
                                    
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Expense",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (transactionType == "expense") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            )
                        }

                        // Income Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (transactionType == "income") MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    transactionType = "income"
                                    
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Income",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (transactionType == "income") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            )
                        }

                        // Savings Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (transactionType == "savings") MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    transactionType = "savings"
                                    
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Savings",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (transactionType == "savings") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }

                // Amount Input (Centered Hero)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextField(
                            value = amountValue,
                            onValueChange = { newValue ->
                                val cleanNewText = newValue.text

                                 // Ensure the prefix is ALWAYS kept at the start
                                if (!cleanNewText.startsWith(prefix)) {
                                    // If they backspaced into the prefix or completely cleared it
                                    // We restore "prefix + 0" with cursor after 0
                                    amountValue = TextFieldValue(text = "${prefix}0", selection = TextRange(prefix.length))
                                } else {
                                    val originalText = amountValue.text
                                    val numericPart = cleanNewText.substringAfter(prefix)
                                    
                                    // Handle replacement of default '0' with the first typed number
                                    if (originalText == "${prefix}0" && cleanNewText != "${prefix}0") {
                                        if (numericPart.length == 2) {
                                            val firstChar = numericPart[0]
                                            val secondChar = numericPart[1]
                                            if (firstChar == '0' && secondChar.isDigit()) {
                                                val formatted = "$prefix$secondChar"
                                                amountValue = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                                            } else if (secondChar == '0' && firstChar.isDigit()) {
                                                val formatted = "$prefix$firstChar"
                                                amountValue = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                                            } else {
                                                // Default logic fallback when numeric length is 2 but not a replacement scenario
                                                if (numericPart.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                    amountValue = newValue
                                                }
                                            }
                                        } else if (numericPart == ".") {
                                            // If they typed dot, keep "0."
                                            val formatted = "${prefix}0."
                                            amountValue = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                                        } else {
                                            // General fallback
                                            if (numericPart.isEmpty()) {
                                                amountValue = TextFieldValue(text = "${prefix}0", selection = TextRange(prefix.length))
                                            } else if (numericPart.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                amountValue = newValue
                                            }
                                        }
                                    } else {
                                        // Standard editing logic
                                        if (numericPart.isEmpty()) {
                                            amountValue = TextFieldValue(text = "${prefix}0", selection = TextRange(prefix.length))
                                        } else if (numericPart.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                            if (cleanNewText == "${prefix}0") {
                                                amountValue = newValue.copy(selection = TextRange(prefix.length))
                                            } else {
                                                amountValue = newValue
                                            }
                                        }
                                    }
                                }
                            },
                            textStyle = TextStyle(
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-1).sp
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("amount_input")
                        )
                    }
                }

                // Description Field
                item {
                    Column {
                        Text(
                            text = "Description (Optional)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = {
                                Text(
                                    when (transactionType) {
                                        "income" -> "What is the source of this income? (Optional)"
                                        "savings" -> "What are these savings for? (Optional)"
                                        else -> "What did you spend on? (Optional)"
                                    }
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("description_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                // Date Picker Field
                item {
                    Column {
                        Text(
                            text = "Date",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Choose Date",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Category Field
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Category",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(
                                onClick = { showManageCategoriesDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Manage Categories",
                                    tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Categories Horizontal Selector + Addition option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(90.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // List the categories
                                Box(modifier = Modifier.fillMaxSize()) {
                                    androidx.compose.foundation.lazy.LazyRow(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val filtered = categories.filter {
                                            when (transactionType) {
                                                "income" -> it.isIncome && !it.isSavings
                                                "savings" -> it.isSavings
                                                else -> !it.isIncome && !it.isSavings
                                            }
                                        }
                                        items(filtered) { cat ->
                                            val isSelected = selectedCategoryId == cat.id
                                            val activeColor = MaterialTheme.colorScheme.primary
                                            val borderColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.1f else 0.15f)
                                            val iconColor = if (isSelected) activeColor else (if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                            val bgColor = if (isSelected) activeColor.copy(alpha = if (isDark) 0.15f else 0.12f) else MaterialTheme.colorScheme.surface
                                            
                                            Box(
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .height(72.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(bgColor)
                                                    .border(
                                                        border = BorderStroke(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = borderColor
                                                        ),
                                                        shape = RoundedCornerShape(16.dp)
                                                    )
                                                    .clickable { selectedCategoryId = cat.id }
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = CategoryIconHelper.getIconForName(cat.iconName),
                                                        contentDescription = cat.name,
                                                        tint = iconColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = cat.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }

                                        // Inline "+ New Category" button
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .height(72.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(
                                                        BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                                                        RoundedCornerShape(16.dp)
                                                    )
                                                    .clickable { showNewCategoryDialog = true }
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    val textAccentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "New Category",
                                                        tint = textAccentColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "New Category",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textAccentColor
                                                        ),
                                                        maxLines = 1,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Subtle inline budget hint
                        val selectedCategory = categories.find { it.id == selectedCategoryId }
                        if (transactionType == "expense" && selectedCategory != null && selectedCategory.monthlyBudget != null) {
                            val budget = selectedCategory.monthlyBudget ?: 0.0
                            if (budget > 0.0) {
                                val currentCal = java.util.Calendar.getInstance()
                                val curMonth = currentCal.get(java.util.Calendar.MONTH) + 1
                                val curYear = currentCal.get(java.util.Calendar.YEAR)
                                
                                val categorySpent = remember(expenses, selectedCategoryId) {
                                    if (selectedCategoryId == null) 0.0
                                    else {
                                        val cal = java.util.Calendar.getInstance()
                                        expenses.filter { exp ->
                                            if (exp.isIncome) return@filter false
                                            cal.timeInMillis = exp.date
                                            val expMonth = cal.get(java.util.Calendar.MONTH) + 1
                                            val expYear = cal.get(java.util.Calendar.YEAR)
                                            exp.categoryId == selectedCategoryId && expMonth == curMonth && expYear == curYear
                                        }.sumOf { it.amount }
                                    }
                                }

                                val enteredAmount = try {
                                    val cleanStr = amountValue.text.substringAfter(prefix).trim()
                                    cleanStr.toDoubleOrNull() ?: 0.0
                                } catch (e: Exception) {
                                    0.0
                                }

                                val currentEditingExpense = editingExpense
                                val editingOffset = if (currentEditingExpense != null && currentEditingExpense.categoryId == selectedCategoryId) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.timeInMillis = currentEditingExpense.date
                                    val expMonth = cal.get(java.util.Calendar.MONTH) + 1
                                    val expYear = cal.get(java.util.Calendar.YEAR)
                                    if (expMonth == curMonth && expYear == curYear && !currentEditingExpense.isIncome && !currentEditingExpense.isSavings) {
                                        currentEditingExpense.amount
                                    } else 0.0
                                } else 0.0

                                val effectiveCategorySpent = (categorySpent - editingOffset + enteredAmount).coerceAtLeast(0.0)
                                val remainingBudget = budget - effectiveCategorySpent
                                val ratio = effectiveCategorySpent / budget
                                val progress = ratio.coerceIn(0.0, 1.0).toFloat()
                                val barColor = when {
                                    ratio > 1.0 -> MaterialTheme.colorScheme.error
                                    ratio >= 0.85 -> Color(0xFFF59E0B) // Amber
                                    else -> MaterialTheme.colorScheme.primary // Lime Accent
                                }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.03f))
                                        .border(
                                            BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Category Budget Hint",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val formattedRemaining = if (remainingBudget >= 0) {
                                            "$currencySymbol${String.format(java.util.Locale.US, "%,.0f", remainingBudget)}"
                                        } else {
                                            "-$currencySymbol${String.format(java.util.Locale.US, "%,.0f", -remainingBudget)}"
                                        }
                                        Text(
                                            text = "$formattedRemaining of $currencySymbol${String.format(java.util.Locale.US, "%,.0f", budget)} remaining",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = barColor,
                                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                                    )
                                    if (ratio > 1.0) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "⚠️ Over budget by $currencySymbol${String.format(java.util.Locale.US, "%,.0f", categorySpent - budget)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Save CTA at Bottom
            val isKeyboardVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (isKeyboardVisible) 0.dp else 12.dp,
                        bottom = if (isKeyboardVisible) 8.dp else 10.dp
                    )
            ) {
                Button(
                    onClick = { saveExpense() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_expense_button"),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = isFormValid()
                ) {
                    Text(
                        text = if (editingExpense != null) {
                            "Update Transaction"
                        } else {
                            when (transactionType) {
                                "income" -> "Save Income"
                                "savings" -> "Save Savings"
                                else -> "Save Expense"
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    )
                }
            }
        }

        // --- Standard M3 Date Picker Modal Dialog ---
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDateMillis
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                            showDatePicker = false
                        }
                    ) { Text("OK", fontWeight = FontWeight.Bold, color = if (isDark) Color.White else MaterialTheme.colorScheme.primary) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // --- Custom Inline Create Category Dialog ---
        if (showNewCategoryDialog) {
            var catName by remember { mutableStateOf("") }
            var catBudget by remember { mutableStateOf("") }
            var isRecurring by remember { mutableStateOf(false) }
            var selectedIcon by remember { mutableStateOf(CategoryIconHelper.availableIconsList.first()) }
            var selectedColorHex by remember { mutableStateOf(CategoryIconHelper.availableColorsList.first()) }

            AlertDialog(
                onDismissRequest = { showNewCategoryDialog = false },
                title = { Text("Add Category", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = catName,
                            onValueChange = { catName = it },
                            placeholder = { Text("Category Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        )

                        // Icon Selector Grids
                        Text("Pick Icon", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.height(48.dp)) {
                                androidx.compose.foundation.lazy.LazyRow {
                                    items(CategoryIconHelper.availableIconsList) { item ->
                                        val isSelected = selectedIcon == item
                                        val activeHighlightColor = MaterialTheme.colorScheme.primary
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) activeHighlightColor.copy(alpha = if (isDark) 0.25f else 0.12f)
                                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isSelected) activeHighlightColor else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedIcon = item },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = CategoryIconHelper.getIconForName(item),
                                                contentDescription = item,
                                                tint = if (isSelected) activeHighlightColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Colors palette selector
                        Text("Pick Color", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.height(40.dp)) {
                                androidx.compose.foundation.lazy.LazyRow {
                                    items(CategoryIconHelper.availableColorsList) { colorHex ->
                                        val isSelected = selectedColorHex == colorHex
                                        val c = CategoryIconHelper.parseColor(colorHex)
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedColorHex = colorHex }
                                        )
                                    }
                                }
                            }
                        }

                        // Monthly Budget section
                        if (transactionType == "expense") {
                            Column {
                                Text(
                                    text = "Monthly Budget (optional)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = catBudget,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() || it == '.' }) {
                                            catBudget = input
                                        }
                                    },
                                    placeholder = { Text("e.g. ${currencySymbol}3,000") },
                                    leadingIcon = { Text(currencySymbol, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                        focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                )
                                val enteredBudgetAmt = catBudget.trim().toDoubleOrNull() ?: 0.0
                                val availableBalanceForCreate = kotlin.math.round((incomeThisMonthAmount - savingsThisMonthAmount) - totalExpenseCatBudget)
                                val adjustedBalance = availableBalanceForCreate - enteredBudgetAmt
                                val formattedBalance = String.format(Locale.getDefault(), "%,.0f", adjustedBalance)
                                Text(
                                    text = "Remaining Balance: $currencySymbol$formattedBalance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (adjustedBalance >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "We'll alert you when you're close to this limit",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = "Recurring Expense",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Enable to automatically copy this budget to all future/upcoming months",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isRecurring,
                                        onCheckedChange = { isRecurring = it }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (catName.isNotBlank()) {
                                viewModel.addCategory(
                                    name = catName.trim(),
                                    iconName = selectedIcon,
                                    colorHex = selectedColorHex,
                                    monthlyBudget = if (transactionType == "expense" && (catBudget.toDoubleOrNull() ?: 0.0) > 0.0) catBudget.toDoubleOrNull() else null,
                                    isIncome = (transactionType == "income"),
                                    isSavings = (transactionType == "savings"),
                                    isRecurring = isRecurring
                                )
                                showNewCategoryDialog = false
                                Toast.makeText(context, "Added category \"$catName\"", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = catName.isNotBlank()
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewCategoryDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
            )
        }

        // --- Custom Manage Categories Dialog (Manage CRUD) ---
        if (showManageCategoriesDialog) {
            var showRenameDialogForCategory by remember { mutableStateOf<Category?>(null) }

            Dialog(
                onDismissRequest = { showManageCategoriesDialog = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (transactionType) {
                                    "income" -> "Manage Income Categories"
                                    "savings" -> "Manage Savings Categories"
                                    else -> "Manage Expense Categories"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            IconButton(onClick = { showManageCategoriesDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close dialog")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filteredCategories = categories.filter {
                                when (transactionType) {
                                    "income" -> it.isIncome && !it.isSavings
                                    "savings" -> it.isSavings
                                    else -> !it.isIncome && !it.isSavings
                                }
                            }
                            items(filteredCategories) { cat ->
                                val catColor = CategoryIconHelper.parseColor(cat.colorHex)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.03f))
                                        .border(1.dp, if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Icon block
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(catColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CategoryIconHelper.getIconForName(cat.iconName),
                                            contentDescription = cat.name,
                                            tint = catColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Name & Type Badge
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = when {
                                                cat.isSavings -> "Savings"
                                                cat.isIncome -> "Income"
                                                else -> "Expense"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = when {
                                                cat.isSavings -> Color(0xFF3B82F6)
                                                cat.isIncome -> Color(0xFF10B981)
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }

                                    // Rename edit btn
                                    IconButton(
                                        onClick = { showRenameDialogForCategory = cat },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename category",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete btn
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                // Check constraints
                                                val count = viewModel.getExpenseCountForCategory(cat.id)
                                                if (count > 0) {
                                                    Toast.makeText(
                                                        context,
                                                        "Warning: $count expenses use this category. Reassign or delete expenses before removing.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    viewModel.deleteCategory(cat)
                                                    Toast.makeText(context, "Deleted category \"${cat.name}\"", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete category",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Secondary Sub-dialog for Rename ---
            val categoryToRename = showRenameDialogForCategory
            if (categoryToRename != null) {
                key(categoryToRename.id) {
                    var renameName by remember { mutableStateOf(categoryToRename.name) }
                    var renameBudget by remember { 
                        mutableStateOf(
                            categoryToRename.monthlyBudget?.let { 
                                if (it % 1.0 == 0.0) String.format(Locale.US, "%.0f", it) else it.toString()
                            } ?: ""
                        )
                    }
                    val isRecInitial = remember(categoryToRename.id) { viewModel.isCategoryBudgetRecurringForMonth(categoryToRename.id, homeMonth, homeYear) }
                    var renameIsRecurring by remember(categoryToRename.id) { mutableStateOf(isRecInitial) }
                    val isSettledInitial = remember(categoryToRename.id) { viewModel.isCategoryBudgetSettledForMonth(categoryToRename.id, homeMonth, homeYear) }
                    var renameIsSettled by remember(categoryToRename.id) { mutableStateOf(isSettledInitial) }
                    var renameIcon by remember { mutableStateOf(categoryToRename.iconName) }
                    var renameColorHex by remember { mutableStateOf(categoryToRename.colorHex) }

                    AlertDialog(
                        onDismissRequest = { showRenameDialogForCategory = null },
                        title = { Text("Rename Category", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = renameName,
                                    onValueChange = { renameName = it },
                                    placeholder = { Text("Category Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                        focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                )

                                // Icon List mapping
                                Text("Pick Icon", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.height(48.dp)) {
                                        androidx.compose.foundation.lazy.LazyRow {
                                            items(CategoryIconHelper.availableIconsList) { item ->
                                                val isSelected = renameIcon == item
                                                val activeHighlightColor = MaterialTheme.colorScheme.primary
                                                Box(
                                                    modifier = Modifier
                                                        .padding(horizontal = 4.dp)
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) activeHighlightColor.copy(alpha = if (isDark) 0.25f else 0.12f)
                                                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                                                        )
                                                        .border(
                                                            width = 1.5.dp,
                                                            color = if (isSelected) activeHighlightColor else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { renameIcon = item },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = CategoryIconHelper.getIconForName(item),
                                                        contentDescription = item,
                                                        tint = if (isSelected) activeHighlightColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Palette Picker
                                Text("Pick Color", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.height(40.dp)) {
                                        androidx.compose.foundation.lazy.LazyRow {
                                            items(CategoryIconHelper.availableColorsList) { colorHex ->
                                                val isSelected = renameColorHex == colorHex
                                                val c = CategoryIconHelper.parseColor(colorHex)
                                                Box(
                                                    modifier = Modifier
                                                        .padding(horizontal = 4.dp)
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(c)
                                                        .border(
                                                            width = 2.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { renameColorHex = colorHex }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Monthly Budget section
                                if (!categoryToRename.isIncome && !categoryToRename.isSavings) {
                                    Column {
                                        Text(
                                            text = "Monthly Budget (optional)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = renameBudget,
                                            onValueChange = { input ->
                                                if (input.all { it.isDigit() || it == '.' }) {
                                                    renameBudget = input
                                                }
                                            },
                                            placeholder = { Text("e.g. ${currencySymbol}3,000") },
                                            leadingIcon = { Text(currencySymbol, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                                focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                            )
                                        )
                                        val otherBudgetsSum = remember(categories, categoryToRename.id) {
                                            categories.filter { !it.isIncome && !it.isSavings && it.id != categoryToRename.id }
                                                .sumOf { it.monthlyBudget ?: 0.0 }
                                        }
                                        val enteredBudgetAmt = renameBudget.trim().toDoubleOrNull() ?: 0.0
                                        val availableBalanceForEdit = kotlin.math.round((incomeThisMonthAmount - savingsThisMonthAmount) - otherBudgetsSum)
                                        val adjustedBalance = availableBalanceForEdit - enteredBudgetAmt
                                        val formattedBalance = String.format(Locale.getDefault(), "%,.0f", adjustedBalance)
                                        Text(
                                            text = "Remaining Balance: $currencySymbol$formattedBalance",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (adjustedBalance >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "We'll alert you when you're close to this limit",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = "Recurring Expense",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Enable to automatically copy this budget to all future/upcoming months",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = renameIsRecurring,
                                                onCheckedChange = { renameIsRecurring = it }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = "Mark as Settled",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Hides it from the main active spending list once paid.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = renameIsSettled,
                                                onCheckedChange = { renameIsSettled = it }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (renameName.isNotBlank()) {
                                        viewModel.updateCategory(
                                            categoryToRename.copy(
                                                name = renameName.trim(),
                                                iconName = renameIcon,
                                                colorHex = renameColorHex,
                                                monthlyBudget = if (!categoryToRename.isIncome && !categoryToRename.isSavings && (renameBudget.toDoubleOrNull() ?: 0.0) > 0.0) renameBudget.toDoubleOrNull() else null
                                            ),
                                            isRecurring = renameIsRecurring,
                                            isSettled = renameIsSettled
                                        )
                                        showRenameDialogForCategory = null
                                        Toast.makeText(context, "Category updated", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = renameName.isNotBlank()
                             ) {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialogForCategory = null }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                        }
                    )
                }
            }
        }
    }
}
