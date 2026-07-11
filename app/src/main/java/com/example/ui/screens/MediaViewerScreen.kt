package com.example.ui.screens

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.coil.EncryptedUriFetcher
import com.example.crypto.BlockDecryptingInputStream
import com.example.media.EncryptedDataSourceFactory
import com.example.ui.VaultItem
import com.example.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import coil.size.Size as CoilSize

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    viewModel: VaultViewModel,
    item: VaultItem,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = { /* đóng do animateClose() + BackHandler bên trong tự lo */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { w ->
                val attrs = w.attributes
                attrs.flags = attrs.flags or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                attrs.gravity = Gravity.TOP or Gravity.START
                attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                attrs.width = WindowManager.LayoutParams.MATCH_PARENT
                attrs.height = WindowManager.LayoutParams.MATCH_PARENT
                attrs.dimAmount = 0f
                attrs.windowAnimations = 0

                // ÉP 120HZ: tìm mode cùng độ phân giải hiện tại nhưng refresh rate cao nhất
                try {
                    val display = w.windowManager.defaultDisplay
                    val currentMode = display.mode
                    val bestMode = display.supportedModes
                        .filter {
                            it.physicalWidth == currentMode.physicalWidth &&
                                    it.physicalHeight == currentMode.physicalHeight
                        }
                        .maxByOrNull { it.refreshRate }

                    if (bestMode != null) {
                        attrs.preferredDisplayModeId = bestMode.modeId
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                w.attributes = attrs
                w.setBackgroundDrawableResource(android.R.color.transparent)

                // API 34+: báo thẳng cho hệ thống view này cần frame rate cao
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    w.decorView.requestedFrameRate = android.view.View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                }
            }
        }

        val context = LocalContext.current
        val dek by viewModel.dek.collectAsState()
        val items by viewModel.items.collectAsState()
        var showDeleteDialog by remember { mutableStateOf(false) }

        var showInfoDialog by remember { mutableStateOf(false) }
        var imageWidth by remember { mutableStateOf<Int?>(null) }
        var imageHeight by remember { mutableStateOf<Int?>(null) }
        var decryptedSize by remember { mutableStateOf<Long?>(null) }
        var loadingInfo by remember { mutableStateOf(false) }

        val clickedBounds by viewModel.clickedItemBounds.collectAsState()
        val boundsMap by viewModel.thumbnailBounds.collectAsState()

        var showInteractiveViewer by remember { mutableStateOf(false) }
        var isEntering by remember { mutableStateOf(true) }

        var showControls by remember { mutableStateOf(false) }
        var isClosing by remember { mutableStateOf(false) }
        var keepPagerAlive by remember { mutableStateOf(false) }

        val progress = remember { Animatable(0f) }
        val fullImageAlpha = remember { Animatable(0f) }
        // Alpha riêng cho lúc ĐÓNG (zoom-out): ảnh/video full chất lượng sẽ fade dần
        // về 0 để lộ ảnh thumb (đã opaque sẵn) bên dưới trong lúc khung hình thu nhỏ.
        val closeFullQualityAlpha = remember { Animatable(1f) }

        var thumbImageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        var fullImageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        var isDecodingFullFailed by remember { mutableStateOf(false) }
        var thumbReady by remember { mutableStateOf(false) }

        var originalWidth by remember {
            mutableFloatStateOf(clickedBounds?.let { if (it.size >= 6) it[4] else 0f } ?: 0f)
        }
        var originalHeight by remember {
            mutableFloatStateOf(clickedBounds?.let { if (it.size >= 6) it[5] else 0f } ?: 0f)
        }

        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()

        val startBounds = remember {
            clickedBounds?.let {
                if (it[2] > 0f && it[3] > 0f) it else null
            } ?: floatArrayOf(screenW / 2f - 10f, screenH / 2f - 10f, 20f, 20f)
        }
        val startLeft = startBounds[0]
        val startTop = startBounds[1]
        val startWidth = startBounds[2]
        val startHeight = startBounds[3]

        var dragScale by remember { mutableFloatStateOf(1f) }
        var dragX by remember { mutableFloatStateOf(0f) }
        var dragY by remember { mutableFloatStateOf(0f) }

        val initialIndex = remember(items, item) {
            items.indexOfFirst { it.encryptedName == item.encryptedName }.coerceAtLeast(0)
        }

        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })
        val currentItem = items.getOrNull(pagerState.currentPage) ?: item

        val coroutineScope = rememberCoroutineScope()
        val animateClose: () -> Unit = {
            coroutineScope.launch {
                viewModel.setAnimatingItem(currentItem.encryptedName)

                isClosing = true
                showInteractiveViewer = false // Hủy ngay HDR thông qua LaunchedEffect phía dưới
                // LƯU Ý: KHÔNG tắt keepPagerAlive ngay ở đây nữa — giữ pager (ảnh/video full
                // chất lượng) sống thêm một nhịp để có thể fade-out mượt thay vì biến mất
                // đột ngột. Sẽ tắt hẳn sau khi animation hoàn tất, phía dưới.

                // Đảm bảo ảnh thumb đã được decode/cache sẵn TRƯỚC khi bắt đầu animation.
                // Thường đã sẵn có từ lúc mở ảnh nên hầu như trả về ngay lập tức; đây chỉ là
                // lớp bảo hiểm để tránh giật/lag nếu vì lý do gì đó thumb chưa kịp decode.
                withTimeoutOrNull(60) {
                    snapshotFlow { thumbReady }.first { it }
                }

                // CROSSFADE: fade ảnh/video full chất lượng về alpha 0 để lộ dần ảnh thumb
                // (đã vẽ opaque sẵn ở Lớp 2) ngay trong lúc animation zoom-out đang diễn ra.
                // Chạy song song và NHANH HƠN animation lò xo phía dưới, để phần lớn quãng
                // đường thu nhỏ chỉ còn phải vẽ bitmap thumb (nhẹ hơn nhiều) → mượt hơn, đỡ lag.
                launch {
                    closeFullQualityAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 200)
                    )
                }

                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 400f,
                        dampingRatio = 1.0f
                    )
                )

                // CHỈ RESET SAU KHI HOÀN THÀNH ANIMATION LÒ XO để tránh giật hình
                dragScale = 1f
                dragX = 0f
                dragY = 0f

                keepPagerAlive = false
                closeFullQualityAlpha.snapTo(1f) // reset để sẵn sàng cho lần mở kế tiếp
                isClosing = false
                viewModel.setAnimatingItem(null)
                viewModel.selectMedia(null)
            }
        }

        BackHandler {
            animateClose()
        }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }

            withTimeoutOrNull(80) {
                snapshotFlow { thumbReady }.first { it }
            }

            viewModel.setAnimatingItem(item.encryptedName)

            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 600f,
                    dampingRatio = 1.0f
                )
            )
            keepPagerAlive = true
            showInteractiveViewer = true
            isEntering = false
        }

        DisposableEffect(Unit) {
            dialogWindow?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }

        // TẮT HDR LẬP TỨC NGAY KHI showInteractiveViewer == false (Không chờ delay lò xo nữa)
        LaunchedEffect(showInteractiveViewer) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (showInteractiveViewer) {
                    dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_HDR
                } else {
                    dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }

        val customImageLoader = remember(dek) {
            dek?.let { key ->
                ImageLoader.Builder(context)
                    .components { add(EncryptedUriFetcher.Factory(key)) }
                    .build()
            }
        }

        LaunchedEffect(currentItem, dek) {
            val key = dek ?: return@LaunchedEffect
            thumbReady = false

            val cached = ThumbBitmapCache.get(currentItem.encryptedName)
            if (cached != null) {
                thumbImageBitmap = cached
                thumbReady = true
                if (originalWidth <= 0f) {
                    originalWidth = cached.width.toFloat()
                    originalHeight = cached.height.toFloat()
                }
                return@LaunchedEffect
            }

            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(currentItem.thumbUri ?: currentItem.uri)?.use { inputStream ->
                        val decryptedStream = BlockDecryptingInputStream(inputStream, key)
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

                        BitmapFactory.decodeStream(decryptedStream, null, options)

                        if (originalWidth <= 0f) {
                            originalWidth = options.outWidth.toFloat()
                            originalHeight = options.outHeight.toFloat()
                        }
                    }

                    context.contentResolver.openInputStream(currentItem.thumbUri ?: currentItem.uri)?.use { inputStream ->
                        val decryptedStream = BlockDecryptingInputStream(inputStream, key)
                        val finalOptions = BitmapFactory.Options().apply {
                            inSampleSize = calculateInSampleSize(this, (screenW / 2).toInt(), (screenH / 2).toInt())
                            inJustDecodeBounds = false
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }

                        val bmp = BitmapFactory.decodeStream(decryptedStream, null, finalOptions)
                        if (bmp != null) {
                            val imgBmp = bmp.asImageBitmap()
                            ThumbBitmapCache.put(currentItem.encryptedName, imgBmp)
                            withContext(Dispatchers.Main) {
                                thumbImageBitmap = imgBmp
                                thumbReady = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    thumbReady = true
                }
            }
        }

        // QUAN TRỌNG: KHÔNG còn phụ thuộc vào showInteractiveViewer nữa — bắt đầu giải mã +
        // decode ảnh kích thước màn hình (screen-size) NGAY khi item được chọn, chạy song song
        // với animation zoom-in, giống Google Photos (load screen-size ngay từ đầu thay vì chờ
        // animation zoom xong mới bắt đầu load). Ảnh sẽ tự fade vào giữa lúc đang zoom nhờ
        // LaunchedEffect(fullImageBitmap) fade-in bên dưới, thay vì luôn xuất hiện SAU animation.
        LaunchedEffect(currentItem, dek) {
            if (currentItem.isVideo) return@LaunchedEffect
            val key = dek ?: return@LaunchedEffect
            fullImageBitmap = null
            isDecodingFullFailed = false

            withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(currentItem.uri)?.use { testIn ->
                        val testDec = BlockDecryptingInputStream(testIn, key)
                        BitmapFactory.decodeStream(testDec, null, options)
                    }

                    if (originalWidth <= 0f) {
                        originalWidth = options.outWidth.toFloat()
                        originalHeight = options.outHeight.toFloat()
                    }

                    context.contentResolver.openInputStream(currentItem.uri)?.use { inputStream ->
                        val decryptedStream = BlockDecryptingInputStream(inputStream, key)
                        val finalOptions = BitmapFactory.Options().apply {
                            inSampleSize = calculateInSampleSize(options, screenW.toInt(), screenH.toInt())
                            inJustDecodeBounds = false
                        }
                        val finalBmp = BitmapFactory.decodeStream(decryptedStream, null, finalOptions)
                        val finalImgBmp = finalBmp?.asImageBitmap()
                        withContext(Dispatchers.Main) { fullImageBitmap = finalImgBmp }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isDecodingFullFailed = true
                }
            }
        }

        LaunchedEffect(fullImageBitmap) {
            if (fullImageBitmap != null) {
                fullImageAlpha.animateTo(1f, animationSpec = tween(durationMillis = 300))
            } else {
                fullImageAlpha.snapTo(0f)
            }
        }

        var pagerScrollEnabled by remember { mutableStateOf(true) }

        LaunchedEffect(currentItem, dek, showInteractiveViewer) {
            if (!showInteractiveViewer) return@LaunchedEffect
            val key = dek ?: return@LaunchedEffect
            imageWidth = null
            imageHeight = null
            decryptedSize = null
            loadingInfo = true
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(currentItem.uri)?.use { inputStream ->
                        val decryptedStream = BlockDecryptingInputStream(inputStream, key)
                        if (currentItem.isVideo) {
                            val tempFile = java.io.File(context.cacheDir, "temp_info_video")
                            try {
                                tempFile.outputStream().use { out -> decryptedStream.copyTo(out) }
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(tempFile.absolutePath)
                                    imageWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                                    imageHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                                } finally {
                                    retriever.release()
                                }
                                decryptedSize = tempFile.length()
                            } finally {
                                if (tempFile.exists()) tempFile.delete()
                            }
                        } else {
                            var totalDecryptedBytes = 0L
                            val countingStream = object : java.io.FilterInputStream(decryptedStream) {
                                override fun read(): Int { val b = super.read(); if (b != -1) totalDecryptedBytes++; return b }
                                override fun read(b: ByteArray, off: Int, len: Int): Int {
                                    val r = super.read(b, off, len); if (r != -1) totalDecryptedBytes += r; return r
                                }
                            }
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(countingStream, null, options)
                            val buffer = ByteArray(8192)
                            var read = countingStream.read(buffer)
                            while (read != -1) { read = countingStream.read(buffer) }
                            imageWidth = options.outWidth
                            imageHeight = options.outHeight
                            decryptedSize = totalDecryptedBytes
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    loadingInfo = false
                }
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            val activeItem = items.getOrNull(pagerState.currentPage)
            if (activeItem != null) {
                viewModel.selectMedia(activeItem)
            }
            pagerScrollEnabled = true
        }

        LaunchedEffect(showInteractiveViewer, showControls, isClosing) {
            if (isClosing) return@LaunchedEffect
            dialogWindow?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (showInteractiveViewer && !showControls) {
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                }
            }
        }

        val transitionShape = remember(startWidth, startHeight, screenW, screenH) {
            object : Shape {
                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                    val t = progress.value
                    val w = lerp(startWidth, screenW, t)
                    val h = lerp(startHeight, screenH, t)
                    return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            if (keepPagerAlive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Lúc đang đóng: fade mượt theo closeFullQualityAlpha thay vì
                            // biến mất tức thời, để crossfade với ảnh thumb ở Lớp 2 bên dưới.
                            alpha = if (showInteractiveViewer) 1f else closeFullQualityAlpha.value
                        }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = pagerScrollEnabled && showInteractiveViewer,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageItem = items[page]
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (pageItem.isVideo) {
                                dek?.let { key ->
                                    EncryptedVideoPlayer(
                                        context = context,
                                        item = pageItem,
                                        dek = key,
                                        isActive = (page == pagerState.currentPage) && showInteractiveViewer,
                                        showControls = showControls,
                                        placeholderBitmap = if (pageItem.encryptedName == currentItem.encryptedName) (fullImageBitmap ?: thumbImageBitmap) else null,
                                        onTap = { showControls = !showControls },
                                        onDismissDragStart = {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                                            }
                                        },
                                        onDismissDragCancel = { // PHỤC HỒI HDR NẾU HỦY KÉO
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_HDR
                                            }
                                        },
                                        onDismiss = { scaleVal, xVal, yVal ->
                                            dragScale = scaleVal
                                            // Bù trừ trục X và Y để match với TransformOrigin(0f, 0f) của Lớp 2
                                            dragX = xVal + (screenW / 2f) * (1f - scaleVal)
                                            dragY = yVal + (screenH / 2f) * (1f - scaleVal)
                                            animateClose()
                                        }
                                    )
                                }
                            } else {
                                customImageLoader?.let { loader ->
                                    EncryptedImageViewer(
                                        context = context,
                                        item = pageItem,
                                        imageLoader = loader,
                                        placeholderBitmap = if (pageItem.encryptedName == currentItem.encryptedName) (fullImageBitmap ?: thumbImageBitmap) else null,
                                        onDismissDragStart = {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                                            }
                                        },
                                        onDismissDragCancel = { // PHỤC HỒI HDR NẾU HỦY KÉO
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_HDR
                                            }
                                        },
                                        onDismiss = { scaleVal, xVal, yVal ->
                                            dragScale = scaleVal
                                            // Bù trừ trục X và Y để match với TransformOrigin(0f, 0f) của Lớp 2
                                            dragX = xVal + (screenW / 2f) * (1f - scaleVal)
                                            dragY = yVal + (screenH / 2f) * (1f - scaleVal)
                                            animateClose()
                                        },
                                        onScaleChanged = { scale ->
                                            if (page == pagerState.currentPage) {
                                                pagerScrollEnabled = scale <= 1.05f
                                            }
                                        },
                                        onTap = { showControls = !showControls }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // LỚP 2: LÒ XO MƯỢT MÀ OVERLAY
            if (!showInteractiveViewer || isEntering) {
                val currentTargetBounds = if (isEntering) startBounds else (boundsMap[currentItem.encryptedName] ?: startBounds)
                val trStartLeft = currentTargetBounds[0]
                val trStartTop = currentTargetBounds[1]

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val t = progress.value
                            // KHỚP CÔNG THỨC ALPHA: Mờ dần chuẩn từ ngưỡng vuốt
                            drawRect(Color.Black, alpha = t * (1f - (kotlin.math.abs(dragY) / (screenH / 2f)).coerceIn(0f, 1f)))
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = progress.value
                                val currentScale = lerp(1f, dragScale, t)

                                translationX = lerp(trStartLeft, dragX, t)
                                translationY = lerp(trStartTop, dragY, t)
                                scaleX = currentScale
                                scaleY = currentScale
                                transformOrigin = TransformOrigin(0f, 0f)

                                clip = true
                                shape = transitionShape
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val t = progress.value
                            val currentBoxW = lerp(startWidth, screenW, t)
                            val currentBoxH = lerp(startHeight, screenH, t)

                            // Full chỉ coi là "opaque hoàn toàn" khi CẢ 2 alpha (mở + đóng) đều gần 1.
                            // Nhờ vậy, ngay khi bắt đầu đóng (closeFullQualityAlpha bắt đầu giảm),
                            // ảnh thumb sẽ được vẽ lộ ra ở dưới để crossfade.
                            val isFullOpaque = !currentItem.isVideo && fullImageBitmap != null &&
                                    fullImageAlpha.value >= 0.95f && closeFullQualityAlpha.value >= 0.95f

                            if (!isFullOpaque) {
                                thumbImageBitmap?.let { imgBmp ->
                                    val srcW = imgBmp.width.toFloat()
                                    val srcH = imgBmp.height.toFloat()
                                    if (srcW > 0f && srcH > 0f) {
                                        val coverScale = maxOf(startWidth / srcW, startHeight / srcH)
                                        val fitScale = minOf(screenW / srcW, screenH / srcH)
                                        val curScale = lerp(coverScale, fitScale, t)

                                        val curImgW = srcW * curScale
                                        val curImgH = srcH * curScale

                                        val imgX = (currentBoxW - curImgW) / 2f
                                        val imgY = (currentBoxH - curImgH) / 2f

                                        translate(imgX, imgY) {
                                            scale(curScale, curScale, pivot = Offset.Zero) {
                                                drawImage(
                                                    image = imgBmp,
                                                    filterQuality = FilterQuality.Low
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (!currentItem.isVideo) {
                                fullImageBitmap?.let { imgBmp ->
                                    val srcW = imgBmp.width.toFloat()
                                    val srcH = imgBmp.height.toFloat()
                                    if (srcW > 0f && srcH > 0f) {
                                        val coverScale = maxOf(startWidth / srcW, startHeight / srcH)
                                        val fitScale = minOf(screenW / srcW, screenH / srcH)
                                        val curScale = lerp(coverScale, fitScale, t)

                                        val curImgW = srcW * curScale
                                        val curImgH = srcH * curScale

                                        val imgX = (currentBoxW - curImgW) / 2f
                                        val imgY = (currentBoxH - curImgH) / 2f

                                        translate(imgX, imgY) {
                                            scale(curScale, curScale, pivot = Offset.Zero) {
                                                drawImage(
                                                    image = imgBmp,
                                                    // Kết hợp alpha "mở" (fade-in lúc vào) và alpha "đóng"
                                                    // (fade-out lúc zoom-out) để crossfade cả 2 chiều.
                                                    alpha = (fullImageAlpha.value * closeFullQualityAlpha.value).coerceIn(0f, 1f),
                                                    filterQuality = FilterQuality.Low
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

            AnimatedVisibility(
                visible = showControls && showInteractiveViewer,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { animateClose() },
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Gallery",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = currentItem.originalName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )

                        Row {
                            IconButton(
                                onClick = { showInfoDialog = true },
                                modifier = Modifier.testTag("viewer_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "File Properties",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.testTag("viewer_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete File",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (showInfoDialog) {
                val formatSize = remember {
                    { bytes: Long? ->
                        if (bytes == null) "Unknown"
                        else if (bytes < 1024) "$bytes B"
                        else {
                            val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
                            val unit = "KMGTPE"[exp - 1] + "B"
                            String.format("%.2f %s", bytes / Math.pow(1024.0, exp.toDouble()), unit)
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text("File Properties", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "Original Name: ${currentItem.originalName}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Encrypted Name: ${currentItem.encryptedName}", fontSize = 12.sp, color = Color.Gray)
                            Text(text = "Type: ${if (currentItem.isVideo) "Video (Secure)" else "Image (Secure)"}", fontSize = 14.sp)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            if (loadingInfo) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Decrypting & reading metadata...", fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            } else {
                                val decSizeText = formatSize(decryptedSize)
                                val encSizeText = formatSize(currentItem.size)
                                val dimensionsText = if (imageWidth != null && imageHeight != null) "${imageWidth} x ${imageHeight} px" else "Unknown"

                                Text(text = "Decoded (Real) Size: $decSizeText", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(text = "Encrypted (On-Disk) Size: $encSizeText", fontSize = 14.sp, color = Color.Gray)
                                Text(text = "Dimensions: $dimensionsText", fontSize = 14.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showInfoDialog = false }, modifier = Modifier.testTag("dialog_close_info")) {
                            Text("CLOSE")
                        }
                    }
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Secure Media?") },
                    text = { Text("This will permanently decrypt and erase '${currentItem.originalName}' and its thumbnail sidecar from storage. This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                viewModel.deleteMedia(context, currentItem)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("dialog_confirm_delete")
                        ) {
                            Text("DELETE", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("CANCEL") }
                    }
                )
            }
        }
    }
}

@Composable
fun EncryptedImageViewer(
    context: Context,
    item: VaultItem,
    imageLoader: ImageLoader,
    placeholderBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    onDismiss: (scale: Float, x: Float, y: Float) -> Unit,
    onDismissDragStart: () -> Unit = {},
    onDismissDragCancel: () -> Unit = {},
    onScaleChanged: (Float) -> Unit,
    onTap: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // THÊM 2 DÒNG NÀY: Lấy screenH từ context
    val displayMetrics = context.resources.displayMetrics
    val screenH = displayMetrics.heightPixels.toFloat()

    LaunchedEffect(scale.value) { onScaleChanged(scale.value) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // SỬA LẠI DÒNG NÀY: Dùng screenH / 2f thay vì constraints.maxHeight / 2f
            .background(Color.Black.copy(alpha = (1f - (kotlin.math.abs(offsetY.value) / (screenH / 2f))).coerceIn(0f, 1f)))
            .testTag("image_viewer_container"),
        contentAlignment = Alignment.Center
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // NGƯỠNG MỚI THẤP HƠN NHIỀU (15% chiều cao màn hình)
        val dismissThreshold = height * 0.15f

        // ... (Phần code bên dưới giữ nguyên không thay đổi)

        val gestureModifier = Modifier.pointerInput(width, height) {
            var lastTapTime = 0L
            var lastTapPosition = Offset.Zero

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val currentTime = System.currentTimeMillis()
                var isDoubleTap = false

                if (currentTime - lastTapTime < 300L) {
                    val dragDistance = (down.position - lastTapPosition).getDistance()
                    if (dragDistance < 100f) {
                        isDoubleTap = true
                    }
                }

                var gestureScale = scale.value
                var gestureOffsetX = offsetX.value
                var gestureOffsetY = offsetY.value

                if (isDoubleTap) {
                    lastTapTime = 0L
                    lastTapPosition = Offset.Zero
                    down.consume()

                    val targetScale = if (scale.value > 1.05f) 1f else 2.5f
                    coroutineScope.launch { scale.animateTo(targetScale, spring()) }
                    if (targetScale == 1f) {
                        coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                        coroutineScope.launch { offsetY.animateTo(0f, spring()) }
                    } else {
                        val maxOffsetX = (width * 2.5f - width) / 2f
                        val maxOffsetY = (height * 2.5f - height) / 2f
                        val targetX = ((width / 2f) - down.position.x) * 1.5f
                        val targetY = ((height / 2f) - down.position.y) * 1.5f
                        coroutineScope.launch { offsetX.animateTo(targetX.coerceIn(-maxOffsetX, maxOffsetX), spring()) }
                        coroutineScope.launch { offsetY.animateTo(targetY.coerceIn(-maxOffsetY, maxOffsetY), spring()) }
                    }
                } else {
                    lastTapTime = currentTime
                    lastTapPosition = down.position

                    var isPinching = false
                    var isDraggingToDismiss = false
                    var hasMovedSignificantDistance = false
                    val startPos = down.position

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes

                        if (pointers.isEmpty() || pointers.all { !it.pressed }) break

                        val firstPointer = pointers.firstOrNull()
                        if (firstPointer != null) {
                            val dist = (firstPointer.position - startPos).getDistance()
                            if (dist > 15f) hasMovedSignificantDistance = true
                        }

                        if (pointers.size >= 2) {
                            isPinching = true
                            val pointer1 = pointers[0]
                            val pointer2 = pointers[1]
                            val pos1 = pointer1.position
                            val pos2 = pointer2.position
                            val prevPos1 = pointer1.previousPosition
                            val prevPos2 = pointer2.previousPosition

                            val currentDistance = (pos1 - pos2).getDistance()
                            val previousDistance = (prevPos1 - prevPos2).getDistance()

                            if (previousDistance > 0f && currentDistance > 0f) {
                                val zoomFactor = currentDistance / previousDistance
                                val newScale = (gestureScale * zoomFactor).coerceIn(0.8f, 10f)

                                val currentCentroid = (pos1 + pos2) / 2f
                                val previousCentroid = (prevPos1 + prevPos2) / 2f
                                val centroidDelta = currentCentroid - previousCentroid

                                val centroidX = currentCentroid.x - (width / 2f)
                                val centroidY = currentCentroid.y - (height / 2f)

                                val actualZoomFactor = if (gestureScale != 0f) newScale / gestureScale else 1f
                                val newX = centroidX - (centroidX - gestureOffsetX) * actualZoomFactor + centroidDelta.x
                                val newY = centroidY - (centroidY - gestureOffsetY) * actualZoomFactor + centroidDelta.y

                                gestureScale = newScale
                                if (newScale > 1f) {
                                    val maxOffsetX = (width * newScale - width) / 2f
                                    val maxOffsetY = (height * newScale - height) / 2f
                                    gestureOffsetX = newX.coerceIn(-maxOffsetX, maxOffsetX)
                                    gestureOffsetY = newY.coerceIn(-maxOffsetY, maxOffsetY)
                                } else {
                                    gestureOffsetX = 0f
                                    gestureOffsetY = 0f
                                }

                                coroutineScope.launch {
                                    scale.snapTo(gestureScale)
                                    offsetX.snapTo(gestureOffsetX)
                                    offsetY.snapTo(gestureOffsetY)
                                }
                            }
                            pointers.forEach { it.consume() }
                        } else if (pointers.size == 1 && !isPinching) {
                            val pointer = pointers[0]
                            val currentPos = pointer.position
                            val prevPos = pointer.previousPosition
                            val delta = currentPos - prevPos

                            if (gestureScale > 1.05f) {
                                val maxOffsetX = (width * gestureScale - width) / 2f
                                val maxOffsetY = (height * gestureScale - height) / 2f
                                gestureOffsetX = (gestureOffsetX + delta.x).coerceIn(-maxOffsetX, maxOffsetX)
                                gestureOffsetY = (gestureOffsetY + delta.y).coerceIn(-maxOffsetY, maxOffsetY)
                                coroutineScope.launch {
                                    offsetX.snapTo(gestureOffsetX)
                                    offsetY.snapTo(gestureOffsetY)
                                }
                                pointer.consume()
                            } else {
                                val absoluteDeltaX = kotlin.math.abs(delta.x)
                                val absoluteDeltaY = kotlin.math.abs(delta.y)
                                if (isDraggingToDismiss || (delta.y > 0f && absoluteDeltaY > absoluteDeltaX * 1.5f && absoluteDeltaY > 10f)) {
                                    if (!isDraggingToDismiss) onDismissDragStart()
                                    isDraggingToDismiss = true

                                    // Cập nhật cả trục Y và trục X để ảnh đi theo ngón tay
                                    gestureOffsetY += delta.y
                                    gestureOffsetX += delta.x

                                    // THU NHỎ KHI KÉO: tối đa giảm về 60% (tạo hiệu ứng kéo lùi về trục Z)
                                    val swipeProgress = (kotlin.math.abs(gestureOffsetY) / (height * 0.8f)).coerceIn(0f, 1f)
                                    gestureScale = (1f - (swipeProgress * 0.4f)).coerceAtLeast(0.6f)

                                    coroutineScope.launch {
                                        offsetX.snapTo(gestureOffsetX) // Snap theo trục X
                                        offsetY.snapTo(gestureOffsetY)
                                        scale.snapTo(gestureScale)
                                    }
                                    pointer.consume()
                                }
                            }
                        }
                    }

                    if (!isPinching && !isDraggingToDismiss && !hasMovedSignificantDistance) {
                        onTap()
                    } else if (scale.value < 1f && !isDraggingToDismiss) {
                        coroutineScope.launch { scale.animateTo(1f, spring()) }
                        coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                        coroutineScope.launch { offsetY.animateTo(0f, spring()) }
                    } else if (isDraggingToDismiss) {
                        if (offsetY.value > dismissThreshold) {
                            onDismiss(scale.value, offsetX.value, offsetY.value)
                        } else {
                            // HỦY VUỐT TRẢ VỀ BAN ĐẦU + KHÔI PHỤC HDR
                            onDismissDragCancel()
                            coroutineScope.launch { offsetX.animateTo(0f, spring()) } // Thêm dòng này để trả X về 0
                            coroutineScope.launch { offsetY.animateTo(0f, spring()) }
                            coroutineScope.launch { scale.animateTo(1f, spring()) }
                        }
                    } else if (scale.value > 1f) {
                        val maxOffsetX = (width * scale.value - width) / 2f
                        val maxOffsetY = (height * scale.value - height) / 2f
                        val targetX = offsetX.value.coerceIn(-maxOffsetX, maxOffsetX)
                        val targetY = offsetY.value.coerceIn(-maxOffsetY, maxOffsetY)
                        coroutineScope.launch { offsetX.animateTo(targetX, spring()) }
                        coroutineScope.launch { offsetY.animateTo(targetY, spring()) }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = Alignment.Center
        ) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(CoilSize.ORIGINAL)
                    .build(),
                imageLoader = imageLoader
            )
            val painterState = painter.state

            if (painterState !is coil.compose.AsyncImagePainter.State.Success && placeholderBitmap != null) {
                Image(
                    bitmap = placeholderBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            Image(
                painter = painter,
                contentDescription = item.originalName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
                    .testTag("encrypted_image"),
                contentScale = ContentScale.Fit
            )

            if (scale.value > 1f) {
                Text(
                    text = "Pinch to zoom active (${"%.1f".format(scale.value)}x)",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EncryptedVideoPlayer(
    context: Context,
    item: VaultItem,
    dek: ByteArray,
    isActive: Boolean,
    showControls: Boolean,
    placeholderBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onTap: () -> Unit,
    onDismissDragStart: () -> Unit = {},
    onDismissDragCancel: () -> Unit = {},
    onDismiss: (Float, Float, Float) -> Unit
) {
    var isVideoReady by remember { mutableStateOf(false) }

    val exoPlayer = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaSource = ProgressiveMediaSource.Factory(
                EncryptedDataSourceFactory(context, dek)
            ).createMediaSource(MediaItem.fromUri(item.uri))
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = false

            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                }
            })
        }
    }

    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) {
            exoPlayer.pause()
        }
    }

    DisposableEffect(item.uri) {
        onDispose {
            exoPlayer.release()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(250)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val displayMetrics = context.resources.displayMetrics
    val screenH = displayMetrics.heightPixels.toFloat()

    // NGƯỠNG MỚI THẤP HƠN NHIỀU (15% chiều cao màn hình)
    val dismissThreshold = screenH * 0.15f

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var gestureOffsetX = offsetX.value
            var gestureOffsetY = offsetY.value
            var gestureScale = scale.value
            var isDraggingToDismiss = false
            var hasMoved = false
            val startPos = down.position

            while (true) {
                val event = awaitPointerEvent()
                val pointers = event.changes
                if (pointers.isEmpty() || pointers.all { !it.pressed }) {
                    break
                }

                if (pointers.size == 1) {
                    val pointer = pointers[0]
                    val currentPos = pointer.position
                    val prevPos = pointer.previousPosition
                    val delta = currentPos - prevPos

                    val absoluteDeltaX = kotlin.math.abs(delta.x)
                    val absoluteDeltaY = kotlin.math.abs(delta.y)

                    if (isDraggingToDismiss || (delta.y > 0f && absoluteDeltaY > absoluteDeltaX * 1.5f && absoluteDeltaY > 10f)) {
                        if (!isDraggingToDismiss) onDismissDragStart()
                        isDraggingToDismiss = true

                        // Cập nhật cả trục Y và trục X
                        gestureOffsetY += delta.y
                        gestureOffsetX += delta.x

                        // THU NHỎ KHI KÉO: tối đa giảm về 60%
                        val swipeProgress = (kotlin.math.abs(gestureOffsetY) / (screenH * 0.8f)).coerceIn(0f, 1f)
                        gestureScale = (1f - (swipeProgress * 0.4f)).coerceAtLeast(0.6f)

                        coroutineScope.launch {
                            offsetX.snapTo(gestureOffsetX) // Snap theo trục X
                            offsetY.snapTo(gestureOffsetY)
                            scale.snapTo(gestureScale)
                        }
                        pointer.consume()
                    }
                    if ((currentPos - startPos).getDistance() > 15f) {
                        hasMoved = true
                    }
                }
            }

            if (isDraggingToDismiss) {
                if (offsetY.value > dismissThreshold) {
                    onDismiss(scale.value, offsetX.value, offsetY.value)
                } else {
                    // HỦY VUỐT TRẢ VỀ BAN ĐẦU + KHÔI PHỤC HDR
                    onDismissDragCancel()
                    coroutineScope.launch { offsetX.animateTo(0f, spring()) } // Trả trục X về 0
                    coroutineScope.launch { offsetY.animateTo(0f, spring()) }
                    coroutineScope.launch { scale.animateTo(1f, spring()) }
                }
            } else if (!hasMoved) {
                onTap()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // KHỚP CÔNG THỨC ALPHA: Đồng bộ với Lớp 2 phía trên
            .background(Color.Black.copy(alpha = (1f - (kotlin.math.abs(offsetY.value) / (screenH / 2f))).coerceIn(0f, 1f)))
            .then(gestureModifier)
            .testTag("video_player_container"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
                    .testTag("exoplayer_surface_view")
            )

            if (!isVideoReady && placeholderBitmap != null) {
                Image(
                    bitmap = placeholderBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            if (!isPlaying && showControls) {
                IconButton(
                    onClick = { exoPlayer.play() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = formatTime(currentPosition), fontSize = 12.sp, color = Color.White)

                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { exoPlayer.seekTo(it.toLong()) },
                            valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                thumbColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .testTag("video_seek_slider")
                        )

                        Text(text = formatTime(duration), fontSize = 12.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) }) {
                            Icon(imageVector = Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                                .testTag("video_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration)) }) {
                            Icon(imageVector = Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}