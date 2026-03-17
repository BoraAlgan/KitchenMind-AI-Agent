package com.example.kitchenmind

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.kitchenmind.ui.mvi.SideEffect
import com.example.kitchenmind.ui.screens.MainScreen
import com.example.kitchenmind.ui.theme.KitchenMindTheme
import com.example.kitchenmind.ui.viewmodel.InventoryViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.KoinContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KoinContext {
                KitchenMindTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val viewModel: InventoryViewModel = koinViewModel()
                        LaunchedEffect(Unit) {
                            viewModel.effect.collectLatest { effect ->
                                when (effect) {
                                    is SideEffect.ShowToast -> {
                                        Toast.makeText(this@MainActivity, effect.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        MainScreen()
                    }
                }
            }
        }
    }
}