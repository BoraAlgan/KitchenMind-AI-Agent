package com.example.kitchenmind.ui.mvi

import com.example.kitchenmind.data.model.Category
import com.example.kitchenmind.data.model.InventoryItem

/** Backend `missing_items` — alışveriş / tamamlama listesi. */
data class AgentMissingItem(
    val name: String,
    val suggestedQuantity: Double,
    val unit: String,
)

/** Sipariş sepeti satırı (demo akış; ödeme entegrasyonu yok). */
data class OrderCartLine(
    val name: String,
    val quantity: Double,
    val unit: String,
)

/** Son tarif yanıtındaki ham tüketim satırı (envanter değişince yeniden doğrulanır). */
data class RecipeConsumptionRef(
    val inventoryItemId: Int,
    val delta: Double,
)

/** Onay bekleyen stok düşümü (son başarılı agent yanıtından). */
data class PendingConsumptionLine(
    val inventoryItemId: Int,
    val name: String,
    val unit: String,
    val deltaRaw: Double,
    val deltaRounded: Int,
    val projectedQuantity: Int,
)

enum class AgentSuggestMode {
    CHAT,
    RECIPE,
    INVENTORY_ONLY,
}

data class InventoryState(
    val itemList: List<InventoryItem> = emptyList(),
    val categoryList: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    /** Son gönderilen kullanıcı mesajı (Agent Hub sohbet özeti). */
    val agentUserMessage: String? = null,
    /** Asistan yanıtı (backend `message`). */
    val agentMessage: String? = null,
    /** Backend `consumption` + yerel önizleme; kullanıcı onayından sonra Room güncellenir. */
    val pendingConsumption: List<PendingConsumptionLine> = emptyList(),
    /** Backend `missing_items`; kopyala / paylaş / arama ile manuel tamamlama. */
    val agentMissingItems: List<AgentMissingItem> = emptyList(),
    /** Son «Tarif öner» asistan metni; sohbet modunda bağlam olarak gönderilir. */
    val lastRecipeAgentMessage: String? = null,
    /** Tarif yanıtındaki CONSUMPTION_JSON (stoktan düş / «yap» onayı için). */
    val lastRecipeConsumptionRefs: List<RecipeConsumptionRef> = emptyList(),
    /**
     * Son tarifteki eksikler (API ham listesi). Siparişle envanter dolunca tüketim ref’lerine
     * otomatik eklenir (örn. limon).
     */
    val lastRecipePlannedMissing: List<AgentMissingItem> = emptyList(),
    /** Demo sipariş sepeti; onay sonrası envantere eklenir. */
    val orderCart: List<OrderCartLine> = emptyList(),
    /** LangGraph sipariş asistanı (`/api/v1/order-flow/step`). */
    val orderFlowThreadId: String? = null,
    val orderFlowChatMessages: List<OrderFlowChatBubble> = emptyList(),
    val orderFlowLoading: Boolean = false,
)

/** Sipariş AI alt ekranı sohbet balonu. */
data class OrderFlowChatBubble(
    val isUser: Boolean,
    val text: String,
)

sealed class InventoryIntent {
    object LoadItems : InventoryIntent()
    object LoadCategories : InventoryIntent()
    data class AddItem(val item: InventoryItem) : InventoryIntent()
    data class DeleteItem(val item: InventoryItem) : InventoryIntent()
    data class AddCategory(val category: Category) : InventoryIntent()
    data class DeleteCategory(val category: Category) : InventoryIntent()
    /** Sadece envanter listesi ile özet / tarama (user_message gönderilmez). */
    object GetAISuggestion : InventoryIntent()

    /**
     * @param mode CHAT = sohbet/selam (stok düşümü yok); RECIPE = tarif + JSON;
     * INVENTORY_ONLY dahili (GetAISuggestion).
     */
    data class RequestAgentSuggestion(
        val userMessage: String,
        val mode: AgentSuggestMode = AgentSuggestMode.CHAT,
    ) : InventoryIntent()

    /**
     * Asistan tek gönder kutusu: mesaj tarif mi sohbet mi seçilir; «yap»/«pişir» stok onayı (LLM yok).
     */
    data class SubmitAgentHubMessage(val userMessage: String) : InventoryIntent()

    /** [pendingConsumption] üzerinden Room stok düşürür (onay diyalogundan sonra). */
    object ApplyConsumption : InventoryIntent()

    /** «Yemeği yap» onayı: verilen satırlarla stok düşer, tarif bağlamı sıfırlanır. */
    data class ApplyPendingConsumptionLines(val lines: List<PendingConsumptionLine>) : InventoryIntent()

    /** Son agent yanıtındaki eksikleri [orderCart] ile birleştirir. */
    object AddAgentMissingToOrderCart : InventoryIntent()

    data class RemoveOrderCartLine(val index: Int) : InventoryIntent()

    object ClearOrderCart : InventoryIntent()

    /** Demo: sepetteki kalemleri envantere ekler (gerçek ödeme yok). */
    object CompleteDemoOrder : InventoryIntent()

    /** Sipariş asistanı oturumunu sıfırlar (yeni diyalog). */
    object ResetOrderFlowChat : InventoryIntent()

    /** LangGraph sipariş adımı — backend `order-flow/step`. */
    data class SendOrderFlowMessage(val userMessage: String) : InventoryIntent()
}

sealed class SideEffect {
    data class ShowToast(val message: String) : SideEffect()

    /** Yalnızca Agent Hub: doğal dil mesajı başarıyla gönderildikten sonra giriş alanını sıfırla. */
    object ClearAgentHubInput : SideEffect()

    /** Tarif sonrası «yap» niyeti: stok düşümü onay penceresi. */
    data class ShowCookRecipeConfirmation(val lines: List<PendingConsumptionLine>) : SideEffect()

    /** Eksik / silinmiş stok sonrası Sipariş sekmesine geç. */
    object NavigateToOrders : SideEffect()
}
