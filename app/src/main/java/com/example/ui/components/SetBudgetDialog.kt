package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import java.util.Locale

@Composable
fun SetBudgetDialog(
    category: Category,
    initialIsRecurring: Boolean,
    initialIsSettled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double, Boolean, Boolean) -> Unit,
    availableBalance: Double? = null,
    currencySymbol: String = "₹"
) {
    var budgetStr by remember { mutableStateOf(category.monthlyBudget?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "") }
    var isRecurring by remember { mutableStateOf(initialIsRecurring) }
    var isSettled by remember { mutableStateOf(initialIsSettled) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Budget for ${category.name}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a monthly budget limit for this category to track limits and remaining amounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val isDark = MaterialTheme.colorScheme.background.red < 0.5f
                val borderColor = if (isDark) Color.White else Color.Black

                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = {
                        budgetStr = it
                        isError = false
                    },
                    label = { Text("Monthly Budget ($currencySymbol)", color = borderColor) },
                    placeholder = { Text("e.g. 5000", color = borderColor.copy(alpha = 0.6f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = borderColor,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = borderColor,
                        unfocusedLabelColor = borderColor,
                        cursorColor = borderColor,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Please enter a valid positive number",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (availableBalance != null) {
                    val enteredBudgetAmt = budgetStr.trim().toDoubleOrNull() ?: 0.0
                    val adjustedBalance = availableBalance - enteredBudgetAmt
                    val formattedBalance = String.format(Locale.getDefault(), "%,.0f", adjustedBalance)
                    Text(
                        text = "Remaining Balance: $currencySymbol$formattedBalance",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (adjustedBalance >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                        checked = isSettled,
                        onCheckedChange = { isSettled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = budgetStr.trim()
                    if (trimmed.isEmpty()) {
                        onSave(0.0, isRecurring, isSettled)
                    } else {
                        val amt = trimmed.toDoubleOrNull()
                        if (amt != null && amt >= 0.0) {
                            onSave(amt, isRecurring, isSettled)
                        } else {
                            isError = true
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
