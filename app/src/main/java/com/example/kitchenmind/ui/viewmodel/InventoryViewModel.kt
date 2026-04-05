package com.example.kitchenmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kitchenmind.data.model.InventoryItem
import com.example.kitchenmind.data.remote.AgentMessageCleaner
import com.example.kitchenmind.data.remote.AgentRepository
import com.example.kitchenmind.data.remote.dto.InventoryItemRequestDto
import com.example.kitchenmind.data.remote.dto.MissingItemDto
import com.example.kitchenmind.data.remote.dto.SuggestRequestDto
import com.example.kitchenmind.data.repository.InventoryRepository
import com.example.kitchenmind.ui.mvi.AgentSuggestMode
import com.example.kitchenmind.ui.mvi.InventoryIntent
import com.example.kitchenmind.ui.mvi.InventoryState
import com.example.kitchenmind.ui.mvi.AgentMissingItem
import com.example.kitchenmind.ui.mvi.OrderCartLine
import com.example.kitchenmind.ui.mvi.PendingConsumptionLine
import com.example.kitchenmind.ui.mvi.RecipeConsumptionRef
import com.example.kitchenmind.ui.mvi.SideEffect
import com.example.kitchenmind.util.EstimatedShelfLife
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

class InventoryViewModel(
    private val repository: InventoryRepository,
    private val agentRepository: AgentRepository,
) : ViewModel() {

    companion object {
        /** Stok düşümü başarılı; LLM çağrılmadan asistan balonuna eklenir (akışı bozmaz). */
        private const val POST_COOK_ASSISTANT_SUFFIX =
            "\n\n—\nAfiyet olsun! 🙂\n\nBaşka bir yemek önerisi ister misin? " +
                "«Bu akşam ne pişireyim?» yazabilir veya alttaki Tarif düğmesine basabilirsin."
    }

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SideEffect>(extraBufferCapacity = 64)
    val effect = _effect.asSharedFlow()

    init {
        handleIntent(InventoryIntent.LoadItems)
        handleIntent(InventoryIntent.LoadCategories)
    }

    fun handleIntent(intent: InventoryIntent) {
        when (intent) {
            is InventoryIntent.LoadItems -> loadItems()
            is InventoryIntent.LoadCategories -> loadCategories()
            is InventoryIntent.AddItem -> addItem(intent)
            is InventoryIntent.DeleteItem -> deleteItem(intent)
            is InventoryIntent.AddCategory -> addCategory(intent)
            is InventoryIntent.DeleteCategory -> deleteCategory(intent)
            is InventoryIntent.GetAISuggestion -> getAISuggestion()
            is InventoryIntent.RequestAgentSuggestion ->
                requestAgentSuggestion(intent.userMessage, intent.mode)
            is InventoryIntent.SubmitAgentHubMessage ->
                submitAgentHubMessage(intent.userMessage)
            InventoryIntent.ApplyConsumption -> applyConsumption()
            is InventoryIntent.ApplyPendingConsumptionLines ->
                applyPendingConsumptionLines(intent.lines)
            InventoryIntent.AddAgentMissingToOrderCart -> addAgentMissingToOrderCart()
            is InventoryIntent.RemoveOrderCartLine -> removeOrderCartLine(intent.index)
            InventoryIntent.ClearOrderCart -> clearOrderCart()
            InventoryIntent.CompleteDemoOrder -> completeDemoOrder()
        }
    }

    private fun loadItems() {
        _state.update { it.copy(isLoading = true) }
        repository.getAllItems()
            .onEach { items ->
                _state.update { current ->
                    val missingFiltered =
                        filterSatisfiedMissingItems(current.lastRecipePlannedMissing, items)
                    val refs = current.lastRecipeConsumptionRefs
                    val extendedRefs =
                        if (current.lastRecipePlannedMissing.isNotEmpty()) {
                            supplementConsumptionRefsForOrderedMissing(
                                refs,
                                current.lastRecipePlannedMissing,
                                items,
                            )
                        } else {
                            refs
                        }
                    val fromRefs =
                        if (extendedRefs.isNotEmpty()) {
                            buildPendingFromRecipeRefs(extendedRefs, items)
                        } else {
                            emptyList()
                        }
                    val (newPending, newRefs) =
                        when {
                            extendedRefs.isNotEmpty() && fromRefs.isNotEmpty() ->
                                fromRefs to extendedRefs
                            extendedRefs.isNotEmpty() && fromRefs.isEmpty() ->
                                emptyList<PendingConsumptionLine>() to emptyList()
                            else ->
                                revalidatePendingAgainstInventoryDropInvalid(
                                    current.pendingConsumption,
                                    items,
                                ) to refs
                        }
                    current.copy(
                        itemList = items,
                        isLoading = false,
                        agentMissingItems = missingFiltered,
                        pendingConsumption = newPending,
                        lastRecipeConsumptionRefs = newRefs,
                    )
                }
            }
            .catch { e ->
                _state.update { it.copy(isLoading = false) }
                emitEffect(SideEffect.ShowToast("Ürünler yüklenemedi: ${e.message}"))
            }
            .launchIn(viewModelScope)
    }

    private fun loadCategories() {
        repository.getAllCategories()
            .onEach { categories ->
                _state.update { it.copy(categoryList = categories) }
            }
            .catch { e ->
                emitEffect(SideEffect.ShowToast("Kategoriler yüklenemedi: ${e.message}"))
            }
            .launchIn(viewModelScope)
    }

    private fun addItem(intent: InventoryIntent.AddItem) {
        viewModelScope.launch {
            try {
                repository.addOrUpdateItem(intent.item)
                emitEffect(SideEffect.ShowToast("Ürün kaydedildi"))
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Ürün eklenemedi"))
            }
        }
    }

    private fun deleteItem(intent: InventoryIntent.DeleteItem) {
        viewModelScope.launch {
            try {
                repository.deleteItem(intent.item)
                emitEffect(SideEffect.ShowToast("Ürün silindi"))
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Ürün silinemedi"))
            }
        }
    }

    private fun addCategory(intent: InventoryIntent.AddCategory) {
        viewModelScope.launch {
            try {
                repository.insertCategory(intent.category)
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Kategori eklenemedi"))
            }
        }
    }

    private fun deleteCategory(intent: InventoryIntent.DeleteCategory) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(intent.category)
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Kategori silinemedi"))
            }
        }
    }

    private fun mergeOrderLines(
        cart: List<OrderCartLine>,
        additions: List<OrderCartLine>,
    ): List<OrderCartLine> {
        val mut = cart.toMutableList()
        for (add in additions) {
            val i = mut.indexOfFirst {
                it.name.equals(add.name, ignoreCase = true) &&
                    it.unit.equals(add.unit, ignoreCase = true)
            }
            if (i >= 0) {
                val cur = mut[i]
                mut[i] = cur.copy(quantity = cur.quantity + add.quantity)
            } else {
                mut.add(add)
            }
        }
        return mut
    }

    private fun addAgentMissingToOrderCart() {
        val missing = _state.value.agentMissingItems
        if (missing.isEmpty()) {
            emitEffect(SideEffect.ShowToast("Sepete eklenecek eksik ürün yok."))
            return
        }
        val lines = missing.map { OrderCartLine(it.name, it.suggestedQuantity, it.unit) }
        _state.update { it.copy(orderCart = mergeOrderLines(it.orderCart, lines)) }
        emitEffect(SideEffect.ShowToast("Eksikler sipariş sepetine eklendi."))
    }

    private fun removeOrderCartLine(index: Int) {
        _state.update {
            val next = it.orderCart.filterIndexed { i, _ -> i != index }
            it.copy(orderCart = next)
        }
    }

    private fun clearOrderCart() {
        _state.update { it.copy(orderCart = emptyList()) }
    }

    private fun completeDemoOrder() {
        viewModelScope.launch {
            val cart = _state.value.orderCart
            if (cart.isEmpty()) {
                emitEffect(SideEffect.ShowToast("Sepet boş."))
                return@launch
            }
            try {
                val orderedAt = System.currentTimeMillis()
                for (line in cart) {
                    val qty = ceil(line.quantity).toInt().coerceAtLeast(1)
                    val name = line.name.trim()
                    repository.addOrUpdateItem(
                        InventoryItem(
                            name = name,
                            quantity = qty,
                            unit = line.unit,
                            expiryDate = EstimatedShelfLife.estimatedExpiryMillisAtEndOfDay(
                                productName = name,
                                orderedAtMillis = orderedAt,
                            ),
                            categoryId = 0,
                        ),
                    )
                }
                _state.update { it.copy(orderCart = emptyList()) }
                emitEffect(SideEffect.ShowToast("Demo sipariş tamamlandı; ürünler envantere eklendi."))
            } catch (_: Exception) {
                emitEffect(SideEffect.ShowToast("Sipariş tamamlanamadı."))
            }
        }
    }

    private fun getAISuggestion() {
        runAgentSuggest(userMessage = null, mode = AgentSuggestMode.INVENTORY_ONLY)
    }

    private fun requestAgentSuggestion(userMessage: String, mode: AgentSuggestMode) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) {
            emitEffect(
                SideEffect.ShowToast(
                    "Lütfen bir mesaj yazın veya «Envanter özeti» ile sadece stok özetini isteyin.",
                ),
            )
            return
        }
        runAgentSuggest(userMessage = trimmed, mode = mode)
    }

    /**
     * Tek gönder düğmesi: tarif isteği → RECIPE; sohbet → CHAT; «yap»/«pişir» → stok onayı (LLM’e gitmez).
     */
    private fun submitAgentHubMessage(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) {
            emitEffect(
                SideEffect.ShowToast(
                    "Bir şey yazın. Tarif için örn: «Bu akşam ne pişireyim?»",
                ),
            )
            return
        }

        val s = _state.value
        val hasStockPlan =
            effectiveRecipeRefs(s).isNotEmpty() || s.pendingConsumption.isNotEmpty()
        val recipeRequest = looksLikeRecipeRequest(trimmed)
        val cookingConfirm = looksLikeConfirmCookingIntent(trimmed) && !recipeRequest

        if (cookingConfirm) {
            if (!hasStockPlan) {
                emitEffect(
                    SideEffect.ShowToast(
                        "Önce tarif alın: «Bu akşam ne pişireyim?» veya «Elimdekilerle ne yemek?» yazıp gönderin.",
                    ),
                )
                return
            }
            val lines = resolveCookConfirmationLines(s)
            if (lines == null) {
                viewModelScope.launch {
                    _effect.emit(
                        SideEffect.ShowToast(
                            "Tarif stok satırları envanterle eşleşmiyor. Tarifi tekrar isteyin.",
                        ),
                    )
                }
                return
            }
            if (lines.isEmpty()) {
                emitEffect(
                    SideEffect.ShowToast(
                        "Bu tarif için stok düşümü kaydı gelmedi. Aynı soruyu tekrar gönderin veya " +
                            "aşağıdaki «Önerilen tüketim» kutusundan onaylayın.",
                    ),
                )
                return
            }
            val byId = s.itemList.associateBy { it.id }
            val stockOk =
                lines.all { line ->
                    (byId[line.inventoryItemId]?.quantity ?: 0) >= line.deltaRounded
                }
            if (!stockOk) {
                val detail = pendingConsumptionShortageSummary(lines, s.itemList)
                emitEffect(
                    SideEffect.ShowToast(
                        "Tarif için stok yetmiyor (LLM «eksik» listesinden bağımsız). $detail",
                    ),
                )
                return
            }
            viewModelScope.launch {
                _effect.emit(SideEffect.ShowCookRecipeConfirmation(lines))
                _effect.emit(SideEffect.ClearAgentHubInput)
            }
            return
        }

        val mode =
            if (recipeRequest) {
                AgentSuggestMode.RECIPE
            } else {
                AgentSuggestMode.CHAT
            }
        runAgentSuggest(userMessage = trimmed, mode = mode)
    }

    /**
     * Klavye ASCII / Türkçe farkını giderir: «yemegi» ile «yemeği» aynı anahtar.
     */
    private fun foldIntentText(s: String): String {
        var t = s.lowercase(Locale("tr", "TR")).trim()
        t = t.replace('ı', 'i')
        t = t.replace('İ', 'i')
        t = t.replace('ğ', 'g').replace('Ğ', 'g')
        t = t.replace('ü', 'u').replace('Ü', 'u')
        t = t.replace('ş', 's').replace('Ş', 's')
        t = t.replace('ö', 'o').replace('Ö', 'o')
        t = t.replace('ç', 'c').replace('Ç', 'c')
        return t.replace(Regex("\\s+"), "")
    }

    /** Kısa onay / pişir komutları tarif isteği sayılmaz. */
    private fun looksLikeRecipeRequest(msg: String): Boolean {
        val folded = foldIntentText(msg)
        if (folded.isEmpty()) return false
        val notRecipeFolded =
            setOf(
                "yap",
                "pisir",
                "evet",
                "tamam",
                "olur",
                "onay",
                "tamamdir",
                "buyur",
                "hadi",
                "yapalim",
                "yapayim",
                "yaparim",
                "pisireyim",
                "pisiriyorum",
                "pisirelim",
                "oluryapayim",
            )
        if (folded in notRecipeFolded) return false
        if (folded.length < 5) return false
        val hints =
            listOf(
                "ne pişireyim",
                "ne pişiririm",
                "ne yemek",
                "ne yesem",
                "ne yiyeyim",
                "yemek öner",
                "yemek istiyorum",
                "yemek iste",
                "bir şey ye",
                "birşey ye",
                "tarif ver",
                "tarif öner",
                "tarif bul",
                "yemek fikri",
                "akşam ne",
                "bu akşam",
                "akşama",
                "öğle yemeği",
                "kahvaltı",
                "malzemelerimle",
                "elimde",
                "elimdekilerle",
                "envanterimle",
                "ne yapsam",
                "öneri ver",
                "fikir ver",
                "hangi yemek",
                "ne pişirsem",
                "bugün ne",
                "acıktım",
                "açım",
                "karnım aç",
                "karnım acıktı",
                "pişirecek",
                "pişirelim mi",
                "mutfakta ne",
                "dolaba baktım",
                "elimde ne var",
                "buzdolabımda",
                "önerir misin",
                "tavsiye",
                "canım çekti",
            )
        if (hints.any { folded.contains(foldIntentText(it)) }) return true
        if (folded.contains("tarif") && folded.length >= 8) {
            // «bu tarifi yap / pişir» tarif isteği değil; pişirme onayı (Gönder → stok diyaloğu)
            if (folded.contains(foldIntentText("tarifi yap"))) return false
            if (folded.contains(foldIntentText("tarifi pişir"))) return false
            if (folded.contains(foldIntentText("tarifi hazırla"))) return false
            return true
        }
        if (folded.contains("yemek") && folded.length >= 10) return true
        return false
    }

    private fun normalizeIngredientName(s: String): String =
        s.lowercase(Locale("tr", "TR")).replace(Regex("\\s+"), "")

    private fun inventoryRowMatchesMissing(invName: String, missingName: String): Boolean {
        val en = normalizeIngredientName(invName)
        val mn = normalizeIngredientName(missingName)
        return en == mn || en.contains(mn) || mn.contains(en)
    }

    /**
     * Tarifte eksik sayılan ama sonradan envantere eklenen ürünler için CONSUMPTION_JSON'da
     * id olmayabilir; sipariş sonrası aynı isimli satıra tüketim ref'i ekler.
     */
    private fun supplementConsumptionRefsForOrderedMissing(
        refs: List<RecipeConsumptionRef>,
        plannedMissing: List<AgentMissingItem>,
        items: List<InventoryItem>,
    ): List<RecipeConsumptionRef> {
        if (plannedMissing.isEmpty()) return refs
        val byId = items.associateBy { it.id }
        val out = refs.toMutableList()
        for (m in plannedMissing) {
            val need = ceil(m.suggestedQuantity).toInt().coerceAtLeast(1)
            val covered =
                out.any { ref ->
                    val inv = byId[ref.inventoryItemId] ?: return@any false
                    inventoryRowMatchesMissing(inv.name, m.name)
                }
            if (covered) continue
            val inv =
                items.firstOrNull { row ->
                    inventoryRowMatchesMissing(row.name, m.name) && row.quantity >= need
                } ?: continue
            out.add(RecipeConsumptionRef(inventoryItemId = inv.id, delta = m.suggestedQuantity))
        }
        return out
    }

    private fun effectiveRecipeRefs(state: InventoryState): List<RecipeConsumptionRef> {
        val r = state.lastRecipeConsumptionRefs
        if (state.lastRecipePlannedMissing.isEmpty()) return r
        return supplementConsumptionRefsForOrderedMissing(
            r,
            state.lastRecipePlannedMissing,
            state.itemList,
        )
    }

    /** Sipariş / yeni stok sonrası: envanterde adı ve miktarı yeten eksikleri listeden düşer. */
    private fun filterSatisfiedMissingItems(
        missing: List<AgentMissingItem>,
        inventory: List<InventoryItem>,
    ): List<AgentMissingItem> {
        if (missing.isEmpty()) return missing
        return missing.filter { m ->
            val mn = normalizeIngredientName(m.name)
            val need = ceil(m.suggestedQuantity).toInt().coerceAtLeast(1)
            val available = inventory
                .filter { inv ->
                    val en = normalizeIngredientName(inv.name)
                    en == mn || en.contains(mn) || mn.contains(en)
                }
                .sumOf { it.quantity }
            available < need
        }
    }

    private fun looksLikeConfirmCookingIntent(msg: String): Boolean {
        val folded = foldIntentText(msg)
        if (folded.isEmpty()) return false
        val exactFolded =
            setOf(
                foldIntentText("yap"),
                foldIntentText("pişir"),
                foldIntentText("evet"),
                foldIntentText("tamam"),
                foldIntentText("olur"),
                foldIntentText("onay"),
                foldIntentText("tamamdır"),
                foldIntentText("olur öyle"),
                foldIntentText("evet öyle"),
                foldIntentText("buyur"),
                foldIntentText("hadi"),
                foldIntentText("yapalım"),
                foldIntentText("tamam yapayım"),
                foldIntentText("yapayım"),
                foldIntentText("yaparım"),
                foldIntentText("olur yapayım"),
                foldIntentText("pişireyim"),
                foldIntentText("pişiriyorum"),
                foldIntentText("pişirelim"),
            )
        if (folded in exactFolded) return true
        val phrases =
            listOf(
                "bu yemeği",
                "bu yemegi",
                "yemeği yap",
                "yemegi yap",
                "sunu yap",
                "şunu yap",
                "tarifi yap",
                "yapalım",
                "pişireceğim",
                "pişirelim",
                "hazırlayacağım",
                "hazırlayalım",
                "evet yap",
                "tamam yap",
                "olur yap",
                "yemeği pişir",
                "yemegi pisir",
                "tarifi pişir",
                "hadi pişir",
                "hadi yap",
                "şimdi pişir",
                "şimdi yap",
                "stoktan düş",
                "stok düş",
                "yapıyorum",
                "yapacam",
                "yapacağım",
                "dediğin gibi",
                "önerdiğin",
                "bu tarifi",
            )
        return phrases.any { folded.contains(foldIntentText(it)) }
    }

    /**
     * Envanter güncellenince bekleyen satırları güncel stokla yeniden hesaplar.
     * Stok henüz yetmese bile satırı tutar (sipariş sonrası aynı id ile düşüm yapılabilsin).
     */
    private fun revalidatePendingAgainstInventoryDropInvalid(
        pending: List<PendingConsumptionLine>,
        items: List<InventoryItem>,
    ): List<PendingConsumptionLine> {
        if (pending.isEmpty()) return emptyList()
        val byId = items.associateBy { it.id }
        val out = mutableListOf<PendingConsumptionLine>()
        for (line in pending) {
            val item = byId[line.inventoryItemId] ?: continue
            out.add(
                line.copy(
                    projectedQuantity =
                        (item.quantity - line.deltaRounded).coerceAtLeast(0),
                ),
            )
        }
        return out
    }

    private fun resolveCookConfirmationLines(s: InventoryState): List<PendingConsumptionLine>? {
        val eff = effectiveRecipeRefs(s)
        return when {
            eff.isNotEmpty() -> {
                val lines = buildPendingFromRecipeRefs(eff, s.itemList)
                if (lines.isEmpty()) null else lines
            }
            s.pendingConsumption.isNotEmpty() -> {
                val lines = revalidatePendingAgainstInventory(s.pendingConsumption, s.itemList)
                if (lines.isEmpty()) null else lines
            }
            else -> emptyList()
        }
    }

    private fun revalidatePendingAgainstInventory(
        pending: List<PendingConsumptionLine>,
        items: List<InventoryItem>,
    ): List<PendingConsumptionLine> {
        if (pending.isEmpty()) return emptyList()
        val byId = items.associateBy { it.id }
        val out = mutableListOf<PendingConsumptionLine>()
        for (line in pending) {
            val item = byId[line.inventoryItemId] ?: continue
            out.add(
                line.copy(
                    projectedQuantity =
                        (item.quantity - line.deltaRounded).coerceAtLeast(0),
                ),
            )
        }
        return out
    }

    private fun buildPendingFromRecipeRefs(
        refs: List<RecipeConsumptionRef>,
        items: List<InventoryItem>,
    ): List<PendingConsumptionLine> {
        if (refs.isEmpty()) return emptyList()
        val byId = items.associateBy { it.id }
        val out = mutableListOf<PendingConsumptionLine>()
        for (ref in refs) {
            val item = byId[ref.inventoryItemId] ?: continue
            val deltaInt = ceil(ref.delta).toInt().coerceAtLeast(1)
            out.add(
                PendingConsumptionLine(
                    inventoryItemId = item.id,
                    name = item.name,
                    unit = item.unit,
                    deltaRaw = ref.delta,
                    deltaRounded = deltaInt,
                    projectedQuantity =
                        (item.quantity - deltaInt).coerceAtLeast(0),
                ),
            )
        }
        return out
    }

    private fun recipeFollowupContextForChat(state: InventoryState): String? {
        val m = state.lastRecipeAgentMessage?.trim()?.take(3500) ?: return null
        if (m.isEmpty()) return null
        return "Son tarif önerisi (aynı konuşma, özet metin):\n$m"
    }

    private fun runAgentSuggest(userMessage: String?, mode: AgentSuggestMode) {
        viewModelScope.launch {
            val snapshot = _state.value
            val catById = snapshot.categoryList.associateBy { it.id }
            val items = snapshot.itemList.map { item ->
                val catName = item.categoryId.takeIf { it != 0 }?.let { id -> catById[id]?.name }
                InventoryItemRequestDto(
                    name = item.name,
                    quantity = item.quantity.toDouble(),
                    unit = item.unit,
                    expiryDate = item.expiryDate,
                    categoryName = catName,
                    inventoryItemId = item.id.takeIf { it > 0 },
                )
            }
            val suggestMode =
                when (mode) {
                    AgentSuggestMode.CHAT -> "chat"
                    AgentSuggestMode.RECIPE -> "recipe"
                    AgentSuggestMode.INVENTORY_ONLY -> "inventory_only"
                }
            val recipeCtx =
                if (mode == AgentSuggestMode.CHAT) recipeFollowupContextForChat(snapshot) else null
            val request =
                SuggestRequestDto(
                    items = items,
                    userMessage = userMessage,
                    suggestMode = suggestMode,
                    recipeFollowupContext = recipeCtx,
                    clientTimeZone = ZoneId.systemDefault().id,
                )
            _state.update {
                it.copy(
                    isLoading = true,
                    // Envanter özeti (userMessage null) önceki sohbet balonunu silmesin
                    agentUserMessage = userMessage ?: it.agentUserMessage,
                )
            }
            val result = agentRepository.suggest(request)
            result.fold(
                onSuccess = { response ->
                    val cleanedMessage = AgentMessageCleaner.forDisplay(response.message)
                    val plannedRaw =
                        response.missingItems.map { it.toAgentMissingItem() }
                    val baseRefs =
                        response.consumption.map {
                            RecipeConsumptionRef(it.inventoryItemId, it.delta)
                        }
                    val extendedRefs =
                        if (mode == AgentSuggestMode.RECIPE) {
                            supplementConsumptionRefsForOrderedMissing(
                                baseRefs,
                                plannedRaw,
                                snapshot.itemList,
                            )
                        } else {
                            baseRefs
                        }
                    val pending =
                        if (mode == AgentSuggestMode.RECIPE) {
                            if (extendedRefs.isNotEmpty()) {
                                buildPendingFromRecipeRefs(extendedRefs, snapshot.itemList)
                            } else {
                                emptyList()
                            }
                        } else {
                            snapshot.pendingConsumption
                        }
                    val missing =
                        if (mode == AgentSuggestMode.RECIPE) {
                            filterSatisfiedMissingItems(plannedRaw, snapshot.itemList)
                        } else {
                            filterSatisfiedMissingItems(
                                snapshot.lastRecipePlannedMissing,
                                snapshot.itemList,
                            )
                        }
                    val lastRecipeMsg =
                        if (mode == AgentSuggestMode.RECIPE) {
                            cleanedMessage
                        } else {
                            snapshot.lastRecipeAgentMessage
                        }
                    val lastRefs =
                        if (mode == AgentSuggestMode.RECIPE) {
                            extendedRefs
                        } else {
                            snapshot.lastRecipeConsumptionRefs
                        }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            agentMessage = cleanedMessage,
                            pendingConsumption = pending,
                            agentMissingItems = missing,
                            lastRecipeAgentMessage = lastRecipeMsg,
                            lastRecipeConsumptionRefs = lastRefs,
                            lastRecipePlannedMissing =
                                if (mode == AgentSuggestMode.RECIPE) {
                                    plannedRaw
                                } else {
                                    it.lastRecipePlannedMissing
                                },
                        )
                    }
                    if (userMessage != null) {
                        emitEffect(SideEffect.ClearAgentHubInput)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false) }
                    emitEffect(SideEffect.ShowToast(agentRepository.humanReadableError(e)))
                },
            )
        }
    }

    private fun MissingItemDto.toAgentMissingItem() = AgentMissingItem(
        name = name,
        suggestedQuantity = suggestedQuantity,
        unit = unit,
    )

    private fun pendingConsumptionStockSufficient(
        pending: List<PendingConsumptionLine>,
        items: List<InventoryItem>,
    ): Boolean {
        if (pending.isEmpty()) return false
        val byId = items.associateBy { it.id }
        return pending.all { line ->
            (byId[line.inventoryItemId]?.quantity ?: 0) >= line.deltaRounded
        }
    }

    /** Kullanıcıya hangi satırda miktar yetmediğini açık yazar (ör. ekmek 1 adet varken 2 istenmesi). */
    private fun pendingConsumptionShortageSummary(
        lines: List<PendingConsumptionLine>,
        items: List<InventoryItem>,
    ): String {
        val byId = items.associateBy { it.id }
        val parts =
            lines.mapNotNull { line ->
                val q = byId[line.inventoryItemId]?.quantity ?: 0
                if (q >= line.deltaRounded) null
                else {
                    val eksik = line.deltaRounded - q
                    "${line.name}: stokta $q ${line.unit}, tarif ${line.deltaRounded} ${line.unit} istiyor ($eksik ${line.unit} daha gerekli)"
                }
            }
        return parts.joinToString(" ").take(350)
    }

    private fun agentMessageAfterSuccessfulCook(previousAssistantText: String?): String {
        val base = previousAssistantText?.trim().orEmpty()
        return if (base.isEmpty()) {
            "Afiyet olsun! 🙂\n\nBaşka bir yemek önerisi ister misin? " +
                "«Bu akşam ne pişireyim?» yazabilir veya alttaki Tarif düğmesine basabilirsin."
        } else {
            base + POST_COOK_ASSISTANT_SUFFIX
        }
    }

    private fun applyConsumption() {
        viewModelScope.launch {
            val snapshot = _state.value
            val pending = snapshot.pendingConsumption
            if (pending.isEmpty()) {
                emitEffect(SideEffect.ShowToast("Onaylanacak tüketim yok."))
                return@launch
            }
            if (!pendingConsumptionStockSufficient(pending, snapshot.itemList)) {
                emitEffect(
                    SideEffect.ShowToast(
                        "Stok yetersiz. ${pendingConsumptionShortageSummary(pending, snapshot.itemList)}",
                    ),
                )
                return@launch
            }
            val deltas = pending.map { it.inventoryItemId to it.deltaRounded }
            try {
                repository.applyConsumptionDeltas(deltas)
                val prevMsg = _state.value.agentMessage
                _state.update {
                    it.copy(
                        pendingConsumption = emptyList(),
                        lastRecipeAgentMessage = null,
                        lastRecipeConsumptionRefs = emptyList(),
                        lastRecipePlannedMissing = emptyList(),
                        agentMissingItems = emptyList(),
                        agentMessage = agentMessageAfterSuccessfulCook(prevMsg),
                    )
                }
                emitEffect(SideEffect.ShowToast("Stok güncellendi."))
            } catch (e: IllegalArgumentException) {
                emitEffect(SideEffect.ShowToast(e.message ?: "Stok güncellenemedi."))
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Stok güncellenemedi."))
            }
        }
    }

    private fun applyPendingConsumptionLines(lines: List<PendingConsumptionLine>) {
        viewModelScope.launch {
            if (lines.isEmpty()) {
                emitEffect(SideEffect.ShowToast("Onaylanacak tüketim yok."))
                return@launch
            }
            if (!pendingConsumptionStockSufficient(lines, _state.value.itemList)) {
                emitEffect(
                    SideEffect.ShowToast(
                        "Stok yetersiz. ${pendingConsumptionShortageSummary(lines, _state.value.itemList)}",
                    ),
                )
                return@launch
            }
            val deltas = lines.map { it.inventoryItemId to it.deltaRounded }
            try {
                repository.applyConsumptionDeltas(deltas)
                val prevMsg = _state.value.agentMessage
                _state.update {
                    it.copy(
                        pendingConsumption = emptyList(),
                        lastRecipeAgentMessage = null,
                        lastRecipeConsumptionRefs = emptyList(),
                        lastRecipePlannedMissing = emptyList(),
                        agentMissingItems = emptyList(),
                        agentMessage = agentMessageAfterSuccessfulCook(prevMsg),
                    )
                }
                emitEffect(SideEffect.ShowToast("Stok güncellendi."))
            } catch (e: IllegalArgumentException) {
                emitEffect(SideEffect.ShowToast(e.message ?: "Stok güncellenemedi."))
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Stok güncellenemedi."))
            }
        }
    }

    private fun emitEffect(effect: SideEffect) {
        viewModelScope.launch {
            _effect.emit(effect)
        }
    }
}
