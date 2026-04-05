package com.example.kitchenmind.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(var title: String, var icon: ImageVector, var route: String) {
    object Inventory : BottomNavItem("Envanter", Icons.AutoMirrored.Filled.List, "dashboard")
    object Recipes : BottomNavItem("Tarifler", Icons.Default.Restaurant, "recipes")
    object Orders : BottomNavItem("Sipariş", Icons.Default.ShoppingCart, "orders")
    object Agent : BottomNavItem("Asistan", Icons.Default.Star, "agent_hub")
}
