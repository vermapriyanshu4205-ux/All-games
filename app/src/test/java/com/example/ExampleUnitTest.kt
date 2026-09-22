package com.example

import com.example.model.Receipt
import com.example.model.ReceiptItem
import com.example.ui.BillSplitViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testProportionalTaxAndTipDistribution() {
    // 2 items: Nachos ($10) to Dhruv, Pizza ($20) split by Sarah and Sue ($10 each)
    val item1 = ReceiptItem(id = "1", name = "Nachos", price = 10.0, assignedTo = listOf("Dhruv"))
    val item2 = ReceiptItem(id = "2", name = "Pizza", price = 20.0, assignedTo = listOf("Sarah", "Sue"))

    val receipt = Receipt(
      merchantName = "Test Cafe",
      items = listOf(item1, item2),
      subtotal = 30.0,
      tax = 3.0,  // 10%
      tip = 6.0,  // 20%
      total = 39.0
    )

    // Each person has $10 in item subtotal ($10 + $10 + $10 = $30 total assigned)
    // Therefore each person gets exactly 1/3 of the tax ($1.00) and 1/3 of the tip ($2.00)
    // Total owed by each person = $10 + $1.00 + $2.00 = $13.00
    // Sum = $13 * 3 = $39.00 exactly matching total!
    val allPeople = receipt.allAssignees
    assertEquals(listOf("Dhruv", "Sarah", "Sue"), allPeople)

    val personItemMap = mutableMapOf<String, Double>()
    for (item in receipt.items) {
      val share = item.price / item.assignedTo.size
      for (p in item.assignedTo) {
        personItemMap[p] = (personItemMap[p] ?: 0.0) + share
      }
    }

    val totalAssigned = personItemMap.values.sum()
    assertEquals(30.0, totalAssigned, 0.001)

    var sumTotalOwed = 0.0
    for ((person, subtotal) in personItemMap) {
      val ratio = subtotal / totalAssigned
      val taxShare = ratio * receipt.tax
      val tipShare = ratio * receipt.tip
      val totalOwed = subtotal + taxShare + tipShare

      assertEquals(10.0, subtotal, 0.001)
      assertEquals(1.0, taxShare, 0.001)
      assertEquals(2.0, tipShare, 0.001)
      assertEquals(13.0, totalOwed, 0.001)
      sumTotalOwed += totalOwed
    }

    assertEquals(39.0, sumTotalOwed, 0.001)
  }
}
