package com.example.kitchenmind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kitchenmind.data.model.Category
import com.example.kitchenmind.data.model.InventoryItem
import com.example.kitchenmind.ui.mvi.InventoryIntent
import com.example.kitchenmind.ui.viewmodel.InventoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: InventoryViewModel,
    onNavigateToAddItem: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            viewModel = viewModel,
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    itemToDelete?.let { item ->
        DeleteConfirmationDialog(
            title = "Delete Item",
            message = "Are you sure you want to delete \"${item.name}\"?",
            onConfirm = {
                viewModel.handleIntent(InventoryIntent.DeleteItem(item))
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    categoryToDelete?.let { category ->
        DeleteConfirmationDialog(
            title = "Delete Category",
            message = "Are you sure you want to delete category \"${category.name}\"? All items in this category will remain but become uncategorized.",
            onConfirm = {
                viewModel.handleIntent(InventoryIntent.DeleteCategory(category))
                categoryToDelete = null
            },
            onDismiss = { categoryToDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kitchen Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Item") },
                                onClick = {
                                    showAddMenu = false
                                    onNavigateToAddItem()
                                },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("New Category") },
                                onClick = {
                                    showAddMenu = false
                                    showAddCategoryDialog = true
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val grouped = state.itemList.groupBy { it.categoryId }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Her zaman "Uncategorized" grubunu en başta göster
                val uncategorizedItems = grouped[0] ?: emptyList()
                if (uncategorizedItems.isNotEmpty() || state.itemList.isEmpty()) {
                    item(key = "header_uncategorized") {
                        CategoryHeader(
                            title = "Uncategorized",
                            onDeleteCategory = null
                        )
                    }
                    
                    if (uncategorizedItems.isEmpty()) {
                        item(key = "empty_uncategorized") {
                            EmptyCategoryPlaceholder()
                        }
                    } else {
                        // Group same-name items together under one card
                        val nameGroupsUncategorized = uncategorizedItems.groupBy { it.name.lowercase() }
                        nameGroupsUncategorized.forEach { (_, groupItems) ->
                            item(key = "group_unc_${groupItems.first().id}") {
                                InventoryItemGroupCard(
                                    items = groupItems,
                                    onDeleteClick = { itemToDelete = it }
                                )
                            }
                        }
                    }
                    item(key = "spacer_uncategorized") { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Diğer tüm kategorileri state listesinden dön
                state.categoryList.forEach { category ->
                    val itemsInCategory = grouped[category.id] ?: emptyList()
                    
                    item(key = "cat_header_${category.id}") {
                        CategoryHeader(
                            title = category.name,
                            onDeleteCategory = { categoryToDelete = category }
                        )
                    }
                    
                    if (itemsInCategory.isEmpty()) {
                        item(key = "empty_cat_${category.id}") {
                            EmptyCategoryPlaceholder()
                        }
                    } else {
                        // Group same-name items together under one card
                        val nameGroups = itemsInCategory.groupBy { it.name.lowercase() }
                        nameGroups.forEach { (_, groupItems) ->
                            item(key = "group_cat_${category.id}_${groupItems.first().id}") {
                                InventoryItemGroupCard(
                                    items = groupItems,
                                    onDeleteClick = { itemToDelete = it }
                                )
                            }
                        }
                    }
                    item(key = "cat_spacer_${category.id}") { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyCategoryPlaceholder() {
    Text(
        text = "No items in this category.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun CategoryHeader(
    title: String,
    onDeleteCategory: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (onDeleteCategory != null) {
            IconButton(
                onClick = onDeleteCategory,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Category",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        thickness = 1.dp
    )
}

/**
 * Shows a group of same-named inventory items as a single card.
 * The item name is shown once as a bold header.
 * Each distinct expiry-date entry is shown as its own row: "Qty: X unit  Exp: date"
 * with its own delete button.
 */
@Composable
fun InventoryItemGroupCard(
    items: List<InventoryItem>,
    onDeleteClick: (InventoryItem) -> Unit
) {
    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val currentDate = System.currentTimeMillis()

    // Sort by expiry date ascending (null / no date goes last)
    val sortedItems = items.sortedWith(compareBy(nullsLast()) { it.expiryDate })

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Item name header — shown only once
            Text(
                text = sortedItems.first().name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            sortedItems.forEach { item ->
                val isExpired = item.expiryDate != null && item.expiryDate < currentDate
                val isExpiringSoon = item.expiryDate != null &&
                        !isExpired &&
                        item.expiryDate - currentDate < 3 * 24 * 60 * 60 * 1000L
                val statusColor = when {
                    isExpired -> MaterialTheme.colorScheme.error
                    isExpiringSoon -> Color(0xFFEF6C00)
                    else -> MaterialTheme.colorScheme.primary
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Qty: ${item.quantity} ${item.unit}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (item.expiryDate != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Exp: ${dateFormatter.format(Date(item.expiryDate))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = { onDeleteClick(item) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
