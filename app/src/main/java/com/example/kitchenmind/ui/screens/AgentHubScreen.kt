package com.example.kitchenmind.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.res.Configuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.kitchenmind.ui.mvi.AgentMissingItem
import com.example.kitchenmind.ui.mvi.AgentSuggestMode
import com.example.kitchenmind.data.model.InventoryItem
import com.example.kitchenmind.ui.mvi.InventoryIntent
import com.example.kitchenmind.ui.mvi.InventoryState
import com.example.kitchenmind.ui.mvi.PendingConsumptionLine
import com.example.kitchenmind.ui.mvi.SideEffect
import com.example.kitchenmind.ui.viewmodel.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentHubScreen(
    viewModel: InventoryViewModel,
    onNavigateToOrders: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var draftMessage by rememberSaveable { mutableStateOf("") }
    var showApplyConsumptionDialog by rememberSaveable { mutableStateOf(false) }
    var cookConfirmLines by remember { mutableStateOf<List<PendingConsumptionLine>?>(null) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val canApplyPendingConsumption =
        remember(state.pendingConsumption, state.itemList) {
            pendingConsumptionStockOk(state)
        }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SideEffect.ClearAgentHubInput -> draftMessage = ""
                is SideEffect.ShowCookRecipeConfirmation -> cookConfirmLines = effect.lines
                is SideEffect.NavigateToOrders -> onNavigateToOrders()
                else -> Unit
            }
        }
    }

    fun sendUserMessage() {
        viewModel.handleIntent(InventoryIntent.SubmitAgentHubMessage(draftMessage))
    }

    fun forceRecipeCrew() {
        viewModel.handleIntent(
            InventoryIntent.RequestAgentSuggestion(draftMessage, AgentSuggestMode.RECIPE),
        )
    }

    cookConfirmLines?.let { lines ->
        AlertDialog(
            onDismissRequest = { cookConfirmLines = null },
            title = { Text("Bu tarifi yapıyor musunuz?") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Önerilen tarif için aşağıdaki miktarlar stoktan düşülecek (0 olunca kayıt silinir).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lines.forEach { line ->
                        Text(
                            text = "• ${formatPendingLineForDisplay(line, state.itemList)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cookConfirmLines = null
                        viewModel.handleIntent(InventoryIntent.ApplyPendingConsumptionLines(lines))
                    },
                ) {
                    Text("Evet, stoktan düş")
                }
            },
            dismissButton = {
                TextButton(onClick = { cookConfirmLines = null }) {
                    Text("İptal")
                }
            },
        )
    }

    if (showApplyConsumptionDialog) {
        AlertDialog(
            onDismissRequest = { showApplyConsumptionDialog = false },
            title = { Text("Stoktan düşülsün mü?") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Aşağıdaki miktarlar Room veritabanından düşülecek (0 olunca kayıt silinir).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.pendingConsumption.forEach { line ->
                        Text(
                            text = "• ${formatPendingLineForDisplay(line, state.itemList)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyConsumptionDialog = false
                        viewModel.handleIntent(InventoryIntent.ApplyConsumption)
                    },
                ) {
                    Text("Onayla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConsumptionDialog = false }) {
                    Text("İptal")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mutfak asistanı") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            )
        },
    ) { padding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        AgentHubMessages(state = state, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                        AgentHubMissingItemsCard(
                            items = state.agentMissingItems,
                            onAddToOrderCart = {
                                viewModel.handleIntent(InventoryIntent.AddAgentMissingToOrderCart)
                                onNavigateToOrders()
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AgentHubPendingConsumption(
                            pending = state.pendingConsumption,
                            inventory = state.itemList,
                            enabled = !state.isLoading && canApplyPendingConsumption,
                            onOpenConfirm = { showApplyConsumptionDialog = true },
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    AgentHubExpiredInventoryBanner(
                        items = state.itemList,
                        onDelete = { viewModel.handleIntent(InventoryIntent.DeleteItem(it)) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AgentHubInput(
                        draftMessage = draftMessage,
                        onDraftChange = { draftMessage = it },
                        onSend = { sendUserMessage() },
                        onForceRecipeCrew = { forceRecipeCrew() },
                        onInventoryOnly = {
                            viewModel.handleIntent(InventoryIntent.GetAISuggestion)
                        },
                        enabled = !state.isLoading,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    AgentHubMessages(state = state, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentHubMissingItemsCard(
                        items = state.agentMissingItems,
                        onAddToOrderCart = {
                            viewModel.handleIntent(InventoryIntent.AddAgentMissingToOrderCart)
                            onNavigateToOrders()
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentHubPendingConsumption(
                        pending = state.pendingConsumption,
                        inventory = state.itemList,
                        enabled = !state.isLoading && canApplyPendingConsumption,
                        onOpenConfirm = { showApplyConsumptionDialog = true },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                AgentHubExpiredInventoryBanner(
                    items = state.itemList,
                    onDelete = { viewModel.handleIntent(InventoryIntent.DeleteItem(it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                AgentHubInput(
                    draftMessage = draftMessage,
                    onDraftChange = { draftMessage = it },
                    onSend = { sendUserMessage() },
                    onForceRecipeCrew = { forceRecipeCrew() },
                    onInventoryOnly = {
                        viewModel.handleIntent(InventoryIntent.GetAISuggestion)
                    },
                    enabled = !state.isLoading,
                )
            }
        }
    }
}

@Composable
private fun AgentHubExpiredInventoryBanner(
    items: List<InventoryItem>,
    onDelete: (InventoryItem) -> Unit,
) {
    val expired =
        remember(items) {
            val t = System.currentTimeMillis()
            items
                .filter { it.expiryDate != null && it.expiryDate!! < t }
                .sortedBy { it.expiryDate }
        }
    if (expired.isEmpty()) return
    val signature =
        remember(expired) {
            expired.joinToString("|") { "${it.id}_${it.expiryDate}" }
        }
    var dismissedSignature by remember { mutableStateOf<String?>(null) }
    if (dismissedSignature == signature) return

    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SKT'si geçmiş ürünler",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { dismissedSignature = signature },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Text(
                text = "Güvenli tüketim için önerilmez. Envanterden kaldırdıysanız aşağıdan silebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
            )
            expired.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${item.name} · ${item.quantity} ${item.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        item.expiryDate?.let { ms ->
                            Text(
                                text = "SKT: ${dateFmt.format(Date(ms))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onDelete(item) },
                    ) {
                        Text(
                            text = "Sil",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentHubMessages(
    state: InventoryState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.agentUserMessage?.let { userText ->
            ChatBubble(
                text = userText,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                alignEnd = true,
            )
        }
        state.agentMessage?.let { assistantText ->
            ChatBubble(
                text = assistantText,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                alignEnd = false,
            )
        }
    }
}

@Composable
private fun AgentHubMissingItemsCard(
    items: List<AgentMissingItem>,
    onAddToOrderCart: () -> Unit,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shareText = remember(items) { formatMissingItemsForShare(items) }
    val searchQuery = remember(items) {
        "mutfak alışveriş listesi " + items.joinToString(", ") { m ->
            "${m.name} ${formatQuantityForDisplay(m.suggestedQuantity)} ${m.unit}"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Eksik malzemeler",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            items.forEach { m ->
                Text(
                    text = "• ${m.name}: ${formatQuantityForDisplay(m.suggestedQuantity)} ${m.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Listeyi kopyalayın, paylaşın veya sipariş sepetine aktarın (demo ödeme Asistan → Sipariş).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAddToOrderCart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sipariş sepetine ekle")
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(shareText))
                        Toast.makeText(context, "Panoya kopyalandı", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Panoya kopyala")
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, "Listeyi paylaş"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Paylaş")
                }
                OutlinedButton(
                    onClick = {
                        val url =
                            "https://www.google.com/search?q=" + Uri.encode(searchQuery)
                        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(view)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Google’da ara")
                }
            }
        }
    }
}

private fun formatMissingItemsForShare(items: List<AgentMissingItem>): String = buildString {
    appendLine("KitchenMind — eksik malzemeler listesi")
    items.forEach { m ->
        appendLine("• ${m.name}: ${formatQuantityForDisplay(m.suggestedQuantity)} ${m.unit}")
    }
}.trimEnd()

private fun formatPendingLineForDisplay(
    line: PendingConsumptionLine,
    inventory: List<InventoryItem>,
): String {
    val qty = inventory.find { it.id == line.inventoryItemId }?.quantity ?: 0
    return if (qty >= line.deltaRounded) {
        "${line.name}: −${line.deltaRounded} ${line.unit} → ${qty - line.deltaRounded} kalan"
    } else {
        val needMore = line.deltaRounded - qty
        "${line.name}: tarif ${line.deltaRounded} ${line.unit} istiyor, stokta $qty ($needMore ${line.unit} daha gerekli)"
    }
}

private fun pendingConsumptionStockOk(state: InventoryState): Boolean {
    val pending = state.pendingConsumption
    if (pending.isEmpty()) return false
    val byId = state.itemList.associateBy { it.id }
    return pending.all { line ->
        (byId[line.inventoryItemId]?.quantity ?: 0) >= line.deltaRounded
    }
}

private fun formatQuantityForDisplay(q: Double): String {
    val r = kotlin.math.round(q * 100.0) / 100.0
    val asLong = r.toLong()
    return if (kotlin.math.abs(r - asLong) < 1e-6) {
        asLong.toString()
    } else {
        r.toString()
    }
}

@Composable
private fun AgentHubPendingConsumption(
    pending: List<PendingConsumptionLine>,
    inventory: List<InventoryItem>,
    enabled: Boolean,
    onOpenConfirm: () -> Unit,
) {
    if (pending.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Önerilen tüketim",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            pending.forEach { line ->
                Text(
                    text = formatPendingLineForDisplay(line, inventory),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!enabled) {
                Text(
                    text =
                        "Stoktan düş: her kalemin stoğu, tarifin istediği miktara eşit veya fazla olmalı. " +
                            "Eksik görünüyorsa envantere ekleyin veya tarifi tekrar isteyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onOpenConfirm,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stoktan düş")
            }
        }
    }
}

@Composable
private fun ChatBubble(
    text: String,
    container: Color,
    content: Color,
    alignEnd: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (alignEnd) 0.92f else 1f)
                .background(color = container, shape = RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(text = text, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AgentHubInput(
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onForceRecipeCrew: () -> Unit,
    onInventoryOnly: () -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = draftMessage,
        onValueChange = onDraftChange,
        placeholder = { Text("Örn: Bu akşam ne pişireyim? / Merhaba") },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        minLines = 2,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            onClick = onSend,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Gönder",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        OutlinedButton(
            onClick = onForceRecipeCrew,
            enabled = enabled && draftMessage.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Tarif",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        OutlinedButton(
            onClick = onInventoryOnly,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "SKT",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
