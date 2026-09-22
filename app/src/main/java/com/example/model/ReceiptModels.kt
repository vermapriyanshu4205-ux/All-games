package com.example.model

data class ReceiptItem(
    val id: String,
    val name: String,
    val price: Double,
    val quantity: Int = 1,
    val assignedTo: List<String> = emptyList()
) {
    val costPerPerson: Double
        get() = if (assignedTo.isNotEmpty()) price / assignedTo.size else price
}

data class Receipt(
    val id: String = System.currentTimeMillis().toString(),
    val merchantName: String,
    val date: String = "",
    val items: List<ReceiptItem>,
    val subtotal: Double,
    val tax: Double,
    val tip: Double = 0.0,
    val total: Double
) {
    val itemsSum: Double
        get() = items.sumOf { it.price }

    val calculatedTotal: Double
        get() = subtotal + tax + tip

    val unassignedItems: List<ReceiptItem>
        get() = items.filter { it.assignedTo.isEmpty() }

    val allAssignees: List<String>
        get() = items.flatMap { it.assignedTo }.distinct().sorted()
}

data class PersonBreakdown(
    val name: String,
    val items: List<Pair<ReceiptItem, Double>>, // Item and individual share
    val itemsSubtotal: Double,
    val taxShare: Double,
    val tipShare: Double,
    val totalOwed: Double,
    val percentageOfAssigned: Double
)

enum class MessageRole {
    USER, AI, SYSTEM
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionsSummary: List<String> = emptyList(),
    val isPending: Boolean = false
)
