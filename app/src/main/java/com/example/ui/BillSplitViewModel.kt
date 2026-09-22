package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemini.GeminiService
import com.example.model.ChatMessage
import com.example.model.MessageRole
import com.example.model.PersonBreakdown
import com.example.model.Receipt
import com.example.model.ReceiptItem
import com.example.model.SampleData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

class BillSplitViewModel : ViewModel() {

    private val geminiService = GeminiService()

    private val _receipt = MutableStateFlow<Receipt>(SampleData.sampleReceipt1)
    val receipt: StateFlow<Receipt> = _receipt.asStateFlow()

    private val _receiptImageUri = MutableStateFlow<Uri?>(null)
    val receiptImageUri: StateFlow<Uri?> = _receiptImageUri.asStateFlow()

    private val _receiptBitmap = MutableStateFlow<Bitmap?>(null)
    val receiptBitmap: StateFlow<Bitmap?> = _receiptBitmap.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                id = "welcome",
                role = MessageRole.AI,
                text = "Hi! I'm your AI bill splitting assistant. Type natural language commands like \"Dhruv had the nachos\", \"Sarah and Sue shared the pizza\", or \"Alex had the burger and beer\" to assign items. I'll split tax & tip proportionally in real-time!",
                actionsSummary = listOf("AI Ready", "Proportional Tax & Tip Enabled")
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAnalyzingReceipt = MutableStateFlow(false)
    val isAnalyzingReceipt: StateFlow<Boolean> = _isAnalyzingReceipt.asStateFlow()

    private val _isChatProcessing = MutableStateFlow(false)
    val isChatProcessing: StateFlow<Boolean> = _isChatProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _tipPercentage = MutableStateFlow(18)
    val tipPercentage: StateFlow<Int> = _tipPercentage.asStateFlow()

    val personBreakdowns: StateFlow<List<PersonBreakdown>> = _receipt.combine(_tipPercentage) { currReceipt, _ ->
        calculatePersonBreakdowns(currReceipt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadSampleReceipt(newReceipt: Receipt) {
        _receipt.value = newReceipt
        _receiptBitmap.value = null
        _receiptImageUri.value = null
        addSystemMessage("Loaded receipt: \"${newReceipt.merchantName}\" with ${newReceipt.items.size} items.")
    }

    fun setReceiptImage(uri: Uri?, bitmap: Bitmap?) {
        _receiptImageUri.value = uri
        _receiptBitmap.value = bitmap
        if (bitmap != null) {
            analyzeReceiptBitmap(bitmap)
        }
    }

    private fun analyzeReceiptBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _isAnalyzingReceipt.value = true
            _errorMessage.value = null
            addSystemMessage("Analyzing receipt image with Gemini Vision...")

            try {
                // Resize if too large to keep under token/memory limits
                val scaled = if (bitmap.width > 1200 || bitmap.height > 1200) {
                    val ratio = 1200f / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt(),
                        (bitmap.height * ratio).toInt(),
                        true
                    )
                } else bitmap

                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val byteArray = stream.toByteArray()
                val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                val result = geminiService.parseReceiptImage(base64, "image/jpeg")
                result.onSuccess { parsedReceipt ->
                    // Set default 18% tip if 0
                    val tip = if (parsedReceipt.tip <= 0.0 && parsedReceipt.subtotal > 0) {
                        (parsedReceipt.subtotal * 0.18)
                    } else parsedReceipt.tip

                    val updatedReceipt = parsedReceipt.copy(
                        tip = tip,
                        total = parsedReceipt.subtotal + parsedReceipt.tax + tip
                    )
                    _receipt.value = updatedReceipt
                    addAIMessage(
                        text = "Parsed receipt from **${updatedReceipt.merchantName}**! Found ${updatedReceipt.items.size} items (Subtotal: $${String.format("%.2f", updatedReceipt.subtotal)}, Tax: $${String.format("%.2f", updatedReceipt.tax)}). Tell me who ordered what!",
                        actions = listOf("Parsed ${updatedReceipt.items.size} items", "Subtotal $${String.format("%.2f", updatedReceipt.subtotal)}")
                    )
                }.onFailure { err ->
                    _errorMessage.value = "Receipt OCR failed: ${err.message}. You can still use sample receipts or edit items."
                    addSystemMessage("Could not parse image: ${err.message}. Using current receipt.")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error analyzing receipt: ${e.message}"
            } finally {
                _isAnalyzingReceipt.value = false
            }
        }
    }

    fun sendChatMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // 1. Add user message to thread
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = trimmed
        )
        _chatMessages.value = _chatMessages.value + userMsg

        // 2. Process command with Gemini
        viewModelScope.launch {
            _isChatProcessing.value = true
            _errorMessage.value = null

            val currReceipt = _receipt.value
            val history = _chatMessages.value

            val result = geminiService.processChatCommand(trimmed, currReceipt, history)
            result.onSuccess { commandResult ->
                applyChatCommand(commandResult)
            }.onFailure { err ->
                addAIMessage("Sorry, I encountered an issue processing that: ${err.message}")
            }

            _isChatProcessing.value = false
        }
    }

    private fun applyChatCommand(commandResult: com.example.gemini.ChatCommandResult) {
        val currentItems = _receipt.value.items.toMutableList()
        val actionsTaken = mutableListOf<String>()

        for (assignment in commandResult.assignments) {
            val matchQuery = assignment.itemMatch.lowercase()
            // Find item by fuzzy match
            val targetIndices = currentItems.indices.filter { idx ->
                val name = currentItems[idx].name.lowercase()
                name.contains(matchQuery) || matchQuery.contains(name) ||
                    name.split(" ", "-", "&").any { matchQuery.contains(it) && it.length > 2 }
            }

            if (targetIndices.isNotEmpty()) {
                for (idx in targetIndices) {
                    val item = currentItems[idx]
                    val updatedPeople = when (assignment.operation.uppercase()) {
                        "ADD" -> (item.assignedTo + assignment.people).distinct()
                        "REMOVE" -> item.assignedTo.filter { it !in assignment.people }
                        else -> assignment.people.distinct() // "SET"
                    }
                    currentItems[idx] = item.copy(assignedTo = updatedPeople)
                    actionsTaken.add(
                        if (updatedPeople.isEmpty()) "Unassigned ${item.name}"
                        else "Assigned ${item.name} -> ${updatedPeople.joinToString(", ")}"
                    )
                }
            }
        }

        var newTip = _receipt.value.tip
        if (commandResult.tipPercent != null) {
            _tipPercentage.value = commandResult.tipPercent.toInt()
            newTip = (_receipt.value.subtotal * (commandResult.tipPercent / 100.0))
            actionsTaken.add("Tip updated to ${commandResult.tipPercent.toInt()}%")
        } else if (commandResult.tipAmount != null) {
            newTip = commandResult.tipAmount
            _tipPercentage.value = if (_receipt.value.subtotal > 0) ((newTip / _receipt.value.subtotal) * 100).toInt() else 18
            actionsTaken.add("Tip set to $${String.format("%.2f", newTip)}")
        }

        _receipt.value = _receipt.value.copy(
            items = currentItems,
            tip = newTip,
            total = _receipt.value.subtotal + _receipt.value.tax + newTip
        )

        val finalActions = if (actionsTaken.isNotEmpty()) actionsTaken else commandResult.actionsSummary
        addAIMessage(
            text = commandResult.explanation,
            actions = finalActions
        )
    }

    fun assignPersonToItem(itemId: String, personName: String) {
        val cleanName = personName.trim()
        if (cleanName.isEmpty()) return

        val currentItems = _receipt.value.items.map { item ->
            if (item.id == itemId) {
                if (item.assignedTo.contains(cleanName)) {
                    item.copy(assignedTo = item.assignedTo - cleanName)
                } else {
                    item.copy(assignedTo = item.assignedTo + cleanName)
                }
            } else item
        }
        _receipt.value = _receipt.value.copy(items = currentItems)
        addSystemMessage("Updated assignment for ${currentItems.find { it.id == itemId }?.name}")
    }

    fun removePersonFromItem(itemId: String, personName: String) {
        val currentItems = _receipt.value.items.map { item ->
            if (item.id == itemId) {
                item.copy(assignedTo = item.assignedTo - personName)
            } else item
        }
        _receipt.value = _receipt.value.copy(items = currentItems)
    }

    fun setTipPercent(percent: Int) {
        _tipPercentage.value = percent
        val newTip = _receipt.value.subtotal * (percent / 100.0)
        _receipt.value = _receipt.value.copy(
            tip = newTip,
            total = _receipt.value.subtotal + _receipt.value.tax + newTip
        )
        addSystemMessage("Tip adjusted to $percent% ($${String.format("%.2f", newTip)})")
    }

    fun setCustomTip(amount: Double) {
        val newTip = maxOf(0.0, amount)
        val calculatedPercent = if (_receipt.value.subtotal > 0) ((newTip / _receipt.value.subtotal) * 100).toInt() else 0
        _tipPercentage.value = calculatedPercent
        _receipt.value = _receipt.value.copy(
            tip = newTip,
            total = _receipt.value.subtotal + _receipt.value.tax + newTip
        )
        addSystemMessage("Tip set to custom amount: $${String.format("%.2f", newTip)}")
    }

    fun resetAssignments() {
        val resetItems = _receipt.value.items.map { it.copy(assignedTo = emptyList()) }
        _receipt.value = _receipt.value.copy(items = resetItems)
        addSystemMessage("All item assignments reset.")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun addAIMessage(text: String, actions: List<String> = emptyList()) {
        val aiMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.AI,
            text = text,
            actionsSummary = actions
        )
        _chatMessages.value = _chatMessages.value + aiMsg
    }

    private fun addSystemMessage(text: String) {
        val sysMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.SYSTEM,
            text = text
        )
        _chatMessages.value = _chatMessages.value + sysMsg
    }

    private fun calculatePersonBreakdowns(currReceipt: Receipt): List<PersonBreakdown> {
        val allPeople = currReceipt.allAssignees
        if (allPeople.isEmpty()) return emptyList()

        // 1. Calculate each person's item subtotal
        val personItemMap = mutableMapOf<String, MutableList<Pair<ReceiptItem, Double>>>()
        for (person in allPeople) {
            personItemMap[person] = mutableListOf()
        }

        for (item in currReceipt.items) {
            if (item.assignedTo.isNotEmpty()) {
                val perPersonShare = item.price / item.assignedTo.size
                for (person in item.assignedTo) {
                    personItemMap[person]?.add(Pair(item, perPersonShare))
                }
            }
        }

        // Sum of all assigned items
        val totalAssignedSubtotal = personItemMap.values.sumOf { itemsList ->
            itemsList.sumOf { it.second }
        }

        return allPeople.map { person ->
            val items = personItemMap[person] ?: emptyList()
            val itemSubtotal = items.sumOf { it.second }
            val ratio = if (totalAssignedSubtotal > 0.0) itemSubtotal / totalAssignedSubtotal else 0.0

            // Proportional tax and tip based on item share
            val taxShare = ratio * currReceipt.tax
            val tipShare = ratio * currReceipt.tip
            val totalOwed = itemSubtotal + taxShare + tipShare

            PersonBreakdown(
                name = person,
                items = items,
                itemsSubtotal = itemSubtotal,
                taxShare = taxShare,
                tipShare = tipShare,
                totalOwed = totalOwed,
                percentageOfAssigned = ratio * 100.0
            )
        }
    }
}
