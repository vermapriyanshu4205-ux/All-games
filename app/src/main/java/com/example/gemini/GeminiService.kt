package com.example.gemini

import com.example.BuildConfig
import com.example.model.ChatMessage
import com.example.model.MessageRole
import com.example.model.Receipt
import com.example.model.ReceiptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AssignmentAction(
    val itemMatch: String,
    val people: List<String>,
    val operation: String // "SET", "ADD", "REMOVE"
)

data class ChatCommandResult(
    val explanation: String,
    val assignments: List<AssignmentAction> = emptyList(),
    val tipAmount: Double? = null,
    val tipPercent: Double? = null,
    val actionsSummary: List<String> = emptyList()
)

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val primaryModel = "gemini-3.5-flash"
    private val fallbackModel = "gemini-3.8-flash"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun parseReceiptImage(base64Image: String, mimeType: String = "image/jpeg"): Result<Receipt> =
        withContext(Dispatchers.IO) {
            val prompt = """
                You are an expert receipt OCR analyzer.
                Analyze this receipt image and extract all purchased items, prices, subtotal, tax, tip (if present), and total.
                Return ONLY a JSON object formatted strictly like this:
                {
                  "merchantName": "Restaurant or Store Name",
                  "date": "Date if shown, otherwise empty",
                  "items": [
                    {
                      "name": "Item name",
                      "price": 12.50,
                      "quantity": 1
                    }
                  ],
                  "subtotal": 50.00,
                  "tax": 4.12,
                  "tip": 0.00,
                  "total": 54.12
                }
                Important rules:
                - Extract individual line items only. Do not put subtotal or tax into the items array.
                - All numeric values must be numbers, not strings, without dollar signs.
                - If tip is not written on the receipt, set tip to 0.0.
                - If total doesn't match subtotal + tax, use the printed total.
                - Output raw JSON only with no markdown wrapping.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            // Image part
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Image)
                                })
                            })
                            // Text part
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contents)
            }

            val responseText = executeGeminiCallWithFallback(requestBodyJson.toString())
                ?: return@withContext Result.failure(Exception("Failed to analyze receipt image with Gemini"))

            try {
                val cleanJson = cleanJsonString(responseText)
                val json = JSONObject(cleanJson)

                val merchant = json.optString("merchantName", "Scanned Receipt")
                val date = json.optString("date", "")
                val subtotal = json.optDouble("subtotal", 0.0)
                val tax = json.optDouble("tax", 0.0)
                val tip = json.optDouble("tip", 0.0)
                val total = json.optDouble("total", subtotal + tax + tip)

                val itemsArray = json.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<ReceiptItem>()
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val name = itemObj.optString("name", "Item ${i + 1}")
                    val price = itemObj.optDouble("price", 0.0)
                    val qty = itemObj.optInt("quantity", 1)
                    items.add(
                        ReceiptItem(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            price = price,
                            quantity = qty
                        )
                    )
                }

                val calculatedSubtotal = if (subtotal > 0.0) subtotal else items.sumOf { it.price }
                val calculatedTotal = if (total > 0.0) total else calculatedSubtotal + tax + tip

                val parsedReceipt = Receipt(
                    id = UUID.randomUUID().toString(),
                    merchantName = merchant,
                    date = date,
                    items = items,
                    subtotal = calculatedSubtotal,
                    tax = tax,
                    tip = tip,
                    total = calculatedTotal
                )

                Result.success(parsedReceipt)
            } catch (e: Exception) {
                Result.failure(Exception("Could not parse receipt structure: ${e.message}"))
            }
        }

    suspend fun processChatCommand(
        userMessage: String,
        currentReceipt: Receipt,
        conversationHistory: List<ChatMessage>
    ): Result<ChatCommandResult> = withContext(Dispatchers.IO) {
        val currentItemsSummary = currentReceipt.items.joinToString("\n") { item ->
            val assignees = if (item.assignedTo.isEmpty()) "Unassigned" else item.assignedTo.joinToString(", ")
            "- \"${item.name}\" (Price: $${String.format("%.2f", item.price)}, Assigned: $assignees)"
        }

        val systemInstructionText = """
            You are an intelligent bill splitting assistant. You help dining groups split restaurant receipts using natural language.
            
            Current Receipt Information:
            Merchant: "${currentReceipt.merchantName}"
            Subtotal: $${String.format("%.2f", currentReceipt.subtotal)}
            Tax: $${String.format("%.2f", currentReceipt.tax)}
            Tip: $${String.format("%.2f", currentReceipt.tip)}
            Items:
            $currentItemsSummary
            
            Current Known People: ${currentReceipt.allAssignees.joinToString(", ").ifEmpty { "None yet" }}
            
            Your job is to interpret user commands and assign items to people, update tax/tip, or answer bill questions.
            Examples of commands:
            - "Dhruv had the nachos" -> matches item like "Truffle Nachos Grande", assigns Dhruv.
            - "Sarah and Sue shared the pizza" -> matches item like "Margherita Pizza", assigns Sarah and Sue (SET or ADD).
            - "Alex had the burger and beer" -> matches burger and beer items, assigns Alex to both.
            - "Remove Dhruv from nachos" -> removes Dhruv from nachos.
            - "Split tip 20%" -> updates tipPercent to 20.
            - "Add $15 tip" -> updates tipAmount to 15.00.
            - "Who owes what?" -> provide a friendly breakdown summary in the explanation.
            
            You MUST return ONLY a JSON object:
            {
              "explanation": "Friendly conversational message to display to the user explaining what was assigned or answering their question",
              "assignments": [
                {
                  "itemMatch": "item name or substring from the items list",
                  "people": ["Person1", "Person2"],
                  "operation": "SET" | "ADD" | "REMOVE"
                }
              ],
              "tipAmount": null,
              "tipPercent": null,
              "actionsSummary": [
                "Assigned Truffle Nachos to Dhruv",
                "Split Pizza between Sarah and Sue"
              ]
            }
            
            Rules:
            1. "operation":
               - "SET": Replaces all assignees of that item with the specified people.
               - "ADD": Keeps existing assignees and adds the new people.
               - "REMOVE": Removes the specified people from that item.
            2. Match items fuzzily and intelligently (e.g., 'nachos' matches any item containing 'nacho', 'pizza' matches any pizza, etc.).
            3. Cleanly capitalize person names (e.g. 'Dhruv', 'Sarah', 'Alex').
            4. If the user only asks a question (like "What is the total?" or "How much does Sarah owe?"), return empty assignments array and provide the answer in "explanation".
            5. Return raw JSON ONLY, no surrounding markdown, no backticks.
        """.trimIndent()

        // Build Gemini request payload
        val contentsArray = JSONArray()

        // Include recent history (up to last 6 messages) for multi-turn context
        val recentHistory = conversationHistory.takeLast(6)
        for (msg in recentHistory) {
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", msg.text) })
                })
            })
        }

        // Add the current user message
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", userMessage) })
            })
        })

        val requestBodyJson = JSONObject().apply {
            put("contents", contentsArray)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstructionText) })
                })
            })
        }

        val responseText = executeGeminiCallWithFallback(requestBodyJson.toString())

        if (responseText != null) {
            try {
                val cleanJson = cleanJsonString(responseText)
                val json = JSONObject(cleanJson)

                val explanation = json.optString("explanation", "Updated bill assignments.")
                val assignmentsJson = json.optJSONArray("assignments") ?: JSONArray()
                val assignmentsList = mutableListOf<AssignmentAction>()

                for (i in 0 until assignmentsJson.length()) {
                    val aObj = assignmentsJson.getJSONObject(i)
                    val itemMatch = aObj.optString("itemMatch", "")
                    val op = aObj.optString("operation", "SET")
                    val pArray = aObj.optJSONArray("people") ?: JSONArray()
                    val people = mutableListOf<String>()
                    for (p in 0 until pArray.length()) {
                        val name = pArray.getString(p).trim()
                        if (name.isNotEmpty()) people.add(name)
                    }
                    if (itemMatch.isNotEmpty() && people.isNotEmpty()) {
                        assignmentsList.add(AssignmentAction(itemMatch, people, op))
                    }
                }

                val tipAmount = if (json.has("tipAmount") && !json.isNull("tipAmount")) json.getDouble("tipAmount") else null
                val tipPercent = if (json.has("tipPercent") && !json.isNull("tipPercent")) json.getDouble("tipPercent") else null

                val actionsArray = json.optJSONArray("actionsSummary") ?: JSONArray()
                val actionsList = mutableListOf<String>()
                for (a in 0 until actionsArray.length()) {
                    actionsList.add(actionsArray.getString(a))
                }

                return@withContext Result.success(
                    ChatCommandResult(
                        explanation = explanation,
                        assignments = assignmentsList,
                        tipAmount = tipAmount,
                        tipPercent = tipPercent,
                        actionsSummary = actionsList
                    )
                )
            } catch (e: Exception) {
                // Fall back to rule-based parser below
            }
        }

        // Fallback local rule-based parser in case of network issue
        val localResult = fallbackRuleBasedChatParser(userMessage, currentReceipt)
        Result.success(localResult)
    }

    private fun executeGeminiCallWithFallback(requestBody: String): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return null

        // Try primary model
        val res1 = callGeminiApi(primaryModel, apiKey, requestBody)
        if (res1 != null) return res1

        // Fallback to secondary model
        return callGeminiApi(fallbackModel, apiKey, requestBody)
    }

    private fun callGeminiApi(model: String, apiKey: String, requestBody: String): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "aistudio-build")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                val candidates = root.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content") ?: return null
                val parts = content.optJSONArray("parts") ?: return null
                if (parts.length() == 0) return null
                parts.getJSONObject(0).optString("text")
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        return str.trim()
    }

    /**
     * Fallback smart rule-based parser for offline / instant handling:
     * Handles common commands like:
     * - "Dhruv had the nachos"
     * - "Sarah and Sue shared the pizza"
     * - "Alex had the burger and beer"
     * - "Tip 20%"
     */
    private fun fallbackRuleBasedChatParser(userMessage: String, receipt: Receipt): ChatCommandResult {
        val msg = userMessage.trim().lowercase()
        val assignments = mutableListOf<AssignmentAction>()
        val actions = mutableListOf<String>()

        // Tip check
        val tipPercentRegex = Regex("""(?:tip|leave)\s+(\d{1,2})%""")
        val tipPercentMatch = tipPercentRegex.find(msg)
        var tipPercent: Double? = null
        if (tipPercentMatch != null) {
            tipPercent = tipPercentMatch.groupValues[1].toDoubleOrNull()
        }

        val tipDollarRegex = Regex("""(?:tip|add)\s+\$?(\d+(?:\.\d{1,2})?)\s*(?:tip|dollar)?""")
        val tipDollarMatch = tipDollarRegex.find(msg)
        var tipAmount: Double? = null
        if (tipDollarMatch != null && tipPercent == null) {
            tipAmount = tipDollarMatch.groupValues[1].toDoubleOrNull()
        }

        // Match items with people
        for (item in receipt.items) {
            val itemNameLower = item.name.lowercase()
            val keywords = itemNameLower.split(" ", "-", "&", "/", "(", ")").filter { it.length > 2 && it !in listOf("the", "with", "and", "for") }
            val matchedKeyword = keywords.find { msg.contains(it) }

            if (matchedKeyword != null || msg.contains(itemNameLower)) {
                // Extract people from the message
                val people = extractPeopleNames(userMessage, item.name)
                if (people.isNotEmpty()) {
                    val isShared = msg.contains("shared") || msg.contains("split") || people.size > 1
                    val op = if (isShared) "SET" else "SET"
                    assignments.add(AssignmentAction(item.name, people, op))
                    val peopleNames = people.joinToString(" and ")
                    actions.add("Assigned ${item.name} to $peopleNames")
                }
            }
        }

        val explanation = when {
            assignments.isNotEmpty() && tipPercent != null ->
                "Assigned ${assignments.size} items and updated tip to ${tipPercent.toInt()}%."
            assignments.isNotEmpty() ->
                actions.joinToString(". ") + "."
            tipPercent != null ->
                "Updated tip to ${tipPercent.toInt()}%."
            tipAmount != null ->
                "Updated tip to $${String.format("%.2f", tipAmount)}."
            msg.contains("who owes") || msg.contains("summary") ->
                "Check the summary breakdown card on the left pane to view everyone's balance."
            else ->
                "I noted: \"$userMessage\". Try typing who had which item, e.g. \"Dhruv had the nachos\"."
        }

        return ChatCommandResult(
            explanation = explanation,
            assignments = assignments,
            tipAmount = tipAmount,
            tipPercent = tipPercent,
            actionsSummary = actions
        )
    }

    private fun extractPeopleNames(message: String, matchedItemName: String): List<String> {
        val cleanMsg = message.replace(Regex("""(?i)\b(had|ate|drank|ordered|shared|split|the|and|with|for|a|an)\b"""), " ")
            .replace(matchedItemName, "", ignoreCase = true)

        val words = cleanMsg.split(Regex("""[\s,]+""")).filter { it.isNotBlank() }
        val names = mutableListOf<String>()

        for (word in words) {
            val cleaned = word.filter { it.isLetter() }
            if (cleaned.length >= 2 && cleaned[0].isUpperCase()) {
                names.add(cleaned)
            } else if (cleaned.length >= 3 && cleaned.lowercase() !in listOf("nachos", "pizza", "burger", "beer", "salad", "tacos", "chips")) {
                names.add(cleaned.replaceFirstChar { it.uppercase() })
            }
        }
        return names.distinct()
    }
}
