package com.example.kitchenmind.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val quantity: Int,
    val unit: String = "pcs",
    val expiryDate: Long? = null,
    val categoryId: Int = 0
)
