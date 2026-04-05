package com.example.kitchenmind.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kitchenmind.data.catalog.CatalogProduct
import com.example.kitchenmind.data.catalog.KitchenCatalog
import com.example.kitchenmind.data.model.InventoryItem
import com.example.kitchenmind.ui.mvi.InventoryIntent
import com.example.kitchenmind.ui.viewmodel.InventoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    viewModel: InventoryViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<CatalogProduct?>(null) }
    var quantityStr by remember { mutableStateOf("") }
    var expandedUnitMenu by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("adet") }
    val units = listOf(
        "adet", "diş", "demet", "paket",
        "kg", "g", "L", "ml",
        "yemek kaşığı", "çay kaşığı",
    )

    var expandedCategoryMenu by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(0) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")) }

    val filteredCatalog = remember(searchQuery) { KitchenCatalog.filter(searchQuery) }

    val isFormValid =
        selectedProduct != null && (quantityStr.toIntOrNull() ?: 0) > 0

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            viewModel = viewModel,
            onDismiss = { showAddCategoryDialog = false },
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Kapat") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ürün ekle") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Ürünü listeden seçin (serbest yazım yok).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Katalogda ara") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                text = "Eşleşen ürünler",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredCatalog, key = { it.displayName }) { product ->
                    val isSel = selectedProduct?.displayName == product.displayName
                    Card(
                        onClick = {
                            selectedProduct = product
                            selectedUnit = product.defaultUnit
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                        ),
                    ) {
                        Text(
                            text = product.displayName,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            selectedProduct?.let { p ->
                Text(
                    text = "Seçilen: ${p.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: Text(
                text = "Henüz ürün seçilmedi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it.filter { c -> c.isDigit() } },
                    label = { Text("Miktar *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = selectedProduct != null,
                )

                ExposedDropdownMenuBox(
                    expanded = expandedUnitMenu,
                    onExpandedChange = { expandedUnitMenu = !expandedUnitMenu },
                    modifier = Modifier.width(150.dp),
                ) {
                    OutlinedTextField(
                        value = selectedUnit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Birim") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnitMenu)
                        },
                        modifier = Modifier.menuAnchor(),
                        singleLine = true,
                        enabled = selectedProduct != null,
                    )
                    ExposedDropdownMenu(
                        expanded = expandedUnitMenu,
                        onDismissRequest = { expandedUnitMenu = false },
                    ) {
                        units.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = {
                                    selectedUnit = unit
                                    expandedUnitMenu = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = expandedCategoryMenu,
                    onExpandedChange = { expandedCategoryMenu = !expandedCategoryMenu },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = state.categoryList.find { it.id == selectedCategoryId }?.name
                            ?: "Kategorisiz",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryMenu)
                        },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategoryMenu,
                        onDismissRequest = { expandedCategoryMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Kategorisiz") },
                            onClick = {
                                selectedCategoryId = 0
                                expandedCategoryMenu = false
                            },
                        )
                        state.categoryList.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    expandedCategoryMenu = false
                                },
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showAddCategoryDialog = true },
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text("+ Yeni")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Son kullanma (isteğe bağlı)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val dateText = datePickerState.selectedDateMillis
                        ?.let { dateFormatter.format(Date(it)) }
                        ?: "Tarih seç"
                    Text(dateText)
                }
            }

            Button(
                onClick = {
                    val p = selectedProduct ?: return@Button
                    val quantity = quantityStr.toIntOrNull() ?: 1
                    val newItem = InventoryItem(
                        name = p.displayName,
                        quantity = quantity,
                        unit = selectedUnit,
                        expiryDate = datePickerState.selectedDateMillis,
                        categoryId = selectedCategoryId,
                    )
                    viewModel.handleIntent(InventoryIntent.AddItem(newItem))
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isFormValid,
            ) {
                Text("Kaydet")
            }
        }
    }
}
