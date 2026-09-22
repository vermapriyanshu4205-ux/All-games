package com.example.model

object SampleData {
    val sampleReceipt1 = Receipt(
        id = "sample_bistro",
        merchantName = "Bistro Bella & Grill",
        date = "Today, 7:45 PM",
        items = listOf(
            ReceiptItem(id = "1", name = "Truffle Nachos Grande", price = 14.50, quantity = 1),
            ReceiptItem(id = "2", name = "Margherita Woodfired Pizza", price = 18.00, quantity = 1),
            ReceiptItem(id = "3", name = "Angus Bacon Burger", price = 16.50, quantity = 1),
            ReceiptItem(id = "4", name = "Caesar Salad with Chicken", price = 12.00, quantity = 1),
            ReceiptItem(id = "5", name = "Craft IPA Beer (Pint)", price = 8.00, quantity = 1),
            ReceiptItem(id = "6", name = "Iced Hibiscus Green Tea", price = 4.50, quantity = 1)
        ),
        subtotal = 73.50,
        tax = 6.06,
        tip = 13.23, // 18%
        total = 92.79
    )

    val sampleReceipt2 = Receipt(
        id = "sample_taqueria",
        merchantName = "Taqueria Del Sol",
        date = "Yesterday, 1:15 PM",
        items = listOf(
            ReceiptItem(id = "t1", name = "Carne Asada Tacos (3pc)", price = 13.50, quantity = 1),
            ReceiptItem(id = "t2", name = "Baja Crispy Fish Tacos", price = 14.00, quantity = 1),
            ReceiptItem(id = "t3", name = "Guacamole & House Chips", price = 9.50, quantity = 1),
            ReceiptItem(id = "t4", name = "Horchata Tradicional", price = 4.50, quantity = 1),
            ReceiptItem(id = "t5", name = "Churros con Cajeta", price = 6.50, quantity = 1)
        ),
        subtotal = 48.00,
        tax = 3.96,
        tip = 8.64,
        total = 60.60
    )

    val sampleReceipt3 = Receipt(
        id = "sample_brunch",
        merchantName = "Sunday Brunch Club",
        date = "Sunday, 11:30 AM",
        items = listOf(
            ReceiptItem(id = "b1", name = "Smoked Salmon Benedict", price = 19.50, quantity = 1),
            ReceiptItem(id = "b2", name = "Brioche French Toast", price = 16.00, quantity = 1),
            ReceiptItem(id = "b3", name = "Smashed Avocado Sourdough", price = 15.00, quantity = 1),
            ReceiptItem(id = "b4", name = "Bottomless Mimosa Pitcher", price = 26.00, quantity = 1),
            ReceiptItem(id = "b5", name = "Organic Acai Super Bowl", price = 14.00, quantity = 1),
            ReceiptItem(id = "b6", name = "Nitro Cold Brew", price = 6.00, quantity = 1)
        ),
        subtotal = 96.50,
        tax = 8.44,
        tip = 17.37,
        total = 122.31
    )
}
