package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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

        window.setWindowAnimations(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Ẩn vĩnh viễn Status Bar trên Activity chính
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        setContent {
            MyApplicationTheme {
                val vaultUri by vaultViewModel.vaultUri.collectAsState()
                val dek by vaultViewModel.dek.collectAsState()
                val selectedMedia by vaultViewModel.selectedMedia.collectAsState()

                val isVaultUnlocked = vaultUri != null && dek != null

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) { _ ->
                    val screenModifier = Modifier.fillMaxSize()

                    // Chuyển cảnh mượt mà (Fade + Scale nhẹ) khi mở khóa vào Gallery hoặc khóa về trang chủ
                    AnimatedContent(
                        targetState = isVaultUnlocked,
                        transitionSpec = {
                            if (targetState) {
                                // Mở khóa: Gallery phóng nhẹ từ 95% -> 100% kèm fade-in
                                (fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                                        scaleIn(initialScale = 0.95f, animationSpec = tween(300, easing = FastOutSlowInEasing)))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(200)) +
                                                scaleOut(targetScale = 1.04f, animationSpec = tween(200))
                                    )
                            } else {
                                // Khóa Vault: thu nhẹ về trang chủ
                                (fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                        scaleIn(initialScale = 1.04f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(200)) +
                                                scaleOut(targetScale = 0.95f, animationSpec = tween(200))
                                    )
                            }
                        },
                        label = "vault_unlock_screen_transition",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) { unlocked ->
                        if (!unlocked) {
                            WelcomeScreen(
                                viewModel = vaultViewModel,
                                modifier = screenModifier
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color(0xFF000000))
                            ) {
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

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}