package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.PersonBreakdown
import com.example.model.Receipt
import com.example.model.ReceiptItem
import com.example.model.SampleData
import com.example.ui.theme.SplitColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiptPane(
    viewModel: BillSplitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val receipt by viewModel.receipt.collectAsState()
    val personBreakdowns by viewModel.personBreakdowns.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingReceipt.collectAsState()
    val receiptBitmap by viewModel.receiptBitmap.collectAsState()
    val tipPercentage by viewModel.tipPercentage.collectAsState()

    var showSampleMenu by remember { mutableStateOf(false) }
    var selectedItemForAssignment by remember { mutableStateOf<ReceiptItem?>(null) }
    var expandedPersonName by remember { mutableStateOf<String?>(null) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    viewModel.setReceiptImage(uri, bitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SplitColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Receipt Top Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SplitColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SplitColors.PrimaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = "Receipt",
                                    tint = SplitColors.Primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = receipt.merchantName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = SplitColors.TextPrimary
                                )
                                Text(
                                    text = if (receipt.date.isNotBlank()) receipt.date else "Scanned Receipt",
                                    fontSize = 12.sp,
                                    color = SplitColors.TextSecondary
                                )
                            }
                        }

                        // Grand total badge
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SplitColors.PrimaryLight
                        ) {
                            Text(
                                text = "$${String.format("%.2f", receipt.total)}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SplitColors.PrimaryDark
                            )
                        }
                    }

                    if (isAnalyzing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing receipt image with Gemini OCR...",
                            fontSize = 12.sp,
                            color = SplitColors.Primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SplitColors.Primary)
                    }

                    // Action buttons: Upload Photo & Samples
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Upload",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Upload Receipt", fontSize = 12.sp)
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showSampleMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "Sample Receipts ▾", fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = showSampleMenu,
                                onDismissRequest = { showSampleMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bistro Bella & Grill ($92.79)") },
                                    onClick = {
                                        viewModel.loadSampleReceipt(SampleData.sampleReceipt1)
                                        showSampleMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Taqueria Del Sol ($60.60)") },
                                    onClick = {
                                        viewModel.loadSampleReceipt(SampleData.sampleReceipt2)
                                        showSampleMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sunday Brunch Club ($122.31)") },
                                    onClick = {
                                        viewModel.loadSampleReceipt(SampleData.sampleReceipt3)
                                        showSampleMenu = false
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.resetAssignments() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset assignments",
                                tint = SplitColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 2. Receipt Items Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Receipt Items (${receipt.items.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = SplitColors.TextPrimary
                )
                val unassignedCount = receipt.unassignedItems.size
                if (unassignedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SplitColors.WarningLight
                    ) {
                        Text(
                            text = "$unassignedCount unassigned",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SplitColors.Warning
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SplitColors.SuccessLight
                    ) {
                        Text(
                            text = "All assigned ✓",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SplitColors.Success
                        )
                    }
                }
            }
        }

        // Item Cards
        items(receipt.items, key = { it.id }) { item ->
            ReceiptItemCard(
                item = item,
                onClick = { selectedItemForAssignment = item },
                onRemovePerson = { person -> viewModel.removePersonFromItem(item.id, person) }
            )
        }

        // 3. Subtotal, Tax, Tip & Total Breakdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SplitColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Bill Breakdown & Tip",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SplitColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal", color = SplitColors.TextSecondary, fontSize = 13.sp)
                        Text("$${String.format("%.2f", receipt.subtotal)}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tax", color = SplitColors.TextSecondary, fontSize = 13.sp)
                        Text("$${String.format("%.2f", receipt.tax)}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Tip selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tip (${tipPercentage}%)", color = SplitColors.TextSecondary, fontSize = 13.sp)
                        Text("$${String.format("%.2f", receipt.tip)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15, 18, 20, 25).forEach { pct ->
                            FilterChip(
                                selected = tipPercentage == pct,
                                onClick = { viewModel.setTipPercent(pct) },
                                label = { Text("$pct%", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SplitColors.Primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SplitColors.Border)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total to Split", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SplitColors.TextPrimary)
                        Text(
                            "$${String.format("%.2f", receipt.total)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = SplitColors.PrimaryDark
                        )
                    }
                }
            }
        }

        // 4. Real-Time Per-Person Owed Summary Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SplitColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Real-Time Split Summary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SplitColors.TextPrimary
                            )
                            Text(
                                text = "Tax & tip distributed proportionally",
                                fontSize = 12.sp,
                                color = SplitColors.TextSecondary
                            )
                        }

                        if (personBreakdowns.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val summaryText = buildCopyableSummary(receipt, personBreakdowns)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Bill Split Summary", summaryText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Summary copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy summary",
                                    tint = SplitColors.Primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (personBreakdowns.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = SplitColors.SurfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No one assigned yet!",
                                    fontWeight = FontWeight.SemiBold,
                                    color = SplitColors.TextPrimary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Use the AI Chat on the right to assign items (e.g. \"Dhruv had the nachos\"), or tap any item above.",
                                    fontSize = 12.sp,
                                    color = SplitColors.TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        // List of person breakdowns
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            personBreakdowns.forEach { breakdown ->
                                val isExpanded = expandedPersonName == breakdown.name
                                PersonSummaryCard(
                                    breakdown = breakdown,
                                    isExpanded = isExpanded,
                                    onToggleExpand = {
                                        expandedPersonName = if (isExpanded) null else breakdown.name
                                    }
                                )
                            }
                        }

                        // Unassigned warning indicator
                        val unassignedSum = receipt.unassignedItems.sumOf { it.price }
                        if (unassignedSum > 0.0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = SplitColors.WarningLight
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚠️ $${String.format("%.2f", unassignedSum)} of items still unassigned. Total above represents assigned shares.",
                                        fontSize = 12.sp,
                                        color = SplitColors.Warning,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Assign Dialog when tapping an item
    if (selectedItemForAssignment != null) {
        val currentItem = selectedItemForAssignment!!
        var newPersonInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedItemForAssignment = null },
            title = {
                Text(
                    text = "Assign: ${currentItem.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Price: $${String.format("%.2f", currentItem.price)}",
                        color = SplitColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Current Assignees:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = SplitColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (currentItem.assignedTo.isEmpty()) {
                        Text(
                            text = "None (Item is unassigned)",
                            fontSize = 12.sp,
                            color = SplitColors.TextMuted
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentItem.assignedTo.forEach { person ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SplitColors.getAvatarColor(person).copy(alpha = 0.15f),
                                    modifier = Modifier.clickable {
                                        viewModel.removePersonFromItem(currentItem.id, person)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = person,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SplitColors.getAvatarColor(person)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(12.dp),
                                            tint = SplitColors.getAvatarColor(person)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Quick Add Existing Person:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = SplitColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val allKnown = receipt.allAssignees
                    if (allKnown.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allKnown.forEach { person ->
                                val isSelected = currentItem.assignedTo.contains(person)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.assignPersonToItem(currentItem.id, person)
                                    },
                                    label = { Text(person, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPersonInput,
                        onValueChange = { newPersonInput = it },
                        placeholder = { Text("Type new person name...", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPersonInput.isNotBlank()) {
                            viewModel.assignPersonToItem(currentItem.id, newPersonInput.trim())
                        }
                        selectedItemForAssignment = null
                    }
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForAssignment = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiptItemCard(
    item: ReceiptItem,
    onClick: () -> Unit,
    onRemovePerson: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SplitColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SplitColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = SplitColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$${String.format("%.2f", item.price)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = SplitColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Assigned people badges
            if (item.assignedTo.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SplitColors.SurfaceVariant
                    ) {
                        Text(
                            text = "Unassigned • Tap to assign",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = SplitColors.TextMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Assign",
                        tint = SplitColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item.assignedTo.forEach { person ->
                            val color = SplitColors.getAvatarColor(person)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = color.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = person.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = person,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = color
                                    )
                                }
                            }
                        }
                    }

                    if (item.assignedTo.size > 1) {
                        Text(
                            text = "($${String.format("%.2f", item.costPerPerson)} ea)",
                            fontSize = 11.sp,
                            color = SplitColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PersonSummaryCard(
    breakdown: PersonBreakdown,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val color = SplitColors.getAvatarColor(breakdown.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SplitColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = breakdown.name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = breakdown.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SplitColors.TextPrimary
                        )
                        Text(
                            text = "${breakdown.items.size} item${if (breakdown.items.size != 1) "s" else ""}",
                            fontSize = 11.sp,
                            color = SplitColors.TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$${String.format("%.2f", breakdown.totalOwed)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = SplitColors.PrimaryDark
                        )
                        Text(
                            text = "${String.format("%.1f", breakdown.percentageOfAssigned)}% of bill",
                            fontSize = 10.sp,
                            color = SplitColors.TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = SplitColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = SplitColors.Border)
                    Spacer(modifier = Modifier.height(8.dp))

                    breakdown.items.forEach { (item, share) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${item.name}${if (item.assignedTo.size > 1) " (1/${item.assignedTo.size})" else ""}",
                                fontSize = 12.sp,
                                color = SplitColors.TextSecondary
                            )
                            Text(
                                text = "$${String.format("%.2f", share)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SplitColors.TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Items Subtotal:", fontSize = 11.sp, color = SplitColors.TextMuted)
                        Text("$${String.format("%.2f", breakdown.itemsSubtotal)}", fontSize = 11.sp, color = SplitColors.TextMuted)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Proportional Tax Share:", fontSize = 11.sp, color = SplitColors.TextMuted)
                        Text("+$${String.format("%.2f", breakdown.taxShare)}", fontSize = 11.sp, color = SplitColors.TextMuted)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Proportional Tip Share:", fontSize = 11.sp, color = SplitColors.TextMuted)
                        Text("+$${String.format("%.2f", breakdown.tipShare)}", fontSize = 11.sp, color = SplitColors.TextMuted)
                    }
                }
            }
        }
    }
}

private fun buildCopyableSummary(receipt: Receipt, breakdowns: List<PersonBreakdown>): String {
    val sb = StringBuilder()
    sb.appendLine("🧾 Bill Split for ${receipt.merchantName}")
    sb.appendLine("Total Bill: $${String.format("%.2f", receipt.total)} (Subtotal: $${String.format("%.2f", receipt.subtotal)} + Tax: $${String.format("%.2f", receipt.tax)} + Tip: $${String.format("%.2f", receipt.tip)})")
    sb.appendLine("-----------------------------")
    for (b in breakdowns) {
        sb.appendLine("👤 ${b.name}: $${String.format("%.2f", b.totalOwed)}")
        sb.appendLine("   Items ($${String.format("%.2f", b.itemsSubtotal)}): ${b.items.joinToString(", ") { it.first.name }}")
        sb.appendLine("   Tax: $${String.format("%.2f", b.taxShare)} | Tip: $${String.format("%.2f", b.tipShare)}")
    }
    sb.appendLine("-----------------------------")
    sb.appendLine("Generated with Bill Splitter AI")
    return sb.toString()
}
