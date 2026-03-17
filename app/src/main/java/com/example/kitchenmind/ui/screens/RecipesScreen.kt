package com.example.kitchenmind.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.res.Configuration

// mock data
data class RecipeItem(
    val title: String,
    val matchedIngredients: Int,
    val prepTimeMinutes: Int,
    val difficulty: String,
    val color: Color
)

val mockRecipes = listOf(
    RecipeItem("Vegetable Stir-fry", 3, 15, "Easy", Color(0xFF60AD5E)),
    RecipeItem("Tomato Basil Pasta", 2, 25, "Medium", Color(0xFFE57373)),
    RecipeItem("Green Smoothie", 4, 5, "Easy", Color(0xFF81C784)),
    RecipeItem("Omelette with Herbs", 2, 10, "Easy", Color(0xFFFFD54F)),
    RecipeItem("Garlic Roasted Potatoes", 1, 45, "Medium", Color(0xFFFFB74D))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen() {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 4 else 2

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Recipe Output") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 16.dp)
        ) {
            // "AI Insight" Banner — compact in landscape
            if (!isLandscape) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Chef Insight: You have multiple items expiring soon. Check the recommendations below!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Text(
                text = "Recommended for you",
                style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(mockRecipes) { recipe ->
                    RecipeCard(recipe, compact = isLandscape)
                }
            }
        }
    }
}

@Composable
fun RecipeCard(recipe: RecipeItem, compact: Boolean = false) {
    val cardHeight = if (compact) 110.dp else 200.dp
    Card(
        modifier = Modifier.fillMaxWidth().height(cardHeight),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(recipe.color)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 6.dp else 12.dp)
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!compact) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Matches ${recipe.matchedIngredients} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${recipe.prepTimeMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = recipe.difficulty,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
