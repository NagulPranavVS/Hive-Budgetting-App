package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.model.Category
import java.util.Locale

import com.example.ui.components.InfoIconTooltip
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.Info

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
    var budgetStr by remember { mutableStateOf(category.monthlyBudget?.let {
        if (it % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", it)
        } else {
            String.format(Locale.US, "%.2f", it)
        }
    } ?: "") }
    var isRecurring by remember { mutableStateOf(initialIsRecurring) }
    var isSettled by remember { mutableStateOf(initialIsSettled) }
    var isError by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
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
                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = { input ->
                        val allowedChars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '+', '-', '*', '/')
                        if (input.all { it in allowedChars }) {
                            budgetStr = input
                            isError = false
                        }
                    },
                    label = { Text("Monthly Budget ($currencySymbol)") },
                    placeholder = { Text("e.g. 5000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    trailingIcon = if (budgetStr.any { it in listOf('+', '-', '*', '/') }) {
                        {
                            val parsed = evaluateExpression(budgetStr)
                            val isTickEnabled = parsed != null && parsed >= 0.0
                            IconButton(
                                onClick = {
                                    if (parsed != null) {
                                        budgetStr = if (parsed % 1.0 == 0.0) {
                                            parsed.toLong().toString()
                                        } else {
                                            String.format(Locale.US, "%.2f", parsed)
                                        }
                                        isError = false
                                    }
                                },
                                enabled = isTickEnabled
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Evaluate expression",
                                    tint = if (isTickEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    } else {
                        null
                    },
                    isError = isError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isDark) Color(0xFF444444) else Color(0xFFD1D5DB),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Please enter a valid positive number or mathematical expression",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (availableBalance != null) {
                    val enteredBudgetAmt = getActiveBudgetVal(budgetStr) ?: 0.0
                    val adjustedBalance = availableBalance - enteredBudgetAmt
                    val formattedBalance = String.format(Locale.getDefault(), "%,.2f", adjustedBalance)
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
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Recurring Expense",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        InfoIconTooltip(
                            description = "Enable to automatically copy this budget to all future/upcoming months",
                            contentDescription = "Recurring Expense Info"
                        )
                    }
                    StyledSwitch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Mark as Settled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        InfoIconTooltip(
                            description = "Hides it from the main active spending list once paid.",
                            contentDescription = "Mark as Settled Info"
                        )
                    }
                    StyledSwitch(
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
                        val parsedAmt = if (trimmed.any { it in listOf('+', '-', '*', '/') }) {
                            evaluateExpression(trimmed)
                        } else {
                            trimmed.toDoubleOrNull()
                        }
                        if (parsedAmt != null && parsedAmt >= 0.0) {
                            onSave(parsedAmt, isRecurring, isSettled)
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

/**
 * Robustly parses and calculates the current active numeric value of an expression.
 * If the expression is incomplete (e.g. ends with an operator), it strips trailing operators
 * to evaluate the current working prefix so that the displayed remaining balance doesn't jump.
 */
fun getActiveBudgetVal(input: String): Double? {
    var s = input.trim()
    if (s.isEmpty()) return null
    
    // Check if it has any operators
    val hasOperators = s.any { it in listOf('+', '-', '*', '/') }
    if (hasOperators) {
        // Strip trailing operators or open parenthesis
        while (s.isNotEmpty() && s.last() in listOf('+', '-', '*', '/', '(', ' ')) {
            s = s.substring(0, s.length - 1).trim()
        }
        if (s.isEmpty()) return null
        return evaluateExpression(s)
    } else {
        return s.toDoubleOrNull()
    }
}

/**
 * Robust mathematical expression evaluator supporting +, -, *, / and parentheses.
 * Returns null if the expression is invalid or cannot be parsed.
 */
fun evaluateExpression(expr: String): Double? {
    val s = expr.replace(" ", "")
    if (s.isEmpty()) return null
    return try {
        object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < s.length) s[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < s.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw RuntimeException("Division by zero")
                        x /= divisor
                    }
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = this.pos
                if (eat('('.code)) {
                    x = parseExpression()
                    if (!eat(')'.code)) throw RuntimeException("Missing closing parenthesis")
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    val numStr = s.substring(startPos, this.pos)
                    x = numStr.toDoubleOrNull() ?: throw RuntimeException("Invalid number: $numStr")
                } else {
                    throw RuntimeException("Unexpected character: " + ch.toChar())
                }

                return x
            }
        }.parse()
    } catch (e: Exception) {
        null
    }
}
