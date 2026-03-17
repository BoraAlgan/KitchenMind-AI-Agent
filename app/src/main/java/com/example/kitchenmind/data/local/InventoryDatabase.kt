package com.example.kitchenmind.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kitchenmind.data.model.Category
import com.example.kitchenmind.data.model.InventoryItem

@Database(entities = [InventoryItem::class, Category::class], version = 3, exportSchema = false)
abstract class InventoryDatabase : RoomDatabase() {

    abstract val inventoryDao: InventoryDao
    abstract val categoryDao: CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: InventoryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'pcs'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN categoryId INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): InventoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    "kitchenmind_db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
