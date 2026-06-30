package com.example.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.Locale

object CurrencyFormatter {
    fun formatFinanceAmount(
        currencySymbol: String,
        amount: Double,
        baseFontSize: TextUnit = 14.sp,
        decimalScale: Float = 0.8f,
        prefix: String = ""
    ): AnnotatedString {
        val isWhole = amount % 1.0 == 0.0
        if (isWhole) {
            return buildAnnotatedString {
                append(prefix)
                append(currencySymbol)
                append(String.format(Locale.US, "%,.0f", amount))
            }
        }

        val formattedFull = String.format(Locale.US, "%,.2f", amount)
        val dotIndex = formattedFull.lastIndexOf('.')
        if (dotIndex == -1) {
            return buildAnnotatedString {
                append(prefix)
                append(currencySymbol)
                append(formattedFull)
            }
        }
        val mainPart = formattedFull.substring(0, dotIndex)
        val decimalPart = formattedFull.substring(dotIndex) // includes the dot, e.g., ".56"

        return buildAnnotatedString {
            append(prefix)
            append(currencySymbol)
            append(mainPart)
            
            val decimalSize = if (baseFontSize.isSp) {
                (baseFontSize.value * decimalScale).sp
            } else {
                baseFontSize
            }
            withStyle(style = SpanStyle(fontSize = decimalSize)) {
                append(decimalPart)
            }
        }
    }

    fun formatPlain(currencySymbol: String, amount: Double): String {
        val isWhole = amount % 1.0 == 0.0
        return if (isWhole) {
            currencySymbol + String.format(Locale.US, "%,.0f", amount)
        } else {
            currencySymbol + String.format(Locale.US, "%,.2f", amount)
        }
    }
}

@Composable
fun FinanceText(
    currencySymbol: String,
    amount: Double,
    baseFontSize: TextUnit = 14.sp,
    decimalScale: Float = 0.8f,
    prefix: String = "",
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Text(
            text = CurrencyFormatter.formatFinanceAmount(currencySymbol, amount, baseFontSize, decimalScale, prefix),
            color = color,
            fontWeight = fontWeight,
            style = style.copy(fontSize = baseFontSize),
            modifier = modifier
        )
    } else {
        Text(
            text = "$prefix$currencySymbol •••••",
            color = color,
            fontWeight = fontWeight,
            style = style.copy(fontSize = baseFontSize),
            modifier = modifier
        )
    }
}
