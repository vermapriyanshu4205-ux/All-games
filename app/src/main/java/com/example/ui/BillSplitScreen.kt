package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SplitColors

enum class ScreenLayoutMode {
    SPLIT, RECEIPT, CHAT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillSplitScreen(
    viewModel: BillSplitViewModel = remember { BillSplitViewModel() }
) {
    val errorMessage by viewModel.errorMessage.collectAsState()
    var layoutMode by remember { mutableStateOf(ScreenLayoutMode.SPLIT) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SplitColors.Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Bill Splitter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = SplitColors.TextPrimary
                            )
                            Text(
                                text = "AI Receipt Vision & Smart Chat",
                                fontSize = 10.sp,
                                color = SplitColors.TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SplitColors.Surface
                ),
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SplitColors.SuccessLight,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = "AI Active",
                                tint = SplitColors.Success,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Gemini AI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SplitColors.Success
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SplitColors.Background)
        ) {
            // Error banner if any
            AnimatedVisibility(visible = errorMessage != null) {
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SplitColors.WarningLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = SplitColors.Warning,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = SplitColors.Warning,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Adaptive layout based on screen width
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 768.dp

                if (isWideScreen) {
                    // True Split Screen side-by-side for wide screens / tablets / desktop iframe
                    Row(modifier = Modifier.fillMaxSize()) {
                        ReceiptPane(
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        VerticalDivider(
                            color = SplitColors.Border,
                            thickness = 1.dp
                        )
                        ChatPane(
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    // Narrower screens: top selector tabs with Split, Receipt, or Chat view
                    Column(modifier = Modifier.fillMaxSize()) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            SegmentedButton(
                                selected = layoutMode == ScreenLayoutMode.SPLIT,
                                onClick = { layoutMode = ScreenLayoutMode.SPLIT },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                label = { Text("Split View", fontSize = 12.sp) }
                            )
                            SegmentedButton(
                                selected = layoutMode == ScreenLayoutMode.RECEIPT,
                                onClick = { layoutMode = ScreenLayoutMode.RECEIPT },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                label = { Text("Receipt", fontSize = 12.sp) }
                            )
                            SegmentedButton(
                                selected = layoutMode == ScreenLayoutMode.CHAT,
                                onClick = { layoutMode = ScreenLayoutMode.CHAT },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                label = { Text("AI Chat", fontSize = 12.sp) }
                            )
                        }

                        when (layoutMode) {
                            ScreenLayoutMode.SPLIT -> {
                                // Side-by-side on mobile or vertical split
                                Row(modifier = Modifier.fillMaxSize()) {
                                    ReceiptPane(
                                        viewModel = viewModel,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                    VerticalDivider(color = SplitColors.Border)
                                    ChatPane(
                                        viewModel = viewModel,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                            ScreenLayoutMode.RECEIPT -> {
                                ReceiptPane(
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            ScreenLayoutMode.CHAT -> {
                                ChatPane(
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
