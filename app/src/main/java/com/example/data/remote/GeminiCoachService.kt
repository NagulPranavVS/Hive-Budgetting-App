package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Category
import com.example.data.model.Expense
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CoachInsights(
    val healthScore: Int,
    val summary: String,
    val financialTips: List<String>,
    val projections: String,
    val quickRecommendations: String
)

data class CoachConversationResponse(
    val answer: String,
    val affordableStatus: String? = null // "YES", "NO", "CAUTION", or null if not applicable
)

object GeminiCoachService {
    private const val TAG = "GeminiCoachService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Helper to read GEMINI_API_KEY from BuildConfig securely
     */
    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read GEMINI_API_KEY from BuildConfig: ${e.message}")
            ""
        }
    }

    /**
     * Generates on-demand smart financial insights and a financial health metrics check
     */
    suspend fun generateFinancialInsights(
        expenses: List<Expense>,
        categories: List<Category>,
        globalBudget: Double
    ): CoachInsights? {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "GEMINI_API_KEY is not configured or holds the default placeholder.")
            return null
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        // Prepare transactional summary payload for the prompt
        val categoriesMap = categories.associateBy { it.id }
        val expensesSummaryStr = expenses.joinToString("\n") { exp ->
            val catName = categoriesMap[exp.categoryId]?.name ?: "Uncategorized"
            "- ${exp.description}: ₹${exp.amount} on ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(exp.date))} (${if (exp.isIncome) "Income" else "Expense"}) - Category: $catName"
        }

        val budgetSummaryStr = categories.filter { !it.isIncome && !it.isSavings && it.monthlyBudget != null }.joinToString("\n") { cat ->
            "- Category '${cat.name}': Limit ₹${cat.monthlyBudget}"
        }

        val totalIncome = expenses.filter { it.isIncome && !it.isSavings }.sumOf { it.amount }
        val totalExpenses = expenses.filter { !it.isIncome && !it.isSavings }.sumOf { it.amount }
        val totalSavings = expenses.filter { it.isSavings }.sumOf { it.amount }

        val systemInstruction = """
            You are an elite, proactive personal financial coach & budget advisor named "Hive Coach".
            Your task is to analyze the user's spending habits, transactions, budgets, and deliver structured, high-impact savings tips, custom recommendations, and a 'Financial Health Score' (0-100).

            Context Info:
            - Monthly Global Budget limit: ₹$globalBudget
            - Total Spent this month: ₹$totalExpenses
            - Total Income this month: ₹$totalIncome
            - Total Savings/Investments this month: ₹$totalSavings
            - Net Monthly Balance: ₹${totalIncome - totalExpenses - totalSavings}

            User's Monthly Category Limits:
            $budgetSummaryStr

            User's Transaction List:
            $expensesSummaryStr

            Calculation Guidelines for Health Score (0 to 100):
            - If expenses exceed income, reduce score significantly.
            - If spending is near or above limits, reduce score.
            - If saving more than 20% of income, reward with high score.
            - If no monthly guidelines are set, assign a dynamic neutral-high score of 70-80 base.

            Return strictly a single, raw, valid JSON object matching this schema exactly:
            {
              "healthScore": 85,
              "summary": "Short 1-sentence macro summary of their financial status.",
              "financialTips": [
                "Tip about specific category limits or spending velocities", 
                "Another tip regarding savings habits"
              ],
              "projections": "A sentence projecting what their balance will be at the end of the month based on current speed.",
              "quickRecommendations": "One clear actionable micro-target (e.g. Set a coffee limit of ₹1000)"
            }
            Do not include any markdown wrappers like ```json or ```, no text before or after, and no chat fluff. Direct pure JSON string only.
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Analyze my transactions for this month and generate my proactive coaching advice.")
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val jsonResponse = JSONObject(bodyStr)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        val contentObject = candidates?.optJSONObject(0)?.optJSONObject("content")
                        val parts = contentObject?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text")?.trim()

                        if (!text.isNullOrEmpty()) {
                            val cleanJson = cleanJsonString(text)
                            val parsedJson = JSONObject(cleanJson)
                            val health = parsedJson.optInt("healthScore", 75)
                            val sum = parsedJson.optString("summary", "Keep tracking your expenses to build strong financial habits.")
                            val projectionsStr = parsedJson.optString("projections", "Keep on saving!")
                            val quickRec = parsedJson.optString("quickRecommendations", "Create distinct budget allowances for high-spend categories.")
                            val tipsArray = parsedJson.optJSONArray("financialTips")
                            val tips = mutableListOf<String>()
                            if (tipsArray != null) {
                                for (i in 0 until tipsArray.length()) {
                                    tips.add(tipsArray.getString(i))
                                }
                            }
                            if (tips.isEmpty()) {
                                tips.add("Nice work tracking! Set up smaller daily goals to optimize balance.")
                            }

                            CoachInsights(
                                healthScore = health,
                                summary = sum,
                                financialTips = tips,
                                projections = projectionsStr,
                                quickRecommendations = quickRec
                            )
                        } else null
                    } else {
                        Log.e(TAG, "API call was not successful: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during insights generation: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Answers custom questions using the user's spending as context
     */
    suspend fun askBudgetCoach(
        questionText: String,
        expenses: List<Expense>,
        categories: List<Category>,
        globalBudget: Double
    ): CoachConversationResponse? {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return null
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val categoriesMap = categories.associateBy { it.id }
        val expensesSummaryStr = expenses.joinToString("\n") { exp ->
            val catName = categoriesMap[exp.categoryId]?.name ?: "Uncategorized"
            "- ${exp.description}: ₹${exp.amount} (${if (exp.isIncome) "Income" else "Expense"}) - Category: $catName"
        }

        val totalIncome = expenses.filter { it.isIncome && !it.isSavings }.sumOf { it.amount }
        val totalExpenses = expenses.filter { !it.isIncome && !it.isSavings }.sumOf { it.amount }
        val totalSavings = expenses.filter { it.isSavings }.sumOf { it.amount }

        val systemInstruction = """
            You are "Hive Coach", the expert personal finance and budget coach.
            Answer their query directly with professional financial tips based on their current spending values:
            - Global Budget: ₹$globalBudget
            - Spent this month: ₹$totalExpenses
            - Income this month: ₹$totalIncome
            - Savings balance: ₹$totalSavings
            - Net Monthly Balance: ₹${totalIncome - totalExpenses - totalSavings}

            Category Spends:
            ${categories.filter { !it.isIncome && !it.isSavings }.joinToString("\n") { cat -> "- ${cat.name}: limit ₹${cat.monthlyBudget ?: 0.0}" }}

            Recent Transactions:
            $expensesSummaryStr

            If the user asks if they can AFFORD something, estimate if the cost fits their active savings or monthly balance. Determine a status of "YES", "NO", or "CAUTION" (if they can buy it but it significantly depletes savings).

            Return strictly a single, raw, valid JSON object matching this schema exactly:
            {
              "answer": "Warm, expert financial feedback to their question. Short and highly actionable (maximum 3 concise sentences). Mention custom calculated figures if relevant.",
              "affordableStatus": "YES" // (or "NO", "CAUTION", or null if the user's question is general and NOT asking whether they can afford a purchase)
            }
            Do not include any markdown wrappers like ```json or ```, no text before or after, and no chat fluff. Direct pure JSON string only.
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", questionText)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val jsonResponse = JSONObject(bodyStr)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        val contentObject = candidates?.optJSONObject(0)?.optJSONObject("content")
                        val parts = contentObject?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text")?.trim()

                        if (!text.isNullOrEmpty()) {
                            val cleanJson = cleanJsonString(text)
                            val parsedJson = JSONObject(cleanJson)
                            val ans = parsedJson.optString("answer", "I didn't quite catch that. Could you please rephrase?")
                            val statusVal = parsedJson.optString("affordableStatus", "null")
                            val status = if (statusVal == "null" || statusVal.isEmpty() || statusVal.contains("null", ignoreCase = true)) null else statusVal

                            CoachConversationResponse(
                                answer = ans,
                                affordableStatus = status
                            )
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Q&A custom chat: ${e.message}", e)
                null
            }
        }
    }

    private fun cleanJsonString(str: String): String {
        return if (str.startsWith("```json")) {
            str.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (str.startsWith("```")) {
            str.substringAfter("```").substringBeforeLast("```").trim()
        } else {
            str
        }
    }
}
