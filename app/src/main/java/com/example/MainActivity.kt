package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.VaultViewModel
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val vaultViewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vaultViewModel.loadSavedVaultUri(this)
        enableEdgeToEdge()

        // Configure system bars to be shown by default for edge-to-edge drawing
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            MyApplicationTheme {
                val vaultUri by vaultViewModel.vaultUri.collectAsState()
                val dek by vaultViewModel.dek.collectAsState()
                val selectedMedia by vaultViewModel.selectedMedia.collectAsState()

                // Dynamically hide/show system bars based on full-screen viewer state
                androidx.compose.runtime.LaunchedEffect(selectedMedia) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (selectedMedia != null) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val screenModifier = Modifier.fillMaxSize()

                    when {
                        vaultUri == null || dek == null -> {
                            WelcomeScreen(
                                viewModel = vaultViewModel,
                                modifier = screenModifier
                            )
                        }
                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                GalleryScreen(
                                    viewModel = vaultViewModel,
                                    modifier = screenModifier
                                )
                                if (selectedMedia != null) {
                                    MediaViewerScreen(
                                        viewModel = vaultViewModel,
                                        item = selectedMedia!!,
                                        modifier = screenModifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
