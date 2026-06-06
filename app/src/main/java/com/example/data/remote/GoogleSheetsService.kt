package com.example.data.remote

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportRow(
    val date: Long,
    val description: String,
    val categoryName: String,
    val amount: Double
)

object GoogleSheetsService {
    private const val TAG = "GoogleSheetsService"
    private val client = OkHttpClient()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Creates a new Google Spreadsheet and returns the spreadsheetId.
     */
    fun createSpreadsheet(accessToken: String, title: String): String? {
        val payload = JSONObject().apply {
            put("properties", JSONObject().apply {
                put("title", title)
            })
        }

        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val json = JSONObject(bodyStr)
                    val id = json.optString("spreadsheetId")
                    if (id.isNotEmpty()) {
                        // Initialize headers
                        writeHeaders(accessToken, id)
                        return id
                    }
                } else {
                    Log.e(TAG, "Failed to create spreadsheet: Code=${response.code}, Body=$bodyStr")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating spreadsheet", e)
        }
        return null
    }

    /**
     * Appends headers "Date", "Description", "Category", "Amount" to the sheet.
     */
    private fun writeHeaders(accessToken: String, spreadsheetId: String): Boolean {
        val values = JSONArray().apply {
            put(JSONArray().apply {
                put("Date")
                put("Description")
                put("Category")
                put("Amount")
            })
        }
        return appendRowsRaw(accessToken, spreadsheetId, values)
    }

    /**
     * Appends a single expense to the spreadsheet.
     */
    fun appendExpense(
        accessToken: String,
        spreadsheetId: String,
        dateTimestamp: Long,
        description: String,
        categoryName: String,
        amount: Double
    ): Boolean {
        val row = JSONArray().apply {
            put(formatDate(dateTimestamp))
            put(description)
            put(categoryName)
            put(amount)
        }
        val values = JSONArray().apply {
            put(row)
        }
        return appendRowsRaw(accessToken, spreadsheetId, values)
    }

    /**
     * Appends custom rows.
     */
    private fun appendRowsRaw(accessToken: String, spreadsheetId: String, values: JSONArray): Boolean {
        val payload = JSONObject().apply {
            put("range", "Sheet1!A1")
            put("majorDimension", "ROWS")
            put("values", values)
        }

        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Sheet1!A1:append?valueInputOption=USER_ENTERED"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    return true
                } else {
                    Log.e(TAG, "Append error: Code=${response.code}, Body=$bodyStr")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error appending rows", e)
        }
        return false
    }

    /**
     * Exports multiple expenses in a batch.
     */
    fun exportAll(
        accessToken: String,
        spreadsheetId: String,
        expensesList: List<ExportRow> // Date, Desc, CategoryName, Amount
    ): Boolean {
        // First, write header + all rows
        val values = JSONArray().apply {
            // Headers
            put(JSONArray().apply {
                put("Date")
                put("Description")
                put("Category")
                put("Amount")
            })
            // Rows
            for (item in expensesList) {
                put(JSONArray().apply {
                    put(formatDate(item.date))
                    put(item.description)
                    put(item.categoryName)
                    put(item.amount)
                })
            }
        }

        val payload = JSONObject().apply {
            put("range", "Sheet1!A1")
            put("majorDimension", "ROWS")
            put("values", values)
        }

        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Sheet1!A1?valueInputOption=USER_ENTERED"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .put(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    return true
                } else {
                    Log.e(TAG, "Batch export error: Code=${response.code}, Body=$bodyStr")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting all", e)
        }
        return false
    }
}
