package com.example.kitchenmind.ui.mvi

import com.example.kitchenmind.data.model.Category
import com.example.kitchenmind.data.model.InventoryItem

data class InventoryState(
    val itemList: List<InventoryItem> = emptyList(),
    val categoryList: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val agentMessage: String? = null // AI için ileriye yönelik
)

sealed class InventoryIntent {
    object LoadItems : InventoryIntent()
    object LoadCategories : InventoryIntent()
    data class AddItem(val item: InventoryItem) : InventoryIntent()
    data class DeleteItem(val item: InventoryItem) : InventoryIntent()
    data class AddCategory(val category: Category) : InventoryIntent()
    data class DeleteCategory(val category: Category) : InventoryIntent()
    object GetAISuggestion : InventoryIntent()
}

sealed class SideEffect {
    data class ShowToast(val message: String) : SideEffect()
}
