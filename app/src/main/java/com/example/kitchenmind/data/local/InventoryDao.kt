package com.example.kitchenmind.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.kitchenmind.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items ORDER BY categoryId ASC, name ASC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE LOWER(name) = LOWER(:name) AND expiryDate IS :expiryDate LIMIT 1")
    suspend fun getItemByNameAndExpiry(name: String, expiryDate: Long?): InventoryItem?

    @Query("UPDATE inventory_items SET quantity = :newQuantity WHERE id = :id")
    suspend fun updateQuantity(id: Int, newQuantity: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)
}
