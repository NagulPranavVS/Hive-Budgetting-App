package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import kotlinx.coroutines.launch
import com.example.ui.CategoryIconHelper
import com.example.ui.TrackerViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    viewModel: TrackerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val categories by viewModel.categories.collectAsState()
    val homeMonth by viewModel.homeMonth.collectAsState()
    val homeYear by viewModel.homeYear.collectAsState()

    val totalExpenseCatBudget = remember(categories) {
        categories.filter { !it.isIncome && !it.isSavings }.sumOf { it.monthlyBudget ?: 0.0 }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var dialogCategoryType by remember { mutableStateOf("expense") }
    var showRenameDialogForCategory by remember { mutableStateOf<Category?>(null) }

    val allExpenses by viewModel.expenses.collectAsState()
    val homeMonthExpenses = remember(allExpenses, homeMonth, homeYear) {
        val cal = java.util.Calendar.getInstance()
        allExpenses.filter { exp ->
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
    
    // Split categories into Income, Expense, and Savings categories
    val expenseCategories = remember(categories) { categories.filter { !it.isIncome && !it.isSavings } }
    val incomeCategories = remember(categories) { categories.filter { it.isIncome && !it.isSavings } }
    val savingsCategories = remember(categories) { categories.filter { it.isSavings } }

    var selectedSection by remember { mutableStateOf("expense") } // "expense", "income", or "savings"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Manage Categories",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
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
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            
            // Search Input field (animated when expanded)
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn() + expandVertically() + slideInVertically(),
                exit = fadeOut() + shrinkVertically() + slideOutVertically()
            ) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        placeholder = {
                            Text(
                                text = "Search categories...",
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
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
            }

            // --- REDESIGNED TOTAL CATEGORY BUDGET CARD ---
            val budgetCardBg = if (isDark) MaterialTheme.colorScheme.surface else Color(0xFFF5F7FF)
            val accentCardColor = MaterialTheme.colorScheme.primary
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 18.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = budgetCardBg),
                border = BorderStroke(1.dp, accentCardColor.copy(alpha = if (isDark) 0.15f else 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(accentCardColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = "Category Budgets",
                            tint = accentCardColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    
                    val shortMonthsListEx = listOf(
                        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                    )
                    val homeMonthLabelEx = shortMonthsListEx.getOrNull(homeMonth - 1) ?: "Jan"

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Total Category Budget ($homeMonthLabelEx $homeYear)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "₹${String.format(Locale.US, "%,.0f", totalExpenseCatBudget)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = if (isDark) Color.White else Color(0xFF1E1B4B)
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "Sum of all category budgets for $homeMonthLabelEx $homeYear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                        )
                    }
                }
            }
            
            // --- SEGMENTED TAB BAR BELOW THE CARD ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        if (isDark) Color(0xFF1C1C1E)
                        else Color(0xFFF1F3F9)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("expense" to "Expenses", "income" to "Income", "savings" to "Savings").forEach { (section, label) ->
                    val isSelected = selectedSection == section
                    val capsuleColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }
                    val textColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        if (isDark) Color.White.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    }
                    val labelWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(capsuleColor)
                            .clickable { selectedSection = section }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = labelWeight,
                                color = textColor
                            )
                        )
                    }
                }
            }

            val displayedList = when (selectedSection) {
                "income" -> incomeCategories
                "savings" -> savingsCategories
                else -> expenseCategories
            }
            val filteredCategories = remember(displayedList, searchQuery) {
                if (searchQuery.isBlank()) {
                    displayedList
                } else {
                    displayedList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }
            val numCategoriesPlaceholder = when (selectedSection) {
                "income" -> "No income categories found."
                "savings" -> "No savings categories found."
                else -> "No expense categories found."
            }

            // --- TITLE & ADD CATEGORY BUTTON UNDER BOTH SECTIONS (Adaptive section header) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (selectedSection) {
                            "income" -> "Income Categories"
                            "savings" -> "Savings Categories"
                            else -> "Expense Categories"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                // Add Category button
                Button(
                    onClick = {
                        dialogCategoryType = selectedSection
                        showNewCategoryDialog = true
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // --- CATEGORIES LIST ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)
            ) {

                if (filteredCategories.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = "Empty",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = numCategoriesPlaceholder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredCategories, key = { it.id }) { category ->
                        val parsedColor = CategoryIconHelper.parseColor(category.colorHex)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.06f else 0.04f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic theme category icon with matching opaque tint plate
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(parsedColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CategoryIconHelper.getIconForName(category.iconName),
                                        contentDescription = category.name,
                                        tint = parsedColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    if (category.isSavings) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Savings category",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                        )
                                    } else if (!category.isIncome) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val budgetVal = category.monthlyBudget
                                        if (budgetVal != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(parsedColor)
                                                )
                                                Text(
                                                    text = "Budget: ₹${String.format(Locale.US, "%,.0f", budgetVal)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "No monthly limit",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Income category",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                        )
                                    }
                                }

                                // Interactive action items styled neatly side-by-side
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    IconButton(
                                        onClick = { showRenameDialogForCategory = category },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit Category",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val countInMonth = viewModel.getExpenseCountForCategoryInMonth(category.id, homeMonth, homeYear)
                                                if (countInMonth == 0) {
                                                    viewModel.deleteCategoryForMonth(category, homeMonth, homeYear)
                                                    Toast.makeText(context, "Deleted category \"${category.name}\"", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Cannot delete: used in $countInMonth expenses this month.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete Category",
                                            tint = MaterialTheme.colorScheme.error,
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

    // --- Add New Category Dialog ---
    if (showNewCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var catBudget by remember { mutableStateOf("") }
        var isRecurring by remember { mutableStateOf(false) }
        
        val iconsList = listOf("restaurant", "directions_car", "shopping_bag", "power", "movie", "medical_services", "school", "category", "monetization_on", "laptop", "trending_up", "card_giftcard")
        var selectedIcon by remember { mutableStateOf(iconsList.first()) }
        
        val colorsList = listOf("#EF4444", "#3B82F6", "#EC4899", "#F59E0B", "#8B5CF6", "#10B981", "#06B6D4", "#6B7280", "#14B8A6", "#F43F5E")
        var selectedColorHex by remember { mutableStateOf(colorsList.first()) }

        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            title = { 
                val shortMonthsListEx = listOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                )
                val homeMonthLabelEx = shortMonthsListEx.getOrNull(homeMonth - 1) ?: "Jan"
                Text(
                    text = when (dialogCategoryType) {
                        "income" -> "Add Income Category"
                        "savings" -> "Add Savings Category"
                        else -> "Add Expense Category ($homeMonthLabelEx $homeYear)"
                    }, 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                ) 
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                    if (dialogCategoryType == "expense") {
                        OutlinedTextField(
                            value = catBudget,
                            onValueChange = { catBudget = it },
                            label = { Text("Monthly Budget (Optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                        val enteredBudgetAmt = catBudget.trim().toDoubleOrNull() ?: 0.0
                        val availableBalanceForCreate = (incomeThisMonthAmount - savingsThisMonthAmount) - totalExpenseCatBudget
                        val adjustedBalance = availableBalanceForCreate - enteredBudgetAmt
                        val formattedBalance = String.format(Locale.getDefault(), "%,.0f", adjustedBalance)
                        Text(
                            text = "Remaining Balance: ₹$formattedBalance",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (adjustedBalance >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

                    // Select Icon Grid
                    Text(
                        text = "Choose an icon", 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        iconsList.take(6).forEach { iconName ->
                            val acts = selectedIcon == iconName
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (acts) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable { selectedIcon = iconName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CategoryIconHelper.getIconForName(iconName),
                                    contentDescription = iconName,
                                    tint = if (acts) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        iconsList.drop(6).forEach { iconName ->
                            val acts = selectedIcon == iconName
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (acts) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable { selectedIcon = iconName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CategoryIconHelper.getIconForName(iconName),
                                    contentDescription = iconName,
                                    tint = if (acts) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Select Color Palette Grid
                    Text(
                        text = "Choose a color", 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        colorsList.take(5).forEach { colorCode ->
                            val parsed = CategoryIconHelper.parseColor(colorCode)
                            val acts = selectedColorHex == colorCode
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parsed)
                                    .border(
                                        width = if (acts) 3.dp else 0.dp,
                                        color = if (acts) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = colorCode }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        colorsList.drop(5).forEach { colorCode ->
                            val parsed = CategoryIconHelper.parseColor(colorCode)
                            val acts = selectedColorHex == colorCode
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parsed)
                                    .border(
                                        width = if (acts) 3.dp else 0.dp,
                                        color = if (acts) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = colorCode }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (catName.isNotBlank()) {
                            viewModel.addCategory(
                                name = catName.trim(),
                                iconName = selectedIcon,
                                colorHex = selectedColorHex,
                                monthlyBudget = if (dialogCategoryType == "expense" && (catBudget.toDoubleOrNull() ?: 0.0) > 0.0) catBudget.toDoubleOrNull() else null,
                                isIncome = (dialogCategoryType == "income"),
                                isSavings = (dialogCategoryType == "savings"),
                                isRecurring = isRecurring
                            )
                            showNewCategoryDialog = false
                            Toast.makeText(context, "Added \"$catName\"", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = catName.isNotBlank()
                ) {
                    Text("Create", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        )
    }
     // --- Rename / Edit Category Dialog ---
    val categoryToRename = showRenameDialogForCategory
    if (categoryToRename != null) {
        var renameLabel by remember(categoryToRename.id) { mutableStateOf(categoryToRename.name) }
        var renameBudget by remember(categoryToRename.id) { mutableStateOf(categoryToRename.monthlyBudget?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
        val isRecInitial = remember(categoryToRename.id) { viewModel.isCategoryBudgetRecurringForMonth(categoryToRename.id, homeMonth, homeYear) }
        var renameIsRecurring by remember(categoryToRename.id) { mutableStateOf(isRecInitial) }
        val isSettledInitial = remember(categoryToRename.id) { viewModel.isCategoryBudgetSettledForMonth(categoryToRename.id, homeMonth, homeYear) }
        var renameIsSettled by remember(categoryToRename.id) { mutableStateOf(isSettledInitial) }
        
        AlertDialog(
            onDismissRequest = { showRenameDialogForCategory = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            title = { 
                val shortMonthsListEx = listOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                )
                val homeMonthLabelEx = shortMonthsListEx.getOrNull(homeMonth - 1) ?: "Jan"
                Text(
                    text = "Edit Category ($homeMonthLabelEx $homeYear)", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = renameLabel,
                        onValueChange = { renameLabel = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                    
                    if (!categoryToRename.isIncome && !categoryToRename.isSavings) {
                        OutlinedTextField(
                            value = renameBudget,
                            onValueChange = { renameBudget = it },
                            label = { Text("Monthly Budget (Optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                        val enteredBudgetAmt = renameBudget.trim().toDoubleOrNull() ?: 0.0
                        val otherBudgetsSum = categories
                            .filter { !it.isIncome && !it.isSavings && it.id != categoryToRename.id }
                            .sumOf { it.monthlyBudget ?: 0.0 }
                        val availableBalanceForEdit = (incomeThisMonthAmount - savingsThisMonthAmount) - otherBudgetsSum
                        val adjustedBalance = availableBalanceForEdit - enteredBudgetAmt
                        val formattedBalance = String.format(Locale.getDefault(), "%,.0f", adjustedBalance)
                        Text(
                            text = "Remaining Balance: ₹$formattedBalance",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (adjustedBalance >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                        Spacer(modifier = Modifier.height(4.dp))
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameLabel.isNotBlank()) {
                            viewModel.updateCategory(
                                categoryToRename.copy(
                                    name = renameLabel.trim(),
                                    monthlyBudget = if (!categoryToRename.isIncome && !categoryToRename.isSavings && (renameBudget.toDoubleOrNull() ?: 0.0) > 0.0) renameBudget.toDoubleOrNull() else null
                                ),
                                isRecurring = renameIsRecurring,
                                isSettled = renameIsSettled
                            )
                            showRenameDialogForCategory = null
                            Toast.makeText(context, "Saved changes for \"$renameLabel\"", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = renameLabel.isNotBlank()
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
