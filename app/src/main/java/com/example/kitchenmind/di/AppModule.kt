package com.example.kitchenmind.di

import com.example.kitchenmind.data.local.InventoryDatabase
import com.example.kitchenmind.data.repository.InventoryRepository
import com.example.kitchenmind.ui.viewmodel.InventoryViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { InventoryDatabase.getInstance(androidContext()) }
    single { get<InventoryDatabase>().inventoryDao }
    single { get<InventoryDatabase>().categoryDao }
    singleOf(::InventoryRepository)
    viewModelOf(::InventoryViewModel)
}
