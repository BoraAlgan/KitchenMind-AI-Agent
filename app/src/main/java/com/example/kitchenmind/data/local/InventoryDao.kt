package com.example.kitchenmind.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.kitchenmind.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items ORDER BY categoryId ASC, name ASC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE LOWER(name) = LOWER(:name) AND expiryDate IS :expiryDate LIMIT 1")
    suspend fun getItemByNameAndExpiry(name: String, expiryDate: Long?): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): InventoryItem?

    @Query("UPDATE inventory_items SET quantity = :newQuantity WHERE id = :id")
    suspend fun updateQuantity(id: Int, newQuantity: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    /**
     * Onaylı tüketim: id başına deltaları toplar; atomik işlem.
     * @throws IllegalArgumentException bilinmeyen id veya yetersiz stok
     */
    @Transaction
    suspend fun applyConsumptionDeltas(deltas: List<Pair<Int, Int>>) {
        val merged = deltas.groupBy { it.first }.mapValues { (_, pairs) -> pairs.sumOf { it.second } }
        for ((id, totalDelta) in merged) {
            val item = getItemById(id)
                ?: throw IllegalArgumentException("Bilinmeyen ürün (id=$id).")
            val newQty = item.quantity - totalDelta
            if (newQty < 0) {
                throw IllegalArgumentException("Yetersiz stok: ${item.name}")
            }
            if (newQty == 0) {
                deleteItem(item)
            } else {
                updateQuantity(id, newQty)
            }
        }
    }
}
