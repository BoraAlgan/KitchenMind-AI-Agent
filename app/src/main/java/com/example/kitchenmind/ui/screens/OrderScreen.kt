package com.example.kitchenmind.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kitchenmind.ui.mvi.InventoryIntent
import com.example.kitchenmind.ui.mvi.InventoryState
import com.example.kitchenmind.ui.mvi.OrderFlowChatBubble
import com.example.kitchenmind.ui.viewmodel.InventoryViewModel
import kotlin.math.abs
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(viewModel: InventoryViewModel) {
    val state by viewModel.state.collectAsState()
    var showAiDialog by remember { mutableStateOf(false) }

//1. adım
//ekran çıktısı buradan alınıyor
    if (showAiDialog) {
        OrderFlowAiDialog(
            state = state,
            onDismiss = {
                viewModel.handleIntent(InventoryIntent.ResetOrderFlowChat)
                showAiDialog = false
            },
            //2. adım
            //viewmodeldaki sendorderflowmessage kısmına yollanıyor
            onSend = { viewModel.handleIntent(InventoryIntent.SendOrderFlowMessage(it)) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sipariş") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        viewModel.handleIntent(InventoryIntent.ResetOrderFlowChat)
                        showAiDialog = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("AI ile sipariş")
                }
            }

            Text(
                text = "Sepetiniz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Bu ekran demo amaçlıdır: gerçek ödeme yoktur. Onayladığınızda kalemler envantere eklenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.orderCart.isEmpty()) {
                Text(
                    text = "Sepet boş. Asistan sekmesinde «Tarif öner» sonrası eksikleri sepete ekleyebilir veya AI ile sipariş yazabilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.orderCart) { index, line ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = line.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${formatQty(line.quantity)} ${line.unit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.handleIntent(InventoryIntent.RemoveOrderCartLine(index))
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Kaldır",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.handleIntent(InventoryIntent.ClearOrderCart) },
                enabled = state.orderCart.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sepeti temizle")
            }

            Button(
                onClick = { viewModel.handleIntent(InventoryIntent.CompleteDemoOrder) },
                enabled = state.orderCart.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Demo ödemeyi onayla ve envantere ekle")
            }
        }
    }
}

@Composable
private fun OrderFlowAiDialog(
    state: InventoryState,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Sipariş asistanı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Ne almak istediğinizi yazın; onaylayınca ürünler envantere eklenir (demo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.orderFlowChatMessages.isEmpty()) {
                        item {
                            Text(
                                text = "Örnek: «2 kg domates ve süt 1 L» — ardından «evet» ile onaylayın.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        itemsIndexed(
                            state.orderFlowChatMessages,
                            key = { index, bubble -> "$index-${bubble.isUser}-${bubble.text.hashCode()}" },
                        ) { _, bubble ->
                            OrderFlowBubbleRow(bubble = bubble)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        enabled = !state.orderFlowLoading,
                        placeholder = { Text("Mesajınız…") },
                        maxLines = 3,
                    )
                    if (state.orderFlowLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = {
                                val t = input.trim()
                                if (t.isNotEmpty()) {
                                    onSend(t)
                                    input = ""
                                }
                            },
                            enabled = input.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
                        }
                    }
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Kapat")
                }
            }
        }
    }
}

@Composable
private fun OrderFlowBubbleRow(bubble: OrderFlowChatBubble) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (bubble.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (bubble.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = bubble.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatQty(q: Double): String {
    val r = round(q * 100.0) / 100.0
    val asLong = r.toLong()
    return if (abs(r - asLong.toDouble()) < 1e-6) asLong.toString() else r.toString()
}
