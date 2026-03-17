package com.example.kitchenmind.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(var title: String, var icon: ImageVector, var route: String) {
    object Inventory : BottomNavItem("Inventory", Icons.Default.List, "dashboard")
    object Recipes : BottomNavItem("Recipes", Icons.Default.ShoppingCart, "recipes")
    object Agent : BottomNavItem("Agent", Icons.Default.Star, "agent_hub")
}
