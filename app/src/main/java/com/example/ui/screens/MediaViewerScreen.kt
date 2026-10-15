package com.example.ui.screens

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MotionPhotosOn
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
import androidx.compose.ui.draw.clip
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
import com.example.crypto.CryptoEngine
import com.example.media.EncryptedDataSourceFactory
import com.example.ui.VaultItem
import com.example.ui.VaultViewModel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object FullBitmapCache {
    private const val MAX_ENTRIES = 4
    private val cache = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(
        MAX_ENTRIES, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): androidx.compose.ui.graphics.ImageBitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        cache[key] = bitmap
    }
}

object ScreenBitmapCache {
    private const val MAX_ENTRIES = 4
    private val cache = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(
        MAX_ENTRIES, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): androidx.compose.ui.graphics.ImageBitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        cache[key] = bitmap
    }
}

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
        val closeTransitionAlpha = remember { Animatable(1f) }

        var thumbImageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        var screenImageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        var screenImageBitmapOwner by remember { mutableStateOf<String?>(null) }
        val screenImageAlpha = remember { Animatable(0f) }
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

        var isHdrActive by remember { mutableStateOf(false) }
        var isVideoReadyForHdr by remember { mutableStateOf(false) }
        var isImageReadyForHdr by remember { mutableStateOf(false) }

        LaunchedEffect(currentItem) {
            isVideoReadyForHdr = false
            isImageReadyForHdr = false
        }

        val coroutineScope = rememberCoroutineScope()
        val animateClose: () -> Unit = {
            coroutineScope.launch {
                viewModel.setAnimatingItem(currentItem.encryptedName)

                isClosing = true
                showInteractiveViewer = false
                isHdrActive = false

                withTimeoutOrNull(60) {
                    snapshotFlow { thumbReady }.first { it }
                }

                launch {
                    closeTransitionAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 200)
                    )
                }

                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 600f,
                        dampingRatio = 1.0f,
                        visibilityThreshold = 0.0001f
                    )
                )

                dragScale = 1f
                dragX = 0f
                dragY = 0f

                keepPagerAlive = false
                isClosing = false
                viewModel.setAnimatingItem(null)

                delay(15L)
                viewModel.selectMedia(null)
            }
        }

        BackHandler { animateClose() }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            withTimeoutOrNull(80) { snapshotFlow { thumbReady }.first { it } }

            viewModel.setAnimatingItem(item.encryptedName)

            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 600f,
                    dampingRatio = 1.0f,
                    visibilityThreshold = 0.0001f
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
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
            onDispose {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    dialogWindow?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }

        LaunchedEffect(isHdrActive) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                dialogWindow?.colorMode = if (isHdrActive) {
                    ActivityInfo.COLOR_MODE_HDR
                } else {
                    ActivityInfo.COLOR_MODE_DEFAULT
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

        // TẢI TIER 1: THUMBNAIL (Cho animation đóng/mở)
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
                    CryptoEngine.getSgv2TierStream(context, currentItem.uri, CryptoEngine.Tier.THUMB, key)?.use { stream ->
                        val bytes = stream.readBytes()
                        val finalBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (finalBmp != null) {
                            val imgBmp = finalBmp.asImageBitmap()
                            ThumbBitmapCache.put(currentItem.encryptedName, imgBmp)
                            withContext(Dispatchers.Main) {
                                thumbImageBitmap = imgBmp
                                thumbReady = true
                                if (originalWidth <= 0f) {
                                    originalWidth = finalBmp.width.toFloat()
                                    originalHeight = finalBmp.height.toFloat()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    thumbReady = true
                }
            }
        }

        // TẢI TIER 2: SCREEN SIZE
        LaunchedEffect(currentItem, dek) {
            val key = dek ?: return@LaunchedEffect
            val targetName = currentItem.encryptedName

            val cachedScreen = ScreenBitmapCache.get(targetName)
            if (cachedScreen != null) {
                screenImageBitmap = cachedScreen
                screenImageBitmapOwner = targetName
                return@LaunchedEffect
            }

            screenImageBitmap = null
            screenImageBitmapOwner = null
            withContext(Dispatchers.IO) {
                try {
                    CryptoEngine.getSgv2TierStream(context, currentItem.uri, CryptoEngine.Tier.SCREEN, key)?.use { stream ->
                        val bytes = stream.readBytes()
                        val finalBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val imgBmp = finalBmp?.asImageBitmap()
                        if (imgBmp != null) {
                            ScreenBitmapCache.put(targetName, imgBmp)
                        }
                        withContext(Dispatchers.Main) {
                            screenImageBitmap = imgBmp
                            screenImageBitmapOwner = targetName
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        LaunchedEffect(screenImageBitmap) {
            if (screenImageBitmap != null) {
                screenImageAlpha.animateTo(1f, animationSpec = tween(durationMillis = 150))
            } else {
                screenImageAlpha.snapTo(0f)
            }
        }

        LaunchedEffect(isImageReadyForHdr, showInteractiveViewer) {
            if (!currentItem.isVideo) {
                if (isImageReadyForHdr && showInteractiveViewer) {
                    delay(500)
                    if (showInteractiveViewer) isHdrActive = true
                } else {
                    isHdrActive = false
                }
            }
        }

        LaunchedEffect(isVideoReadyForHdr, showInteractiveViewer) {
            if (currentItem.isVideo) {
                if (isVideoReadyForHdr && showInteractiveViewer) {
                    delay(500)
                    if (showInteractiveViewer) isHdrActive = true
                } else {
                    isHdrActive = false
                }
            }
        }

        var pagerScrollEnabled by remember { mutableStateOf(true) }

        LaunchedEffect(currentItem, dek, showInfoDialog) {
            if (!showInfoDialog) return@LaunchedEffect
            val key = dek ?: return@LaunchedEffect
            imageWidth = null
            imageHeight = null
            decryptedSize = null
            loadingInfo = true
            withContext(Dispatchers.IO) {
                try {
                    val info = CryptoEngine.readSgv2FileInfo(context, currentItem.uri, key)
                    if (info != null) {
                        decryptedSize = info.decryptedSize
                        if (info.width > 0 && info.height > 0) {
                            imageWidth = info.width
                            imageHeight = info.height
                        }
                    }

                    if (imageWidth == null || imageHeight == null) {
                        val cachedFull = FullBitmapCache.get(currentItem.encryptedName)
                        if (cachedFull != null) {
                            imageWidth = cachedFull.width
                            imageHeight = cachedFull.height
                        } else if (!currentItem.isVideo) {
                            CryptoEngine.getSgv2TierStream(context, currentItem.uri, CryptoEngine.Tier.FULL, key)?.use { stream ->
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeStream(stream, null, options)
                                if (options.outWidth > 0 && options.outHeight > 0) {
                                    imageWidth = options.outWidth
                                    imageHeight = options.outHeight
                                }
                            }
                        } else {
                            val cachedScreen = ScreenBitmapCache.get(currentItem.encryptedName)
                            if (cachedScreen != null) {
                                imageWidth = cachedScreen.width
                                imageHeight = cachedScreen.height
                            }
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
                viewModel.setAnimatingItem(activeItem.encryptedName)
            }
            pagerScrollEnabled = true
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
                            alpha = if (showInteractiveViewer) 1f else 0f
                        }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = pagerScrollEnabled && showInteractiveViewer,
                        beyondViewportPageCount = 1,
                        key = { page -> items[page].encryptedName },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageItem = items[page]
                        val isCurrentPage = page == pagerState.currentPage

                        val cachedThumb = remember(pageItem.encryptedName) {
                            ThumbBitmapCache.get(pageItem.encryptedName)
                        }

                        val cachedScreen = remember(pageItem.encryptedName) {
                            ScreenBitmapCache.get(pageItem.encryptedName)
                        }

                        val bestPlaceholder = when {
                            isCurrentPage &&
                                    screenImageBitmapOwner == pageItem.encryptedName &&
                                    screenImageBitmap != null -> screenImageBitmap
                            cachedScreen != null -> cachedScreen
                            else -> cachedThumb
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pageItem.isVideo) {
                                dek?.let { key ->
                                    EncryptedVideoPlayer(
                                        context = context,
                                        item = pageItem,
                                        dek = key,
                                        isActive = isCurrentPage && showInteractiveViewer,
                                        isInteractive = showInteractiveViewer,
                                        showControls = showControls,
                                        placeholderBitmap = bestPlaceholder,
                                        onTap = { showControls = !showControls },
                                        onVideoReady = { if (isCurrentPage) isVideoReadyForHdr = true },
                                        onDismissDragStart = { if (isCurrentPage) isHdrActive = false },
                                        onDismissDragCancel = { if (isCurrentPage && isVideoReadyForHdr) isHdrActive = true },
                                        onDismiss = { scaleVal, xVal, yVal ->
                                            dragScale = scaleVal
                                            dragX = xVal + (screenW / 2f) * (1f - scaleVal)
                                            dragY = yVal + (screenH / 2f) * (1f - scaleVal)
                                            animateClose()
                                        }
                                    )
                                }
                            } else {
                                val loader = customImageLoader
                                val key = dek
                                if (loader != null && key != null) {
                                    EncryptedImageViewer(
                                        context = context,
                                        item = pageItem,
                                        imageLoader = loader,
                                        dek = key,
                                        isActivePage = isCurrentPage && showInteractiveViewer,
                                        placeholderBitmap = bestPlaceholder,
                                        onDismissDragStart = { if (isCurrentPage) isHdrActive = false },
                                        onDismissDragCancel = { if (isCurrentPage) isHdrActive = true },
                                        onDismiss = { scaleVal, xVal, yVal ->
                                            dragScale = scaleVal
                                            dragX = xVal + (screenW / 2f) * (1f - scaleVal)
                                            dragY = yVal + (screenH / 2f) * (1f - scaleVal)
                                            animateClose()
                                        },
                                        onScaleChanged = { scale ->
                                            if (isCurrentPage) {
                                                pagerScrollEnabled = scale <= 1.05f
                                            }
                                        },
                                        onTap = { showControls = !showControls },
                                        onFullImageReady = { if (isCurrentPage) isImageReadyForHdr = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // LỚP VẼ CANVAS CHO ANIMATION
            if (!showInteractiveViewer || isEntering) {
                val currentTargetBounds = if (isEntering) startBounds else (boundsMap[currentItem.encryptedName] ?: startBounds)
                val trStartLeft = currentTargetBounds[0]
                val trStartTop = currentTargetBounds[1]

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val t = progress.value
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

                            fun drawLayer(imgBmp: androidx.compose.ui.graphics.ImageBitmap, opacity: Float) {
                                if (opacity <= 0f) return
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
                                                alpha = opacity,
                                                filterQuality = FilterQuality.Low
                                            )
                                        }
                                    }
                                }
                            }

                            if (thumbImageBitmap != null) {
                                drawLayer(thumbImageBitmap!!, 1f)
                            }

                            if (screenImageBitmap != null) {
                                val screenDrawAlpha = (screenImageAlpha.value * closeTransitionAlpha.value).coerceIn(0f, 1f)
                                drawLayer(screenImageBitmap!!, screenDrawAlpha)
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
                            val typeText = when {
                                currentItem.isVideo -> "Video (Secure)"
                                currentItem.isLivePhoto -> "Live Photo (Secure)"
                                else -> "Image (Secure)"
                            }
                            Text(text = "Type: $typeText", fontSize = 14.sp)
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
                    text = { Text("This will permanently decrypt and erase '${currentItem.originalName}' from storage. This cannot be undone.") },
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
    dek: ByteArray,
    isActivePage: Boolean = false,
    placeholderBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    onDismiss: (scale: Float, x: Float, y: Float) -> Unit,
    onDismissDragStart: () -> Unit = {},
    onDismissDragCancel: () -> Unit = {},
    onScaleChanged: (Float) -> Unit,
    onTap: () -> Unit,
    onFullImageReady: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val backgroundDimAlpha = remember { Animatable(1f) }

    val displayMetrics = context.resources.displayMetrics
    val screenH = displayMetrics.heightPixels.toFloat()

    LaunchedEffect(scale.value) { onScaleChanged(scale.value) }

    var fullBitmap by remember(item.encryptedName) {
        mutableStateOf(FullBitmapCache.get(item.encryptedName))
    }
    val fullImageAlpha = remember(item.encryptedName) {
        Animatable(if (FullBitmapCache.get(item.encryptedName) != null) 1f else 0f)
    }

    // Trạng thái phát video nhúng của Live Photo (khi nhấn giữ ảnh hoặc bấm vào nút LIVE)
    var isPlayingLivePhoto by remember(item.encryptedName) { mutableStateOf(false) }
    var isLiveVideoRendered by remember(item.encryptedName) { mutableStateOf(false) }
    var liveExoPlayer by remember(item.encryptedName) { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            isPlayingLivePhoto = false
            isLiveVideoRendered = false
            liveExoPlayer?.pause()
        }
    }

    LaunchedEffect(isPlayingLivePhoto, isActivePage) {
        if (item.isLivePhoto && isPlayingLivePhoto && isActivePage) {
            if (liveExoPlayer == null) {
                val liveUri = item.uri.buildUpon().appendQueryParameter("live", "1").build()
                liveExoPlayer = ExoPlayer.Builder(context).build().apply {
                    val mediaSource = ProgressiveMediaSource.Factory(
                        EncryptedDataSourceFactory(context, dek)
                    ).createMediaSource(MediaItem.fromUri(liveUri))
                    setMediaSource(mediaSource)
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            isLiveVideoRendered = true
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                isPlayingLivePhoto = false
                                isLiveVideoRendered = false
                            }
                        }
                    })
                }
            } else {
                liveExoPlayer?.seekTo(0L)
            }
            liveExoPlayer?.playWhenReady = true
            liveExoPlayer?.play()
        } else {
            liveExoPlayer?.playWhenReady = false
            liveExoPlayer?.pause()
            isLiveVideoRendered = false
        }
    }

    DisposableEffect(item.encryptedName) {
        onDispose {
            liveExoPlayer?.release()
            liveExoPlayer = null
        }
    }

    val liveVideoAlpha by animateFloatAsState(
        targetValue = if (isPlayingLivePhoto && isLiveVideoRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "live_photo_video_crossfade"
    )

    LaunchedEffect(item.encryptedName, dek) {
        val cachedFull = FullBitmapCache.get(item.encryptedName)
        if (cachedFull != null) {
            fullBitmap = cachedFull
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            var attempt = 0
            val maxAttempts = 3
            var succeeded = false
            while (!succeeded && attempt < maxAttempts) {
                attempt++
                try {
                    val info = CryptoEngine.readSgv2FileInfo(context, item.uri, dek)
                    val maxImageBytes = if (info != null && info.isLivePhoto && info.liveVideoOffset > 0L) {
                        info.liveVideoOffset.toInt()
                    } else -1

                    CryptoEngine.getSgv2TierStream(context, item.uri, CryptoEngine.Tier.FULL, dek)?.use { stream ->
                        val bytes = if (maxImageBytes > 0) {
                            val baos = ByteArrayOutputStream(maxImageBytes)
                            val buf = ByteArray(8192)
                            var remaining = maxImageBytes
                            while (remaining > 0) {
                                val r = stream.read(buf, 0, minOf(buf.size, remaining))
                                if (r == -1) break
                                baos.write(buf, 0, r)
                                remaining -= r
                            }
                            baos.toByteArray()
                        } else {
                            stream.readBytes()
                        }

                        val finalBmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            try {
                                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                    decoder.isMutableRequired = false
                                }
                            } catch (e: Exception) {
                                val options = BitmapFactory.Options().apply {
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                    inJustDecodeBounds = false
                                }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            }
                        } else {
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                inJustDecodeBounds = false
                            }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        }
                        if (finalBmp != null) {
                            val imgBmp = finalBmp.asImageBitmap()
                            FullBitmapCache.put(item.encryptedName, imgBmp)
                            withContext(Dispatchers.Main) {
                                fullBitmap = imgBmp
                            }
                            succeeded = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (!succeeded && attempt < maxAttempts) {
                    delay(150L * attempt)
                }
            }
        }
    }

    LaunchedEffect(fullBitmap) {
        if (fullBitmap != null) {
            fullImageAlpha.animateTo(1f, animationSpec = tween(durationMillis = 250))
            onFullImageReady()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundDimAlpha.value))
            .testTag("image_viewer_container"),
        contentAlignment = Alignment.Center
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val dismissThreshold = height * 0.15f

        val gestureModifier = Modifier.pointerInput(width, height, item.isLivePhoto) {
            var lastTapTime = 0L
            var lastTapPosition = Offset.Zero
            var pendingSingleTapJob: Job? = null

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
                    pendingSingleTapJob?.cancel()
                    pendingSingleTapJob = null
                    down.consume()

                    val targetScale = if (scale.value > 1.05f) 1f else 2.5f
                    coroutineScope.launch { scale.animateTo(targetScale, spring()) }
                    if (targetScale == 1f) {
                        coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                        coroutineScope.launch { offsetY.animateTo(0f, spring()) }
                    } else {
                        val maxOffsetX = (width * 2.5f - width) / 2f
                        val targetX = ((width / 2f) - down.position.x) * 1.5f
                        coroutineScope.launch { offsetX.animateTo(targetX.coerceIn(-maxOffsetX, maxOffsetX), spring()) }

                        val bmpW = (fullBitmap?.width ?: placeholderBitmap?.width)?.toFloat() ?: 0f
                        val bmpH = (fullBitmap?.height ?: placeholderBitmap?.height)?.toFloat() ?: 0f
                        val fittedHeight = if (bmpW > 0f && bmpH > 0f) {
                            bmpH * kotlin.math.min(width / bmpW, height / bmpH)
                        } else {
                            height
                        }
                        val actualImageHeight = fittedHeight * targetScale
                        val targetY = if (actualImageHeight <= height) {
                            0f
                        } else {
                            val maxOffsetY = (actualImageHeight - height) / 2f
                            (((height / 2f) - down.position.y) * 1.5f).coerceIn(-maxOffsetY, maxOffsetY)
                        }
                        coroutineScope.launch { offsetY.animateTo(targetY, spring()) }
                    }
                } else {
                    lastTapTime = currentTime
                    lastTapPosition = down.position

                    var isPinching = false
                    var isDraggingToDismiss = false
                    var hasMovedSignificantDistance = false
                    var triggeredLiveHold = false
                    val startPos = down.position

                    // Nếu là Live Photo và đang ở tỉ lệ 1x: nhấn giữ ~280ms sẽ tự động phát Live Photo (giống iOS/Samsung)
                    val liveHoldJob = if (item.isLivePhoto && scale.value <= 1.05f) {
                        coroutineScope.launch {
                            delay(280L)
                            if (!isPinching && !isDraggingToDismiss && !hasMovedSignificantDistance) {
                                triggeredLiveHold = true
                                isPlayingLivePhoto = true
                            }
                        }
                    } else null

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes

                        if (pointers.isEmpty() || pointers.all { !it.pressed }) break

                        val firstPointer = pointers.firstOrNull()
                        if (firstPointer != null) {
                            val dist = (firstPointer.position - startPos).getDistance()
                            if (dist > 15f) {
                                hasMovedSignificantDistance = true
                                liveHoldJob?.cancel()
                            }
                        }

                        if (pointers.size >= 2) {
                            isPinching = true
                            liveHoldJob?.cancel()
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
                            } else if (!triggeredLiveHold) {
                                val absoluteDeltaX = kotlin.math.abs(delta.x)
                                val absoluteDeltaY = kotlin.math.abs(delta.y)
                                if (isDraggingToDismiss || (delta.y > 0f && absoluteDeltaY > absoluteDeltaX * 1.5f && absoluteDeltaY > 10f)) {
                                    liveHoldJob?.cancel()
                                    if (!isDraggingToDismiss) onDismissDragStart()
                                    isDraggingToDismiss = true

                                    gestureOffsetY += delta.y
                                    gestureOffsetX += delta.x

                                    val swipeProgress = (kotlin.math.abs(gestureOffsetY) / (height * 0.8f)).coerceIn(0f, 1f)
                                    gestureScale = (1f - (swipeProgress * 0.4f)).coerceAtLeast(0.6f)
                                    val dimAlpha = (1f - (kotlin.math.abs(gestureOffsetY) / (screenH / 2f))).coerceIn(0f, 1f)

                                    coroutineScope.launch {
                                        offsetX.snapTo(gestureOffsetX)
                                        offsetY.snapTo(gestureOffsetY)
                                        scale.snapTo(gestureScale)
                                        backgroundDimAlpha.snapTo(dimAlpha)
                                    }
                                    pointer.consume()
                                }
                            }
                        }
                    }

                    liveHoldJob?.cancel()
                    if (triggeredLiveHold) {
                        isPlayingLivePhoto = false
                    } else if (!isPinching && !isDraggingToDismiss && !hasMovedSignificantDistance) {
                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = coroutineScope.launch {
                            delay(300L)
                            onTap()
                        }
                    } else if (scale.value < 1f && !isDraggingToDismiss) {
                        coroutineScope.launch { scale.animateTo(1f, spring()) }
                        coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                        coroutineScope.launch { offsetY.animateTo(0f, spring()) }
                    } else if (isDraggingToDismiss) {
                        if (offsetY.value > dismissThreshold) {
                            onDismiss(scale.value, offsetX.value, offsetY.value)
                        } else {
                            onDismissDragCancel()
                            coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                            coroutineScope.launch { offsetY.animateTo(0f, spring()) }
                            coroutineScope.launch { scale.animateTo(1f, spring()) }
                            coroutineScope.launch { backgroundDimAlpha.animateTo(1f, spring()) }
                        }
                    } else if (scale.value > 1f) {
                        val maxOffsetX = (width * scale.value - width) / 2f
                        val targetX = offsetX.value.coerceIn(-maxOffsetX, maxOffsetX)
                        coroutineScope.launch { offsetX.animateTo(targetX, spring()) }

                        val bmpW = (fullBitmap?.width ?: placeholderBitmap?.width)?.toFloat() ?: 0f
                        val bmpH = (fullBitmap?.height ?: placeholderBitmap?.height)?.toFloat() ?: 0f
                        val fittedHeight = if (bmpW > 0f && bmpH > 0f) {
                            bmpH * kotlin.math.min(width / bmpW, height / bmpH)
                        } else {
                            height
                        }
                        val actualImageHeight = fittedHeight * scale.value

                        if (actualImageHeight <= height) {
                            coroutineScope.launch {
                                offsetY.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        stiffness = 600f,
                                        dampingRatio = 1.0f,
                                        visibilityThreshold = 0.0001f
                                    )
                                )
                            }
                        } else {
                            val maxOffsetY = (actualImageHeight - height) / 2f
                            val targetY = offsetY.value.coerceIn(-maxOffsetY, maxOffsetY)
                            coroutineScope.launch { offsetY.animateTo(targetY, spring()) }
                        }
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
            val screenTierAlpha = 1f - fullImageAlpha.value
            val tierScreenUri = remember(item.uri) {
                item.uri.buildUpon().appendQueryParameter("tier", "SCREEN").build()
            }
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(tierScreenUri)
                    .build(),
                imageLoader = imageLoader
            )
            val painterState = painter.state

            if (painterState is coil.compose.AsyncImagePainter.State.Success) {
                Image(
                    painter = painter,
                    contentDescription = item.originalName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value,
                            alpha = screenTierAlpha
                        )
                        .testTag("encrypted_image"),
                    contentScale = ContentScale.Fit
                )
            } else if (placeholderBitmap != null) {
                Image(
                    bitmap = placeholderBitmap,
                    contentDescription = item.originalName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value,
                            alpha = screenTierAlpha
                        )
                        .testTag("encrypted_image"),
                    contentScale = ContentScale.Fit
                )
            }

            val currentFullBitmap = fullBitmap
            if (currentFullBitmap != null) {
                Image(
                    bitmap = currentFullBitmap,
                    contentDescription = item.originalName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value
                        )
                        .testTag("encrypted_image_full"),
                    contentScale = ContentScale.Fit
                )
            }

            // LỚP VIDEO CỦA LIVE PHOTO (Crossfade mượt lên trên ảnh khi người dùng nhấn giữ hoặc bấm nút LIVE)
            if (item.isLivePhoto && liveExoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = liveExoPlayer
                        }
                    },
                    update = { view ->
                        view.player = liveExoPlayer
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value,
                            alpha = liveVideoAlpha
                        )
                )
            }

            // Nút / Huy hiệu LIVE cho ảnh Live Photo (bấm để phát/dừng hoặc nhấn giữ vào ảnh để phát)
            if (item.isLivePhoto) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isPlayingLivePhoto) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.62f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isPlayingLivePhoto) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clickable {
                            isPlayingLivePhoto = !isPlayingLivePhoto
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("live_photo_badge_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MotionPhotosOn,
                        contentDescription = "Play Live Photo",
                        tint = if (isPlayingLivePhoto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlayingLivePhoto) "LIVE • PLAYING" else "LIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = if (isPlayingLivePhoto) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                }
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
    isInteractive: Boolean,
    showControls: Boolean,
    placeholderBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onTap: () -> Unit,
    onVideoReady: () -> Unit,
    onDismissDragStart: () -> Unit = {},
    onDismissDragCancel: () -> Unit = {},
    onDismiss: (Float, Float, Float) -> Unit
) {
    var isVideoReady by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var localScreenBitmap by remember(item.encryptedName) {
        mutableStateOf(ScreenBitmapCache.get(item.encryptedName))
    }

    LaunchedEffect(item.encryptedName, dek) {
        if (localScreenBitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                CryptoEngine.getSgv2TierStream(context, item.uri, CryptoEngine.Tier.SCREEN, dek)?.use { stream ->
                    val bytes = stream.readBytes()
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    if (bmp != null) {
                        ScreenBitmapCache.put(item.encryptedName, bmp)
                        withContext(Dispatchers.Main) {
                            localScreenBitmap = bmp
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context).build().apply {
                    val mediaSource = ProgressiveMediaSource.Factory(
                        EncryptedDataSourceFactory(context, dek)
                    ).createMediaSource(MediaItem.fromUri(item.uri))
                    setMediaSource(mediaSource)
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            isVideoReady = true
                            onVideoReady()
                        }
                    })
                }
            }
            exoPlayer?.playWhenReady = true
        } else {
            exoPlayer?.playWhenReady = false
            exoPlayer?.pause()
        }
    }

    DisposableEffect(item.uri) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }

    LaunchedEffect(exoPlayer) {
        while (exoPlayer != null) {
            currentPosition = exoPlayer!!.currentPosition
            duration = exoPlayer!!.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer!!.isPlaying
            delay(250)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val displayMetrics = context.resources.displayMetrics
    val screenH = displayMetrics.heightPixels.toFloat()

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

                        gestureOffsetY += delta.y
                        gestureOffsetX += delta.x

                        val swipeProgress = (kotlin.math.abs(gestureOffsetY) / (screenH * 0.8f)).coerceIn(0f, 1f)
                        gestureScale = (1f - (swipeProgress * 0.4f)).coerceAtLeast(0.6f)

                        coroutineScope.launch {
                            offsetX.snapTo(gestureOffsetX)
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
                    onDismissDragCancel()
                    coroutineScope.launch { offsetX.animateTo(0f, spring()) }
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
            .background(Color.Black.copy(alpha = (1f - (kotlin.math.abs(offsetY.value) / (screenH / 2f))).coerceIn(0f, 1f)))
            .then(gestureModifier)
            .testTag("video_player_container"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isInteractive && exoPlayer != null) {
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
            }

            val effectivePlaceholder = localScreenBitmap ?: placeholderBitmap
            if ((!isVideoReady || exoPlayer == null) && effectivePlaceholder != null) {
                Image(
                    bitmap = effectivePlaceholder,
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

            if (!isPlaying && showControls && exoPlayer != null) {
                IconButton(
                    onClick = { exoPlayer?.play() },
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
            visible = showControls && exoPlayer != null,
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
                            onValueChange = { exoPlayer?.seekTo(it.toLong()) },
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
                        IconButton(onClick = { exoPlayer?.seekTo((exoPlayer!!.currentPosition - 10000).coerceAtLeast(0L)) }) {
                            Icon(imageVector = Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
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

                        IconButton(onClick = { exoPlayer?.seekTo((exoPlayer!!.currentPosition + 10000).coerceAtMost(duration)) }) {
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

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}