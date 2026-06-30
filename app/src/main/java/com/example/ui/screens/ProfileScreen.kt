package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CategoryIconHelper
import com.example.ui.components.StyledSwitch
import com.example.ui.TrackerViewModel
import com.example.ui.ExportState
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.items
import com.example.data.model.Category
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: TrackerViewModel,
    onManageCategories: () -> Unit,
    onCopyBudget: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleDailySpendNotification(true)
        } else {
            Toast.makeText(context, "Notification permission is required to receive daily spend summaries.", Toast.LENGTH_LONG).show()
        }
    }

    val userName by viewModel.userName.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    val themeMode by viewModel.themeMode.collectAsState()
    val dailySpendNotificationEnabled by viewModel.dailySpendNotificationEnabled.collectAsState()
    val dailySpendNotificationTime by viewModel.dailySpendNotificationTime.collectAsState()
    val sheetsSyncEnabled by viewModel.sheetsSyncEnabled.collectAsState()
    val connectedSheetId by viewModel.connectedSheetId.collectAsState()
    val googleEmail by viewModel.googleEmail.collectAsState()
    val googleAccessToken by viewModel.googleAccessToken.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    // Profile editable state
    var editName by remember { mutableStateOf(userName) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    // Manual backup & restore state
    var showExportDialog by remember { mutableStateOf(false) }
    var backupJsonText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showManualTextInput by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    // Launcher for exporting/downloading JSON backup file (Offline SAF Download)
    val createBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(backupJsonText.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Backup downloaded successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to download backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Launcher for selecting/restoring JSON backup file via System File Picker
    val importBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
                    if (jsonString != null) {
                        val success = viewModel.importBackupJson(jsonString)
                        if (success) {
                            Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                            showImportDialog = false
                        } else {
                            Toast.makeText(context, "Failed to restore backup. Invalid file format.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Failed to read selected file.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Share File Helper using cache/FileProvider
    val shareBackupFileHelper = remember {
        { ctx: android.content.Context, jsonText: String ->
            try {
                val cacheDir = ctx.cacheDir
                val currentDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val cacheFile = java.io.File(cacheDir, "hive_data_backup_$currentDateStr.json")
                cacheFile.writeText(jsonText)
                val authority = "${ctx.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, authority, cacheFile)
                
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "application/json"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(sendIntent, "Share Hive Data Backup"))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Synchronize edit state when database pulls
    LaunchedEffect(userName) {
        editName = userName
    }





    // Handle export state Toast signals
    LaunchedEffect(exportState) {
        when (exportState) {
            is ExportState.Success -> {
                Toast.makeText(context, "Export to Google Sheets was successful!", Toast.LENGTH_LONG).show()
                viewModel.resetExportState()
            }
            is ExportState.Error -> {
                Toast.makeText(context, (exportState as ExportState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetExportState()
            }
            else -> {}
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toolbar
        item {
            Text(
                text = "Profile Settings",
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )
        }

        // Subheading: Account & Currency
        item {
            Text(
                text = "ACCOUNT & CURRENCY",
                color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
            )
        }

        // Card: Account & Currency details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF131315) else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF1F1F21) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                        disabledTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = if (isDark) Color(0xFF27272A) else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isDark) Color(0xFF1E1E1F) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                        disabledBorderColor = if (isDark) Color(0xFF1E1E1F) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        focusedPlaceholderColor = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        disabledPlaceholderColor = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        disabledTrailingIconColor = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        focusedTrailingIconColor = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        unfocusedTrailingIconColor = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    // Your Name edit section
                    Column {
                        Text(
                            text = "Your Name",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editName,
                            onValueChange = {
                                editName = it
                                viewModel.saveName(it)
                            },
                            placeholder = { 
                                Text(
                                    text = "Enter your name",
                                    color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                ) 
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input"),
                            colors = textFieldColors
                        )
                    }

                    // Base Currency selector with dropdown
                    Column {
                        Text(
                            text = "Base Currency",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val displayLabel = when (selectedCurrency.uppercase()) {
                                "INR" -> "Indian Rupee (₹ INR)"
                                "GBP" -> "British Pound (£ GBP)"
                                "EUR" -> "Euro (€ EUR)"
                                "USD" -> "US Dollar ($ USD)"
                                "AED" -> "UAE Dirham (د.إ AED)"
                                else -> "Indian Rupee (₹ INR)"
                            }
                            
                            OutlinedTextField(
                                value = displayLabel,
                                onValueChange = {},
                                readOnly = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCurrencyDropdown = true }
                                    .testTag("profile_currency_input"),
                                enabled = false, // false so that entire textfield is clickable without focus
                                colors = textFieldColors,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown Indicator",
                                        modifier = Modifier.clickable { showCurrencyDropdown = true }
                                    )
                                }
                            )
                            
                            // Absolute overlay to make clicking the OutlinedTextField work seamlessly when disabled
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showCurrencyDropdown = true }
                            )

                            DropdownMenu(
                                expanded = showCurrencyDropdown,
                                onDismissRequest = { showCurrencyDropdown = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color(0xFF131315) else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, if (isDark) Color(0xFF1F1F21) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            ) {
                                val currencies = listOf(
                                    Pair("INR", "Indian Rupee (₹ INR)"),
                                    Pair("GBP", "British Pound (£ GBP)"),
                                    Pair("EUR", "Euro (€ EUR)"),
                                    Pair("USD", "US Dollar ($ USD)"),
                                    Pair("AED", "UAE Dirham (د.إ AED)")
                                )
                                currencies.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                        },
                                        onClick = {
                                            viewModel.saveCurrency(code)
                                            showCurrencyDropdown = false
                                        },
                                        modifier = Modifier.testTag("currency_item_${code.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subheading: App Customization
        item {
            Text(
                text = "APP CUSTOMIZATION",
                color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
            )
        }

        // Card: App Customization settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF131315) else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF1F1F21) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // App Appearance Row
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "App Appearance",
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        val options = listOf("system", "light", "dark")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (isDark) Color(0xFF1E1E24) else Color(0xFFEAEBF0))
                                .padding(3.dp)
                        ) {
                            options.forEach { opt ->
                                val isSel = themeMode == opt
                                val activeBgColor = MaterialTheme.colorScheme.primary
                                val activeTextColor = MaterialTheme.colorScheme.onPrimary
                                val inactiveTextColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(if (isSel) activeBgColor else Color.Transparent)
                                        .clickable { viewModel.saveTheme(opt) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opt.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) activeTextColor else inactiveTextColor,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Divider inside card
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = if (isDark) Color(0xFF1F1F21) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                    )

                    // Daily Spend Summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Spend Summary",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Receive high-fidelity summaries of today's budgets and spends.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        
                        StyledSwitch(
                            checked = dailySpendNotificationEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        
                                        if (hasPerm) {
                                            viewModel.toggleDailySpendNotification(true)
                                        } else {
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        viewModel.toggleDailySpendNotification(true)
                                    }
                                } else {
                                    viewModel.toggleDailySpendNotification(false)
                                }
                            },
                            modifier = Modifier.testTag("daily_spend_notification_toggle")
                        )
                    }

                    AnimatedVisibility(visible = dailySpendNotificationEnabled) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.03f))
                                    .clickable {
                                        val parts = dailySpendNotificationTime.split(":")
                                        val curHour = parts.getOrNull(0)?.toIntOrNull() ?: 23
                                        val curMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                val formatted = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                                                viewModel.updateDailySpendNotificationTime(formatted)
                                                val h12 = when {
                                                    hour == 0 -> 12
                                                    hour > 12 -> hour - 12
                                                    else -> hour
                                                }
                                                val period = if (hour >= 12) "PM" else "AM"
                                                val displayTime = String.format(java.util.Locale.US, "%02d:%02d %s", h12, minute, period)
                                                Toast.makeText(context, "Daily Spend Notification scheduled for $displayTime", Toast.LENGTH_LONG).show()
                                            },
                                            curHour,
                                            curMinute,
                                            false // set to false for AM/PM format picker
                                        ).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF8F8AFF) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Schedule delivery time",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        ),
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Format time representation to human readable 12-hour format (e.g., 10:36 PM)
                                val formattedDisplayTime = try {
                                    val parts = dailySpendNotificationTime.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 23
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    val period = if (h >= 12) "PM" else "AM"
                                    val h12 = when {
                                        h == 0 -> 12
                                        h > 12 -> h - 12
                                        else -> h
                                    }
                                    String.format(java.util.Locale.US, "%02d:%02d %s", h12, m, period)
                                } catch (e: Exception) {
                                    dailySpendNotificationTime
                                }

                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = if (isDark) Color(0xFF5F56FF).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.2.dp, if (isDark) Color(0xFF5F56FF).copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        text = formattedDisplayTime,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDark) Color(0xFF8F8AFF) else MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subheading: Data & Utilities
        item {
            Text(
                text = "DATA & UTILITIES",
                color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
            )
        }

        // Card: Data & Utilities buttons
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF131315) else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF1F1F21) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Category Manager
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Category Manager",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Add new custom categories, configure budget limits, delete unused categories, or edit icons and colors.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        val buttonPrimaryColor = if (isDark) Color(0xFF5F56FF) else MaterialTheme.colorScheme.primary
                        Button(
                            onClick = { onManageCategories() },
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("profile_manage_categories_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonPrimaryColor,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Manage & Create Categories", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Divider inside card between Category Manager and Copy Budget Details
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = if (isDark) Color(0xFF1F1F21) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                    )

                    // Copy Budget Details
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Copy Monthly Budget",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Duplicate and apply complete budget settings, recurring categories, and custom constraints from any past month to save setup time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        val buttonPrimaryColor = if (isDark) Color(0xFF5F56FF) else MaterialTheme.colorScheme.primary
                        Button(
                            onClick = { onCopyBudget() },
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("profile_copy_budget_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonPrimaryColor,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Copy Past Budget Details", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Divider inside card between Category Manager and Local Backup & Restore
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = if (isDark) Color(0xFF1F1F21) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                    )

                    // Local Backup & Restore
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Local Backup & Restore",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Securely save your data offline. Export backup keys or restore information manually at any time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val buttonPrimaryColor = if (isDark) Color(0xFF5F56FF) else MaterialTheme.colorScheme.primary
                            
                            // Export Button (Capsule, Purple)
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val backupString = viewModel.exportBackupJson()
                                        backupJsonText = backupString
                                        showExportDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(99.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("profile_export_backup_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonPrimaryColor,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text("Export", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Restore Button (Capsule, Outlined, White in dark mode, Primary in light mode)
                            val restoreStrokeColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                            OutlinedButton(
                                onClick = {
                                    showImportDialog = true
                                },
                                shape = RoundedCornerShape(99.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("profile_import_backup_btn"),
                                border = BorderStroke(1.2.dp, restoreStrokeColor),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = restoreStrokeColor
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text("Restore", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // App version / footer indicator at the bottom
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Made with ❤️ by Nagul",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    }

    // Backup & Restore Dialogs
    if (showExportDialog) {
        Dialog(onDismissRequest = { showExportDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF13151A) else Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Backup Exported",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    )
                    Text(
                        text = "Your offline backup file is ready. Choose to download it directly or share it securely.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val buttonPrimaryColor = if (isDark) Color(0xFF5F56FF) else MaterialTheme.colorScheme.primary

                        // Download File Button
                        Button(
                            onClick = {
                                try {
                                    val currentDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    createBackupDocumentLauncher.launch("hive_data_backup_$currentDateStr.json")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clipData = android.content.ClipData.newPlainText("Hive Data Backup", backupJsonText)
                                    clipboardManager.setPrimaryClip(clipData)
                                    Toast.makeText(context, "No system file picker detected. Backup copied to clipboard!", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonPrimaryColor)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Download Backup File", fontWeight = FontWeight.Bold)
                            }
                        }

                        val shareButtonColor = if (isDark) Color.White else buttonPrimaryColor

                        // Share File Button
                        OutlinedButton(
                            onClick = {
                                shareBackupFileHelper(context, backupJsonText)
                            },
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_backup_btn"),
                            border = BorderStroke(1.2.dp, shareButtonColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = shareButtonColor
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = shareButtonColor, modifier = Modifier.size(20.dp))
                                Text("Share Backup File", fontWeight = FontWeight.Bold, color = shareButtonColor)
                            }
                        }
                    }

                    TextButton(
                        onClick = { showExportDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        Dialog(onDismissRequest = { 
            showImportDialog = false
            showManualTextInput = false
            importJsonText = ""
        }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF13151A) else Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Restore Data Backup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    )
                    Text(
                        text = "Select a previously downloaded backup JSON file to restore your settings, categories, and transaction history. This will overwrite current offline local records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    // Big Restore Action Button - Dynamic Stroke/Outlined Colors: White in dark mode, Primary in light mode
                    val restoreStrokeColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                    OutlinedButton(
                        onClick = {
                            try {
                                importBackupDocumentLauncher.launch("*/*")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "No system file picker found. Please ensure a file explorer is installed.", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_restore_file_btn"),
                        border = BorderStroke(1.dp, restoreStrokeColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = restoreStrokeColor
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(22.dp))
                            Text("Upload Backup File", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { 
                                showImportDialog = false
                                showManualTextInput = false
                                importJsonText = ""
                            }
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
