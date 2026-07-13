package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
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

        // Tắt animation mặc định của hệ điều hành khi Activity này chuyển cảnh (mở Document
        // Picker để import nhiều file, rồi quay lại). Animation hệ thống (phóng to/mờ dần nền
        // đen) chạy song song và đè lên animation Compose tự vẽ (AnimatedVisibility) của popup
        // tiến trình import, gây ra hiệu ứng "nền đen phóng to lên" người dùng thấy. Từ giờ chỉ
        // còn animation do chính app tự viết (trong GalleryScreen) đảm nhiệm việc này.
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

    override fun onResume() {
        super.onResume()
        // overridePendingTransition() (API < 34) chỉ áp dụng cho lượt chuyển cảnh kế tiếp, nên
        // cần gọi lại mỗi lần quay lại Activity (ví dụ sau khi đóng Document Picker) để animation
        // hệ thống luôn bị tắt, không riêng gì lần khởi động đầu tiên.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}