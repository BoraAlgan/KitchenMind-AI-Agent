package com.example.kitchenmind.data.repository

import com.example.kitchenmind.data.local.CategoryDao
import com.example.kitchenmind.data.local.InventoryDao
import com.example.kitchenmind.data.model.Category
import com.example.kitchenmind.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

class InventoryRepository(
    private val dao: InventoryDao,
    private val categoryDao: CategoryDao
) {
    fun getAllItems(): Flow<List<InventoryItem>> = dao.getAllItems()

    suspend fun addOrUpdateItem(item: InventoryItem) {
        val existing = dao.getItemByNameAndExpiry(item.name, item.expiryDate)
        if (existing != null) {
            // Same name AND same expiry date → just add to quantity
            dao.updateQuantity(existing.id, existing.quantity + item.quantity)
        } else {
            // Different expiry date (or brand new item) → insert as separate entry
            dao.insertItem(item)
        }
    }

    suspend fun deleteItem(item: InventoryItem) = dao.deleteItem(item)

    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category) = categoryDao.insertCategory(category)

    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)
}
