package com.example.kitchenmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kitchenmind.data.model.Category
import com.example.kitchenmind.data.repository.InventoryRepository
import com.example.kitchenmind.ui.mvi.InventoryIntent
import com.example.kitchenmind.ui.mvi.InventoryState
import com.example.kitchenmind.ui.mvi.SideEffect
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

class InventoryViewModel(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SideEffect>()
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
        }
    }

    private fun loadItems() {
        _state.update { it.copy(isLoading = true) }
        repository.getAllItems()
            .onEach { items ->
                _state.update { it.copy(itemList = items, isLoading = false) }
            }
            .catch { e ->
                _state.update { it.copy(isLoading = false) }
                emitEffect(SideEffect.ShowToast("Error loading items: ${e.message}"))
            }
            .launchIn(viewModelScope)
    }

    private fun loadCategories() {
        repository.getAllCategories()
            .onEach { categories ->
                _state.update { it.copy(categoryList = categories) }
            }
            .catch { e ->
                emitEffect(SideEffect.ShowToast("Error loading categories: ${e.message}"))
            }
            .launchIn(viewModelScope)
    }

    private fun addItem(intent: InventoryIntent.AddItem) {
        viewModelScope.launch {
            try {
                repository.addOrUpdateItem(intent.item)
                emitEffect(SideEffect.ShowToast("Item added"))
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Failed to add item"))
            }
        }
    }

    private fun deleteItem(intent: InventoryIntent.DeleteItem) {
        viewModelScope.launch {
            try {
                repository.deleteItem(intent.item)
                emitEffect(SideEffect.ShowToast("Item deleted"))
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Failed to delete item"))
            }
        }
    }

    private fun addCategory(intent: InventoryIntent.AddCategory) {
        viewModelScope.launch {
            try {
                repository.insertCategory(intent.category)
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Failed to add category"))
            }
        }
    }

    private fun deleteCategory(intent: InventoryIntent.DeleteCategory) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(intent.category)
            } catch (e: Exception) {
                emitEffect(SideEffect.ShowToast("Failed to delete category"))
            }
        }
    }

    // Proactively scan inventory for AI-driven insights and recipe generation
    // This hook is designed for future integration with LLM-based agents (e.g., CrewAI or LangGraph)
    private fun getAISuggestion() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Simulate asynchronous API processing delay
            kotlinx.coroutines.delay(1500)
            _state.update {
                it.copy(
                    isLoading = false,
                    agentMessage = "Inventory scanned. Consider making a vegetable stir-fry with your expiring items!"
                )
            }
        }
    }

    private fun emitEffect(effect: SideEffect) {
        viewModelScope.launch {
            _effect.emit(effect)
        }
    }
}
