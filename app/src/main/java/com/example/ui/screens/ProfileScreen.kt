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
    onManageCategories: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val userName by viewModel.userName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
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
            .background(Color.Transparent),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Toolbar
        item {
            Text(
                text = "Profile Settings",
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Subheading: Personal details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Personal Details",
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    )

                    // Your Name edit section
                    Column {
                        Text(
                            text = "Your Name",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editName,
                            onValueChange = {
                                editName = it
                                viewModel.saveName(it)
                            },
                            placeholder = { Text("Enter your name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = if (isDark) Color.White else Color.Black,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                focusedLabelColor = if (isDark) Color.White else Color.Black,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        )
                    }

                    // Currency (Fixed display in INR - icon removed for Requirement 11)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                               text = "Base Currency",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Indian Rupee (₹ INR)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }



        // Subheading: Theme configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "App Appearance",
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Dropdown / Row choice light, dark, system
                    val options = listOf("system", "light", "dark")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                            .padding(3.dp)
                    ) {
                        options.forEach { opt ->
                            val isSel = themeMode == opt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSel) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.saveTheme(opt) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }        // Subheading: Category Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Category Manager",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Add new custom categories, configure budget limits, delete unused categories, or edit icons and colors.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Button(
                        onClick = { onManageCategories() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_manage_categories_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null)
                            Text("Manage & Create Categories", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Subheading: Data Backup & Recovery Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Local Backup & Restore",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Securely save your data offline. Export backup keys or restore information manually at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Export Button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val backupString = viewModel.exportBackupJson()
                                    backupJsonText = backupString
                                    showExportDialog = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_export_backup_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Export", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Restore Button - Dynamic stroke/outlined colors (White in dark mode, Primary in light mode)
                        val restoreStrokeColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                        OutlinedButton(
                            onClick = {
                                showImportDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_import_backup_btn"),
                            border = BorderStroke(1.dp, restoreStrokeColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = restoreStrokeColor
                            )
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

        // App version indicator at the bottom
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Made with ❤️ by Nagul",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Download Backup File", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Share File Button
                        Button(
                            onClick = {
                                shareBackupFileHelper(context, backupJsonText)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Share Backup File", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    TextButton(
                        onClick = { showExportDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
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
                                showManualTextInput = true
                                Toast.makeText(context, "No system file picker found. Paste your backup code directly instead.", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
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

                    if (!showManualTextInput) {
                        TextButton(
                            onClick = { showManualTextInput = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Paste backup text code manually", color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = importJsonText,
                                onValueChange = { importJsonText = it },
                                placeholder = { Text("Paste JSON backup code here...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("import_backup_input"),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            Button(
                                onClick = {
                                    if (importJsonText.isBlank()) {
                                        Toast.makeText(context, "Please paste valid backup code.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    coroutineScope.launch {
                                        val success = viewModel.importBackupJson(importJsonText)
                                        if (success) {
                                            Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                                            showImportDialog = false
                                            showManualTextInput = false
                                            importJsonText = ""
                                        } else {
                                            Toast.makeText(context, "Failed to restore backup. Invalid code format.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Verify & Restore Pasted Backup", fontWeight = FontWeight.Bold)
                            }
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
