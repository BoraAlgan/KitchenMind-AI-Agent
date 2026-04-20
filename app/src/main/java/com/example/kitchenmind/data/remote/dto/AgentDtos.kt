package com.example.kitchenmind.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InventoryItemRequestDto(
    val name: String,
    val quantity: Double,
    val unit: String,
    val expiryDate: Long? = null,
    val categoryName: String? = null,
    /** Room `inventory_items.id`; sunucu tüketim doğrulaması için. */
    val inventoryItemId: Int? = null,
)

@Serializable
data class SuggestRequestDto(
    val items: List<InventoryItemRequestDto> = emptyList(),
    val userMessage: String? = null,
    /** Backend: `chat` | `inventory_only` | `recipe` */
    val suggestMode: String? = null,
    /** Sohbette son tarif önerisi metni özeti (bağlam). */
    val recipeFollowupContext: String? = null,
    /** IANA (örn. Europe/Istanbul); SKT analizi kullanıcı yerel gününe göre yapılır. */
    val clientTimeZone: String? = null,
)

@Serializable
data class ConsumptionLineDto(
    @SerialName("inventory_item_id") val inventoryItemId: Int,
    val delta: Double,
)

@Serializable
data class MissingItemDto(
    val name: String,
    @SerialName("suggested_quantity") val suggestedQuantity: Double,
    val unit: String,
)

@Serializable
data class SuggestResponseDto(
    val message: String,
    val consumption: List<ConsumptionLineDto> = emptyList(),
    @SerialName("missing_items") val missingItems: List<MissingItemDto> = emptyList(),
)

// --- LangGraph sipariş akışı ---

@Serializable
data class OrderFlowStepRequestDto(
    @SerialName("userMessage") val userMessage: String,
    @SerialName("threadId") val threadId: String? = null,
)

@Serializable
data class OrderDraftLineDto(
    val name: String,
    val quantity: Double,
    val unit: String,
)

@Serializable
data class OrderFlowStepResponseDto(
    @SerialName("threadId") val threadId: String,
    val message: String,
    @SerialName("draftLines") val draftLines: List<OrderDraftLineDto> = emptyList(),
    val status: String,
)
