package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.TrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyBudgetScreen(
    viewModel: TrackerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val homeMonth by viewModel.homeMonth.collectAsState()
    val homeYear by viewModel.homeYear.collectAsState()

    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val accentColor = MaterialTheme.colorScheme.primary

    val monthsList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val targetMonthName = monthsList.getOrNull(homeMonth - 1) ?: "Current Month"

    // Generate last 12 months for source selection
    val pastMonthsList = remember(homeMonth, homeYear) {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.YEAR, homeYear)
        calendar.set(java.util.Calendar.MONTH, homeMonth - 1) // 0-indexed

        val list = mutableListOf<Pair<Int, Int>>()
        for (i in 1..12) {
            calendar.add(java.util.Calendar.MONTH, -1)
            list.add(Pair(calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.YEAR)))
        }
        list
    }

    var selectedSourceMonthYear by remember { mutableStateOf<Pair<Int, Int>?>(pastMonthsList.firstOrNull()) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dropdownWidth by remember { mutableStateOf(0.dp) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Copy Budget Details",
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
                            .testTag("copy_budget_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                windowInsets = WindowInsets(0.dp)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown Selector for past months styled as OutlinedTextField
            var dropdownExpanded by remember { mutableStateOf(false) }
            val selectedLabel = selectedSourceMonthYear?.let { (m, y) ->
                val fullMonthName = monthsList.getOrNull(m - 1) ?: ""
                val abbrMonthName = if (fullMonthName.length > 3) fullMonthName.substring(0, 3) else fullMonthName
                "$abbrMonthName $y"
            } ?: ""

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        dropdownWidth = with(density) { coordinates.size.width.toDp() }
                    }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Copy budget from") },
                    placeholder = { Text("Select a Month") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = if (dropdownExpanded) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Open Dropdown",
                            tint = if (dropdownExpanded) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(if (dropdownExpanded) 180f else 0f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (dropdownExpanded) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF444444) else Color(0xFFD1D5DB)),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = if (dropdownExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                )

                // Overlay to handle click and open dropdown safely without keyboard/focus
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { dropdownExpanded = !dropdownExpanded }
                        .testTag("copy_budget_source_dropdown_trigger")
                )

                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        surface = if (isDark) Color.Black else Color.White,
                        surfaceContainer = if (isDark) Color.Black else Color.White,
                        surfaceContainerHigh = if (isDark) Color.Black else Color.White,
                        surfaceContainerHighest = if (isDark) Color.Black else Color.White,
                        surfaceContainerLow = if (isDark) Color.Black else Color.White,
                        surfaceContainerLowest = if (isDark) Color.Black else Color.White
                    )
                ) {
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        offset = DpOffset(0.dp, 8.dp),
                        modifier = Modifier
                            .width(dropdownWidth)
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.Black else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color(0xFF444444) else Color(0xFFD1D5DB),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("copy_budget_source_dropdown_menu")
                    ) {
                        pastMonthsList.forEach { (m, y) ->
                            val fullMonthName = monthsList.getOrNull(m - 1) ?: ""
                            val abbrMonthName = if (fullMonthName.length > 3) fullMonthName.substring(0, 3) else fullMonthName
                            val labelShort = "$abbrMonthName $y"
                            val isSelected = selectedSourceMonthYear?.first == m && selectedSourceMonthYear?.second == y

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = labelShort,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) accentColor else (if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedSourceMonthYear = Pair(m, y)
                                    dropdownExpanded = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    leadingIconColor = accentColor,
                                    trailingIconColor = accentColor
                                ),
                                modifier = Modifier
                                    .testTag("dropdown_item_${m}_${y}")
                                    .background(
                                        if (isSelected) {
                                            if (isDark) Color(0xFF222222) else Color(0xFFE5E7EB)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target Month Section styled as OutlinedTextField
            OutlinedTextField(
                value = "$targetMonthName $homeYear",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Copy budget to") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_budget_target_field"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    disabledBorderColor = if (isDark) Color(0xFF444444) else Color(0xFFD1D5DB),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    disabledContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // Info Notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Importing will copy category budgets, recurring flags, settled flags, and category structures to $targetMonthName $homeYear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            // CTAs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("copy_budget_cancel_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        val source = selectedSourceMonthYear
                        if (source != null) {
                            viewModel.copyMonthlyBudgets(
                                srcMonth = source.first,
                                srcYear = source.second,
                                targetMonth = homeMonth,
                                targetYear = homeYear,
                                onComplete = {
                                    onBack()
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .testTag("copy_budget_import_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    enabled = selectedSourceMonthYear != null
                ) {
                    Text(
                        text = "Import Details",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
