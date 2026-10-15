package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.media.ExifInterface
import android.view.Gravity
import android.view.TextureView
import android.view.VelocityTracker
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
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
import com.example.ui.MediaGpsInfo
import com.example.ui.VaultItem
import com.example.ui.VaultViewModel
import com.example.ui.theme.GoogleSansRounded
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan
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

object MapTileCache {
    private const val MAX_TILES = 512
    private val cache = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(
        MAX_TILES, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?
        ): Boolean = size > MAX_TILES
    }
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile
    private var diskCacheDir: File? = null

    // Bộ đếm trạng thái Compose giúp Canvas tự động vẽ lại ngay lập tức khi có bất kỳ ô bản đồ nào tải xong
    val version = mutableIntStateOf(0)

    fun initDiskCache(context: Context) {
        if (diskCacheDir == null) {
            val darkDir = File(context.cacheDir, "vault_map_tiles_dark_v2")
            if (!darkDir.exists()) darkDir.mkdirs()
            diskCacheDir = darkDir

            // Dọn dẹp các thư mục cache cũ (bao gồm cả tile sáng cũ hoặc tile lỗi từ carto)
            try {
                File(context.cacheDir, "vault_map_tiles_hd").takeIf { it.exists() }?.deleteRecursively()
                File(context.cacheDir, "vault_map_tiles_dark_hd").takeIf { it.exists() }?.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun get(key: String): androidx.compose.ui.graphics.ImageBitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        cache[key] = bitmap
    }

    fun markInFlight(key: String): Boolean = inFlight.add(key)
    fun unmarkInFlight(key: String) {
        inFlight.remove(key)
    }

    /**
     * Chuyển đổi ô bản đồ sáng sang giao diện Dark Mode sắc nét bằng ma trận đảo ngược độ sáng
     * giữ nguyên sắc độ tự nhiên (Hue-Preserving Luminance Inversion):
     * - Nền đất sáng chuyển thành xám than tối (#11151F)
     * - Mặt nước vẫn giữ sắc xanh biển tối (#1E2F42)
     * - Công viên giữ sắc xanh rêu tối
     * - Chữ địa danh và tên đường nổi bật màu xám bạc sáng rõ nét
     */
    private fun convertLightTileToDarkBitmap(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(
            source.width.coerceAtLeast(1),
            source.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(out)
        val darkMapMatrix = ColorMatrix(
            floatArrayOf(
                0.011f, -0.646f, -0.125f, 0f, 205f,
                -0.329f, -0.306f, -0.125f, 0f, 209f,
                -0.329f, -0.646f,  0.215f, 0f, 218f,
                0f,      0f,      0f,     1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(darkMapMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Đọc ô bản đồ Dark từ Disk Cache (< 1ms) nếu đã từng tải; nếu chưa có thì tải từ các máy chủ
     * bản đồ công cộng KHÔNG cần API Key (Google Maps Retina @2x & OpenStreetMap), chuyển sang Dark Mode
     * và lưu xuống Disk Cache.
     */
    fun downloadTileBitmap(z: Int, x: Int, y: Int): androidx.compose.ui.graphics.ImageBitmap? {
        val diskFile = diskCacheDir?.let { File(it, "${z}_${x}_${y}.tile") }
        if (diskFile != null && diskFile.exists() && diskFile.length() > 64L) {
            try {
                val diskBytes = diskFile.readBytes()
                val diskBmp = BitmapFactory.decodeByteArray(diskBytes, 0, diskBytes.size)?.asImageBitmap()
                if (diskBmp != null) return diskBmp
            } catch (_: Exception) {}
        }

        val serverIdx = (x + y) and 3
        val altServerIdx = (x + y + 1) and 3
        val candidateUrls = listOf(
            "https://mt$serverIdx.google.com/vt/lyrs=m&hl=vi&x=$x&y=$y&z=$z&scale=2",
            "https://mt$altServerIdx.google.com/vt/lyrs=m&hl=vi&x=$x&y=$y&z=$z&scale=2",
            "https://mt$serverIdx.google.com/vt/lyrs=m&hl=vi&x=$x&y=$y&z=$z",
            "https://a.tile.openstreetmap.fr/osmfr/$z/$x/$y.png",
            "https://tile.openstreetmap.org/$z/$x/$y.png"
        )

        for (urlStr in candidateUrls) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/png,image/*,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        val decodedBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (decodedBmp != null) {
                            val darkened = convertLightTileToDarkBitmap(decodedBmp)
                            if (darkened !== decodedBmp) decodedBmp.recycle()

                            if (diskFile != null) {
                                try {
                                    ByteArrayOutputStream().use { baos ->
                                        darkened.compress(Bitmap.CompressFormat.PNG, 90, baos)
                                        diskFile.writeBytes(baos.toByteArray())
                                    }
                                } catch (_: Exception) {}
                            }
                            return darkened.asImageBitmap()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
        return null
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
        val mediaNotes by viewModel.mediaNotes.collectAsState()
        val mediaGps by viewModel.mediaGps.collectAsState()
        var showDeleteDialog by remember { mutableStateOf(false) }

        var showInfoDialog by remember { mutableStateOf(false) }
        var showFullScreenMap by remember { mutableStateOf(false) }
        val sheetProgress = remember { Animatable(0f) }

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

        // Tải trước (Pre-warm) các ô bản đồ Dark quanh GPS của ảnh hiện tại ngay khi mở ảnh
        val activeItemGps = mediaGps[currentItem.encryptedName]
        LaunchedEffect(currentItem.encryptedName, activeItemGps?.latitude, activeItemGps?.longitude) {
            MapTileCache.initDiskCache(context)
            val gps = activeItemGps ?: return@LaunchedEffect
            withContext(Dispatchers.IO) {
                for (z in listOf(15, 16, 17)) {
                    val numTiles = 1 shl z
                    val cx = floor(lonToTileX(gps.longitude, z.toDouble())).toInt()
                    val cy = floor(latToTileY(gps.latitude, z.toDouble())).toInt()
                    for (dy in -1..1) {
                        val ty = cy + dy
                        if (ty !in 0 until numTiles) continue
                        for (dx in -1..1) {
                            val wrappedTx = (((cx + dx) % numTiles) + numTiles) % numTiles
                            val key = "$z/$wrappedTx/$ty"
                            if (MapTileCache.get(key) == null && MapTileCache.markInFlight(key)) {
                                launch {
                                    try {
                                        val bmp = MapTileCache.downloadTileBitmap(z, wrappedTx, ty)
                                        if (bmp != null) {
                                            MapTileCache.put(key, bmp)
                                            withContext(Dispatchers.Main) {
                                                MapTileCache.version.intValue++
                                            }
                                        }
                                    } finally {
                                        MapTileCache.unmarkInFlight(key)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val closeSheet: () -> Unit = {
            showInfoDialog = false
        }
        val currentCloseSheet by rememberUpdatedState(closeSheet)

        var isInteractiveSheetGesture by remember { mutableStateOf(false) }

        LaunchedEffect(showInfoDialog, isInteractiveSheetGesture) {
            if (isInteractiveSheetGesture) return@LaunchedEffect
            if (showInfoDialog) {
                showControls = false
                sheetProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)
                )
            } else {
                sheetProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.92f, stiffness = 440f)
                )
            }
        }

        BackHandler {
            when {
                showFullScreenMap -> {
                    showFullScreenMap = false
                }
                showInfoDialog -> {
                    currentCloseSheet()
                }
                else -> {
                    animateClose()
                }
            }
        }

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
        var isTouchingAnyInlineMap by remember { mutableStateOf(false) }

        LaunchedEffect(pagerState.currentPage) {
            val activeItem = items.getOrNull(pagerState.currentPage)
            if (activeItem != null) {
                viewModel.selectMedia(activeItem)
                viewModel.setAnimatingItem(activeItem.encryptedName)
            }
            pagerScrollEnabled = true
            isTouchingAnyInlineMap = false
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

        val allVaultGpsEntries = remember(mediaGps, items) {
            val activeKeys = items.map { it.encryptedName }.toSet()
            mediaGps.filterKeys { it in activeKeys }
        }
        val currentGps = mediaGps[currentItem.encryptedName]
        val density = LocalDensity.current
        val focusManager = LocalFocusManager.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            val sheetHeightPx = screenH * 0.60f
            val sheetHeightDp = with(density) { sheetHeightPx.toDp() }
            val mediaUpwardShiftPx = -0.5f * sheetHeightPx * sheetProgress.value
            val isInfoSheetOpen = showInfoDialog || sheetProgress.value > 0.005f
            var lastSheetDragDy by remember { mutableFloatStateOf(0f) }

            val handleSheetVerticalEnd: () -> Unit = {
                isInteractiveSheetGesture = false
                val shouldOpen = when {
                    lastSheetDragDy < -1f -> sheetProgress.value > 0.05f
                    lastSheetDragDy > 1f -> sheetProgress.value > 0.92f
                    else -> sheetProgress.value > 0.5f
                }
                if (shouldOpen) {
                    showInfoDialog = true
                    coroutineScope.launch {
                        sheetProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)
                        )
                    }
                } else {
                    focusManager.clearFocus()
                    currentCloseSheet()
                }
            }

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
                        userScrollEnabled = ((pagerScrollEnabled || isInfoSheetOpen) && !isTouchingAnyInlineMap) && showInteractiveViewer,
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

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Lớp 1: Ảnh / Video của trang này
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationY = mediaUpwardShiftPx
                                    },
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
                                            isInfoSheetOpen = isInfoSheetOpen,
                                            placeholderBitmap = bestPlaceholder,
                                            onTap = {
                                                if (showInfoDialog) {
                                                    focusManager.clearFocus()
                                                    currentCloseSheet()
                                                } else {
                                                    showControls = !showControls
                                                }
                                            },
                                            onSwipeUpDelta = { dy ->
                                                lastSheetDragDy = dy
                                                isInteractiveSheetGesture = true
                                                showControls = false
                                                if (!showInfoDialog) showInfoDialog = true
                                                val nextProg = (sheetProgress.value - (dy * 1.5f) / sheetHeightPx).coerceIn(0f, 1f)
                                                coroutineScope.launch { sheetProgress.snapTo(nextProg) }
                                            },
                                            onSwipeUpEnd = {
                                                handleSheetVerticalEnd()
                                            },
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
                                            isActivePage = isCurrentPage && showInteractiveViewer && !isClosing,
                                            isInfoSheetOpen = isInfoSheetOpen,
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
                                            onTap = {
                                                if (showInfoDialog) {
                                                    focusManager.clearFocus()
                                                    currentCloseSheet()
                                                } else {
                                                    showControls = !showControls
                                                }
                                            },
                                            onSwipeUpDelta = { dy ->
                                                lastSheetDragDy = dy
                                                isInteractiveSheetGesture = true
                                                showControls = false
                                                if (!showInfoDialog) showInfoDialog = true
                                                val nextProg = (sheetProgress.value - (dy * 1.5f) / sheetHeightPx).coerceIn(0f, 1f)
                                                coroutineScope.launch { sheetProgress.snapTo(nextProg) }
                                            },
                                            onSwipeUpEnd = {
                                                handleSheetVerticalEnd()
                                            },
                                            onFullImageReady = { if (isCurrentPage) isImageReadyForHdr = true }
                                        )
                                    }
                                }
                            }

                            // Lớp 2: Bảng Properties Details RIÊNG của từng ảnh/video (nằm trực tiếp trong từng page của HorizontalPager)
                            if ((showInfoDialog || sheetProgress.value > 0.001f) && showInteractiveViewer) {
                                MediaPagePropertiesSheet(
                                    context = context,
                                    viewModel = viewModel,
                                    pageItem = pageItem,
                                    isCurrentPage = isCurrentPage,
                                    dek = dek,
                                    savedNote = mediaNotes[pageItem.encryptedName] ?: "",
                                    pageGps = mediaGps[pageItem.encryptedName],
                                    allVaultGps = allVaultGpsEntries.values.toList(),
                                    sheetHeightPx = sheetHeightPx,
                                    sheetHeightDp = sheetHeightDp,
                                    sheetProgress = sheetProgress,
                                    onInteractiveGestureChanged = { active -> isInteractiveSheetGesture = active },
                                    onCloseSheet = {
                                        focusManager.clearFocus()
                                        currentCloseSheet()
                                    },
                                    onOpenFullScreenMap = {
                                        showFullScreenMap = true
                                    },
                                    onInlineMapTouchChanged = { touching ->
                                        if (isCurrentPage) {
                                            isTouchingAnyInlineMap = touching
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
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
                            drawRect(Color.Black, alpha = t * (1f - (abs(dragY) / (screenH / 2f)).coerceIn(0f, 1f)))
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

            // HIỂN THỊ GHI CHÚ (NOTES) DƯỚI BOTTOM MÀN HÌNH, CĂN LỀ TRÁI, CHỮ TRẮNG SHADOW ĐEN MỜ
            val activeBottomNote = mediaNotes[currentItem.encryptedName]?.trim().orEmpty()
            val noteBottomSpace by animateDpAsState(
                targetValue = when {
                    currentItem.isVideo && showControls -> 136.dp
                    currentItem.isLivePhoto && !currentItem.isVideo -> 58.dp
                    else -> 24.dp
                },
                animationSpec = tween(durationMillis = 200),
                label = "note_bottom_space"
            )
            AnimatedVisibility(
                visible = showInteractiveViewer && activeBottomNote.isNotEmpty() && !showInfoDialog && sheetProgress.value < 0.05f,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 24.dp, bottom = noteBottomSpace)
            ) {
                Text(
                    text = activeBottomNote,
                    style = TextStyle(
                        fontFamily = GoogleSansRounded,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("viewer_bottom_note_text")
                )
            }

            // MÀN HÌNH BẢN ĐỒ FULL SCREEN KHI BẤM NÚT "OPEN" KÈM BOTTOM SHEET 3 MỐC (TOP BAR INDICATOR / 50% / 80%)
            AnimatedVisibility(
                visible = showFullScreenMap && showInteractiveViewer,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
                exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                val topBarIndicatorHeightDp = 34.dp
                val collapsedSheetFraction = remember(screenH, density) {
                    (with(density) { topBarIndicatorHeightDp.toPx() } / screenH.coerceAtLeast(1f)).coerceIn(0.035f, 0.12f)
                }
                val halfSheetFraction = 0.50f
                val expandedSheetFraction = 0.80f
                val mapSheetFraction = remember { Animatable(halfSheetFraction) }

                LaunchedEffect(showFullScreenMap) {
                    if (showFullScreenMap) {
                        mapSheetFraction.snapTo(halfSheetFraction)
                    }
                }

                val snapMapSheetToTarget: (Float) -> Unit = { velocityY ->
                    val anchors = listOf(collapsedSheetFraction, halfSheetFraction, expandedSheetFraction)
                    val cur = mapSheetFraction.value
                    val target = when {
                        velocityY < -350f -> anchors.firstOrNull { it > cur + 0.02f } ?: expandedSheetFraction
                        velocityY > 350f -> anchors.lastOrNull { it < cur - 0.02f } ?: collapsedSheetFraction
                        else -> anchors.minByOrNull { abs(it - cur) } ?: halfSheetFraction
                    }
                    coroutineScope.launch {
                        mapSheetFraction.animateTo(
                            targetValue = target,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)
                        )
                    }
                }

                var mapViewportLat by remember(currentGps?.latitude) {
                    mutableDoubleStateOf(
                        currentGps?.latitude ?: allVaultGpsEntries.values.firstOrNull()?.latitude ?: 21.0031
                    )
                }
                var mapViewportLon by remember(currentGps?.longitude) {
                    mutableDoubleStateOf(
                        currentGps?.longitude ?: allVaultGpsEntries.values.firstOrNull()?.longitude ?: 105.8202
                    )
                }
                var mapViewportZoom by remember(currentGps) {
                    mutableFloatStateOf(if (currentGps != null) 15.5f else 5f)
                }
                var mapViewportW by remember { mutableFloatStateOf(screenW) }
                var mapViewportH by remember { mutableFloatStateOf(screenH) }

                // Lọc danh sách các ảnh và video có toạ độ nằm trong vùng bản đồ đang hiển thị trên màn hình
                val visibleMapItems = remember(
                    items,
                    mediaGps,
                    mapViewportLat,
                    mapViewportLon,
                    mapViewportZoom,
                    mapViewportW,
                    mapViewportH
                ) {
                    val effW = if (mapViewportW > 0f) mapViewportW else screenW
                    val effH = if (mapViewportH > 0f) mapViewportH else screenH
                    val zInt = mapViewportZoom.toInt().coerceIn(2, 19)
                    val scaleFactor = 2.0.pow((mapViewportZoom - zInt).toDouble()).toFloat()
                    val tileSizePx = 512f * scaleFactor
                    val numTiles = 1 shl zInt
                    val centerTileX = lonToTileX(mapViewportLon, zInt.toDouble())
                    val centerTileY = latToTileY(mapViewportLat, zInt.toDouble())

                    items.filter { vaultItem ->
                        val gps = mediaGps[vaultItem.encryptedName] ?: return@filter false
                        val pTileX = lonToTileX(gps.longitude, zInt.toDouble())
                        val pTileY = latToTileY(gps.latitude, zInt.toDouble())
                        var deltaTileX = pTileX - centerTileX
                        if (deltaTileX > numTiles / 2.0) deltaTileX -= numTiles
                        else if (deltaTileX < -numTiles / 2.0) deltaTileX += numTiles
                        val px = (effW / 2f) + (deltaTileX * tileSizePx).toFloat()
                        val py = (effH / 2f) + ((pTileY - centerTileY) * tileSizePx).toFloat()
                        px in 0f..effW && py in 0f..effH
                    }
                }

                val groupedMapItems = remember(visibleMapItems) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    visibleMapItems.groupBy { vItem ->
                        if (vItem.dateTaken == 0L) "Unknown" else sdf.format(Date(vItem.dateTaken))
                    }
                }

                val mapGridState = rememberLazyGridState()

                val mapSheetNestedScrollConnection = remember(
                    screenH,
                    collapsedSheetFraction,
                    halfSheetFraction,
                    expandedSheetFraction
                ) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.Fling) return Offset.Zero
                            if (available.y < 0f && mapSheetFraction.value < expandedSheetFraction - 0.001f) {
                                val nextFrac = (mapSheetFraction.value - available.y / screenH)
                                    .coerceIn(collapsedSheetFraction, expandedSheetFraction)
                                coroutineScope.launch { mapSheetFraction.snapTo(nextFrac) }
                                return Offset(0f, available.y)
                            }
                            if (available.y > 0f && !mapGridState.canScrollBackward && mapSheetFraction.value > collapsedSheetFraction) {
                                val nextFrac = (mapSheetFraction.value - available.y / screenH)
                                    .coerceIn(collapsedSheetFraction, expandedSheetFraction)
                                coroutineScope.launch { mapSheetFraction.snapTo(nextFrac) }
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (source == NestedScrollSource.Fling) return Offset.Zero
                            if (available.y > 0f && mapSheetFraction.value > collapsedSheetFraction) {
                                val nextFrac = (mapSheetFraction.value - available.y / screenH)
                                    .coerceIn(collapsedSheetFraction, expandedSheetFraction)
                                coroutineScope.launch { mapSheetFraction.snapTo(nextFrac) }
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            val cur = mapSheetFraction.value
                            val isAtExpandedAndScrolled = abs(cur - expandedSheetFraction) < 0.015f && mapGridState.canScrollBackward
                            if (!isAtExpandedAndScrolled) {
                                snapMapSheetToTarget(available.y)
                                return available
                            }
                            return Velocity.Zero
                        }
                    }
                }

                val currentMapSheetHeightDp = with(density) {
                    (screenH * mapSheetFraction.value).toDp()
                }
                val controlsBottomOffsetDp = with(density) {
                    (screenH * mapSheetFraction.value.coerceAtMost(halfSheetFraction)).toDp() + 12.dp
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0E1014))
                        .testTag("fullscreen_map_container")
                ) {
                    DetailsGpsHeatmapMap(
                        currentGps = currentGps,
                        allVaultGps = allVaultGpsEntries.values.toList(),
                        isFullScreen = true,
                        controlsBottomPadding = controlsBottomOffsetDp,
                        onViewportChanged = { cLat, cLon, cZoom, cW, cH ->
                            mapViewportLat = cLat
                            mapViewportLon = cLon
                            mapViewportZoom = cZoom
                            if (cW > 0f) mapViewportW = cW
                            if (cH > 0f) mapViewportH = cH
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Nút X đóng bản đồ ở góc trên bên phải
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 12.dp, end = 12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .clickable { showFullScreenMap = false }
                            .testTag("close_fullscreen_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Full Screen Map",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // BOTTOM SHEET TRONG FULL SCREEN MAP (3 mốc: lộ top bar indicator / 50% / 80%)
                    Card(
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121314)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(currentMapSheetHeightDp)
                            .border(
                                width = 0.8.dp,
                                color = Color(0xFF262628),
                                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                            )
                            .nestedScroll(mapSheetNestedScrollConnection)
                            .pointerInput(screenH, collapsedSheetFraction, halfSheetFraction, expandedSheetFraction) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val velocityTracker = VelocityTracker.obtain()
                                    var draggedVertically = false
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val dy = change.position.y - change.previousPosition.y
                                            if (abs(change.position.y - down.position.y) > 6f) {
                                                draggedVertically = true
                                            }
                                            if (draggedVertically && (!mapGridState.canScrollForward && !mapGridState.canScrollBackward || down.position.y <= with(density) { topBarIndicatorHeightDp.toPx() })) {
                                                val nextFrac = (mapSheetFraction.value - dy / screenH)
                                                    .coerceIn(collapsedSheetFraction, expandedSheetFraction)
                                                coroutineScope.launch { mapSheetFraction.snapTo(nextFrac) }
                                                change.consume()
                                            }
                                        }
                                    } finally {
                                        velocityTracker.recycle()
                                    }
                                    if (draggedVertically) {
                                        snapMapSheetToTarget(0f)
                                    }
                                }
                            }
                            .testTag("fullscreen_map_bottom_sheet")
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Bar Indicator (luôn lộ ra khi thu nhỏ tối đa, hỗ trợ kéo lên/xuống trực tiếp)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(topBarIndicatorHeightDp)
                                    .pointerInput(screenH, collapsedSheetFraction, halfSheetFraction, expandedSheetFraction) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var totalDy = 0f
                                            var lastDy = 0f
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) break
                                                val dy = change.position.y - change.previousPosition.y
                                                totalDy += dy
                                                lastDy = dy
                                                val nextFrac = (mapSheetFraction.value - dy / screenH)
                                                    .coerceIn(collapsedSheetFraction, expandedSheetFraction)
                                                coroutineScope.launch { mapSheetFraction.snapTo(nextFrac) }
                                                change.consume()
                                            }
                                            if (abs(totalDy) < 8f) {
                                                val target = if (mapSheetFraction.value <= collapsedSheetFraction + 0.03f) {
                                                    halfSheetFraction
                                                } else {
                                                    collapsedSheetFraction
                                                }
                                                coroutineScope.launch {
                                                    mapSheetFraction.animateTo(
                                                        targetValue = target,
                                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)
                                                    )
                                                }
                                            } else {
                                                val flingVel = lastDy * 45f
                                                snapMapSheetToTarget(flingVel)
                                            }
                                        }
                                    }
                                    .testTag("fullscreen_map_sheet_handle")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(38.dp)
                                        .height(4.5.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF6E6E73))
                                )
                            }

                            // Nội dung bên trong sheet: Grid các ảnh/video y hệt GalleryScreen nhưng chỉ hiện các mục trong vùng bản đồ đang hiển thị
                            if (mapSheetFraction.value > collapsedSheetFraction + 0.01f) {
                                if (visibleMapItems.isEmpty()) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp)
                                    ) {
                                        Text(
                                            text = "No photos or videos in this map area",
                                            fontFamily = GoogleSansRounded,
                                            fontSize = 13.sp,
                                            color = Color(0xFF8E8E93),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        state = mapGridState,
                                        columns = GridCells.Fixed(5),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .navigationBarsPadding()
                                            .testTag("fullscreen_map_media_grid"),
                                        contentPadding = PaddingValues(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        groupedMapItems.forEach { (dateKey, itemsInGroup) ->
                                            item(
                                                key = "map_header_$dateKey",
                                                span = { GridItemSpan(maxLineSpan) }
                                            ) {
                                                val displayHeader = remember(dateKey, itemsInGroup) {
                                                    val firstItem = itemsInGroup.firstOrNull()
                                                    if (firstItem != null && firstItem.dateTaken != 0L) {
                                                        formatMapSheetGroupDate(firstItem.dateTaken)
                                                    } else {
                                                        "Unknown Date"
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateItem()
                                                        .padding(start = 8.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)
                                                        .height(24.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = displayHeader,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            items(itemsInGroup, key = { it.encryptedName }) { mapMediaItem ->
                                                GalleryGridItem(
                                                    item = mapMediaItem,
                                                    imageLoader = customImageLoader,
                                                    isAnimating = false,
                                                    isSelectionMode = false,
                                                    isSelected = false,
                                                    onItemClick = { _ ->
                                                        val targetIdx = items.indexOfFirst {
                                                            it.encryptedName == mapMediaItem.encryptedName
                                                        }
                                                        showFullScreenMap = false
                                                        showInfoDialog = false
                                                        if (targetIdx >= 0) {
                                                            coroutineScope.launch {
                                                                sheetProgress.snapTo(0f)
                                                                pagerState.scrollToPage(targetIdx)
                                                            }
                                                        }
                                                    },
                                                    onPositioned = { _ -> },
                                                    modifier = Modifier.animateItem()
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

/**
 * Bảng Properties Details RIÊNG BIỆT cho từng ảnh/video bên trong mỗi trang của HorizontalPager.
 * Khi người dùng vuốt ngang sang ảnh/video bên cạnh, cả bảng sheet này sẽ trượt ngang đi theo đúng trang của nó.
 */
@Composable
private fun MediaPagePropertiesSheet(
    context: Context,
    viewModel: VaultViewModel,
    pageItem: VaultItem,
    isCurrentPage: Boolean,
    dek: ByteArray?,
    savedNote: String,
    pageGps: MediaGpsInfo?,
    allVaultGps: List<MediaGpsInfo>,
    sheetHeightPx: Float,
    sheetHeightDp: Dp,
    sheetProgress: Animatable<Float, *>,
    onInteractiveGestureChanged: (Boolean) -> Unit,
    onCloseSheet: () -> Unit,
    onOpenFullScreenMap: () -> Unit,
    onInlineMapTouchChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var imageWidth by remember(pageItem.encryptedName) { mutableStateOf<Int?>(null) }
    var imageHeight by remember(pageItem.encryptedName) { mutableStateOf<Int?>(null) }
    var decryptedSize by remember(pageItem.encryptedName) { mutableStateOf<Long?>(null) }
    var deviceName by remember(pageItem.encryptedName) { mutableStateOf<String?>(null) }
    var loadingInfo by remember(pageItem.encryptedName) { mutableStateOf(false) }

    var noteDraft by remember(pageItem.encryptedName) { mutableStateOf(savedNote) }

    LaunchedEffect(savedNote, pageItem.encryptedName) {
        if (noteDraft.isEmpty() && savedNote.isNotEmpty()) {
            noteDraft = savedNote
        }
    }

    val latestNoteDraft by rememberUpdatedState(noteDraft)
    val latestSavedNote by rememberUpdatedState(savedNote)

    LaunchedEffect(noteDraft, pageItem.encryptedName) {
        if (noteDraft.trim() != savedNote.trim()) {
            delay(250L)
            viewModel.saveMediaNote(context, pageItem.encryptedName, noteDraft)
        }
    }

    DisposableEffect(pageItem.encryptedName) {
        val targetEncryptedName = pageItem.encryptedName
        onDispose {
            if (latestNoteDraft.trim() != latestSavedNote.trim()) {
                viewModel.saveMediaNote(context, targetEncryptedName, latestNoteDraft)
            }
        }
    }

    LaunchedEffect(pageItem.encryptedName, dek) {
        val key = dek ?: return@LaunchedEffect
        viewModel.ensureMediaGpsLoaded(context, pageItem)
        loadingInfo = true
        withContext(Dispatchers.IO) {
            try {
                val info = CryptoEngine.readSgv2FileInfo(context, pageItem.uri, key)
                if (info != null) {
                    decryptedSize = info.decryptedSize
                    if (info.width > 0 && info.height > 0) {
                        imageWidth = info.width
                        imageHeight = info.height
                    }
                }

                if (imageWidth == null || imageHeight == null) {
                    val cachedFull = FullBitmapCache.get(pageItem.encryptedName)
                    if (cachedFull != null) {
                        imageWidth = cachedFull.width
                        imageHeight = cachedFull.height
                    } else if (!pageItem.isVideo) {
                        CryptoEngine.getSgv2TierStream(context, pageItem.uri, CryptoEngine.Tier.FULL, key)?.use { stream ->
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(stream, null, options)
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                imageWidth = options.outWidth
                                imageHeight = options.outHeight
                            }
                        }
                    } else {
                        val cachedScreen = ScreenBitmapCache.get(pageItem.encryptedName)
                        if (cachedScreen != null) {
                            imageWidth = cachedScreen.width
                            imageHeight = cachedScreen.height
                        }
                    }
                }

                deviceName = extractDeviceNameFromVaultItem(context, pageItem, key)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingInfo = false
            }
        }
    }

    var isTouchingInlineMap by remember(pageItem.encryptedName) { mutableStateOf(false) }
    val sheetScrollState = rememberScrollState()

    LaunchedEffect(pageGps) {
        if (pageGps == null && isTouchingInlineMap) {
            isTouchingInlineMap = false
            onInlineMapTouchChanged(false)
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && isTouchingInlineMap) {
            isTouchingInlineMap = false
            onInlineMapTouchChanged(false)
        }
    }

    val sheetNestedScrollConnection = remember(sheetHeightPx, isTouchingInlineMap) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isTouchingInlineMap || source == NestedScrollSource.Fling) return Offset.Zero
                if (available.y > 0f && sheetScrollState.value == 0) {
                    onInteractiveGestureChanged(true)
                    val newProg = (sheetProgress.value - available.y / sheetHeightPx).coerceIn(0f, 1f)
                    coroutineScope.launch { sheetProgress.snapTo(newProg) }
                    return Offset(0f, available.y)
                }
                if (available.y < 0f && sheetProgress.value < 1f) {
                    onInteractiveGestureChanged(true)
                    val newProg = (sheetProgress.value - available.y / sheetHeightPx).coerceIn(0f, 1f)
                    coroutineScope.launch { sheetProgress.snapTo(newProg) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isTouchingInlineMap) return Velocity.Zero
                if (sheetProgress.value < 1f) {
                    onInteractiveGestureChanged(false)
                    if (sheetProgress.value < 0.88f || available.y > 320f) {
                        if (latestNoteDraft.trim() != latestSavedNote.trim()) {
                            viewModel.saveMediaNote(context, pageItem.encryptedName, latestNoteDraft)
                        }
                        onCloseSheet()
                    } else {
                        coroutineScope.launch {
                            sheetProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)
                            )
                        }
                    }
                    return available
                } else if (sheetScrollState.value == 0 && available.y > 550f) {
                    onInteractiveGestureChanged(false)
                    if (latestNoteDraft.trim() != latestSavedNote.trim()) {
                        viewModel.saveMediaNote(context, pageItem.encryptedName, latestNoteDraft)
                    }
                    onCloseSheet()
                    return available
                }
                onInteractiveGestureChanged(false)
                return Velocity.Zero
            }
        }
    }

    val formatSizeCompact = remember {
        { bytes: Long? ->
            if (bytes == null) "..."
            else if (bytes < 1024) "${bytes}B"
            else {
                val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
                val unit = "KMGTPE"[exp - 1] + "B"
                String.format(Locale.US, "%.2f%s", bytes / Math.pow(1024.0, exp.toDouble()), unit)
            }
        }
    }

    val formattedDateTaken = remember(pageItem.dateTaken) {
        if (pageItem.dateTaken <= 0L) "Unknown date"
        else {
            val d = Date(pageItem.dateTaken)
            val datePart = SimpleDateFormat("EEE, MMM d, yyyy", Locale.ENGLISH).format(d)
            val timePart = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(d)
            "$datePart  •  $timePart"
        }
    }

    val primaryTextColor = Color(0xFFD6D6D6)
    val secondaryTextColor = Color(0xFF8E8E93)
    val placeholderTextColor = Color(0xFF5A5A5E)
    val dividerColor = Color(0xFF242426)

    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeightDp)
            .graphicsLayer {
                translationY = sheetHeightPx * (1f - sheetProgress.value)
            }
            .border(width = 0.5.dp, color = Color(0xFF1F1F22))
            .nestedScroll(sheetNestedScrollConnection)
            .then(if (isCurrentPage) Modifier.testTag("properties_bottom_sheet") else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(sheetScrollState, enabled = !isTouchingInlineMap)
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            // Dòng 1 (Heading): Date and Time (17.sp, định dạng "Thu, Oct 15, 2026  •  20:41")
            Text(
                text = formattedDateTaken,
                fontFamily = GoogleSansRounded,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                letterSpacing = 0.1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dòng 2 (Sub-line 1): Tên file gốc (click để copy full name + extension)
            Text(
                text = pageItem.originalName,
                fontFamily = GoogleSansRounded,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        copyFileNameToClipboard(context, pageItem.originalName)
                    }
                    .then(if (isCurrentPage) Modifier.testTag("copy_original_filename") else Modifier)
            )

            Spacer(modifier = Modifier.height(3.5.dp))

            // Dòng 3 (Sub-line 2): Tên file mã hoá (click để copy full name + extension)
            Text(
                text = pageItem.encryptedName,
                fontFamily = GoogleSansRounded,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        copyFileNameToClipboard(context, pageItem.encryptedName)
                    }
                    .then(if (isCurrentPage) Modifier.testTag("copy_encrypted_filename") else Modifier)
            )

            Spacer(modifier = Modifier.height(3.5.dp))

            // Media Note nằm ngay trên thẻ Device
            BasicTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                textStyle = TextStyle(
                    fontFamily = GoogleSansRounded,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFB8B8BD),
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(primaryTextColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (noteDraft.trim() != savedNote.trim()) {
                            viewModel.saveMediaNote(context, pageItem.encryptedName, noteDraft)
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        if (noteDraft.isEmpty()) {
                            Text(
                                text = "Thêm ghi chú...",
                                fontFamily = GoogleSansRounded,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = placeholderTextColor
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isCurrentPage) Modifier.testTag("media_note_input") else Modifier)
            )

            HorizontalDivider(
                thickness = 0.6.dp,
                color = dividerColor,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Heading 2: Tên device chụp ảnh (16.5.sp)
            val resolvedDeviceText = when {
                !deviceName.isNullOrBlank() -> deviceName!!
                loadingInfo -> "Reading device..."
                else -> "Unknown device"
            }
            Text(
                text = resolvedDeviceText,
                fontFamily = GoogleSansRounded,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dòng thông số chi tiết: số Megapixel, Width & Height
            val mpAndResolutionText = if (imageWidth != null && imageHeight != null && imageWidth!! > 0 && imageHeight!! > 0) {
                val mp = (imageWidth!!.toLong() * imageHeight!!.toLong()) / 1_000_000.0
                val mpFormatted = if (mp >= 1.0) {
                    val roundedInt = kotlin.math.round(mp).toInt()
                    if (abs(mp - roundedInt) < 0.15) "${roundedInt}MP"
                    else String.format(Locale.US, "%.1fMP", mp)
                } else {
                    String.format(Locale.US, "%.2fMP", mp)
                }
                "$mpFormatted | ${imageWidth} × ${imageHeight}"
            } else if (loadingInfo) {
                "Calculating resolution..."
            } else {
                "Unknown resolution"
            }
            Text(
                text = mpAndResolutionText,
                fontFamily = GoogleSansRounded,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(3.5.dp))

            // Dòng dung lượng trước và sau mã hoá: ví dụ "2.20MB (enc: 2.25MB)"
            val decSizeText = if (loadingInfo && decryptedSize == null) "..." else formatSizeCompact(decryptedSize)
            val encSizeText = formatSizeCompact(pageItem.size)
            Text(
                text = "$decSizeText (enc: $encSizeText)",
                fontFamily = GoogleSansRounded,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )

            HorizontalDivider(
                thickness = 0.6.dp,
                color = dividerColor,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Hàng Location: khi No location data (pageGps == null) thì KHÔNG được phép có Map và nút Open
            val twoLevelLocationText = remember(pageGps) {
                formatProvinceAndSubLevel(pageGps)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = twoLevelLocationText,
                    fontFamily = GoogleSansRounded,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (pageGps != null) primaryTextColor else secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )

                if (pageGps != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenFullScreenMap() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .then(if (isCurrentPage) Modifier.testTag("open_fullscreen_map_button") else Modifier)
                    ) {
                        Text(
                            text = "Open",
                            fontFamily = GoogleSansRounded,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (pageGps != null) {
                Spacer(modifier = Modifier.height(8.dp))

                DetailsGpsHeatmapMap(
                    currentGps = pageGps,
                    allVaultGps = allVaultGps,
                    onMapTouchChanged = { touching ->
                        isTouchingInlineMap = touching
                        onInlineMapTouchChanged(touching)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .then(if (isCurrentPage) Modifier.testTag("details_gps_heatmap_map") else Modifier)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
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
    isInfoSheetOpen: Boolean = false,
    placeholderBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    onDismiss: (scale: Float, x: Float, y: Float) -> Unit,
    onDismissDragStart: () -> Unit = {},
    onDismissDragCancel: () -> Unit = {},
    onScaleChanged: (Float) -> Unit,
    onTap: () -> Unit,
    onSwipeUpDelta: (Float) -> Unit = {},
    onSwipeUpEnd: () -> Unit = {},
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

    // Khi mở bảng Properties, tự động đưa tỉ lệ phóng về 1x để cho phép vuốt ngang sang ảnh bên cạnh mượt mà
    LaunchedEffect(isInfoSheetOpen) {
        if (isInfoSheetOpen && scale.value > 1.01f) {
            launch { scale.animateTo(1f, spring()) }
            launch { offsetX.animateTo(0f, spring()) }
            launch { offsetY.animateTo(0f, spring()) }
        }
    }

    var fullBitmap by remember(item.encryptedName) {
        mutableStateOf(FullBitmapCache.get(item.encryptedName))
    }
    val fullImageAlpha = remember(item.encryptedName) {
        Animatable(if (FullBitmapCache.get(item.encryptedName) != null) 1f else 0f)
    }

    // Trạng thái phát video nhúng của Live Photo (khi nhấn giữ ảnh hoặc bấm vào nút LIVE)
    var isPlayingLivePhoto by remember(item.encryptedName) { mutableStateOf(false) }
    var isLiveVideoRendered by remember(item.encryptedName) { mutableStateOf(false) }
    var liveVideoWidth by remember(item.encryptedName) { mutableIntStateOf(0) }
    var liveVideoHeight by remember(item.encryptedName) { mutableIntStateOf(0) }
    var liveExoPlayer by remember(item.encryptedName) { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            isPlayingLivePhoto = false
            isLiveVideoRendered = false
            liveExoPlayer?.playWhenReady = false
            liveExoPlayer?.pause()
            liveExoPlayer?.clearVideoSurface()
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
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                liveVideoWidth = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
                                liveVideoHeight = videoSize.height
                            }
                        }

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

    val currentIsInfoSheetOpen by rememberUpdatedState(isInfoSheetOpen)

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

                if (!currentIsInfoSheetOpen && currentTime - lastTapTime < 300L) {
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
                        val maxOffsetX = (width * targetScale - width) / 2f
                        val maxOffsetY = (height * targetScale - height) / 2f
                        val targetX = ((width / 2f) - down.position.x) * (targetScale - 1f)
                        val targetY = ((height / 2f) - down.position.y) * (targetScale - 1f)
                        coroutineScope.launch { offsetX.animateTo(targetX.coerceIn(-maxOffsetX, maxOffsetX), spring()) }
                        coroutineScope.launch { offsetY.animateTo(targetY.coerceIn(-maxOffsetY, maxOffsetY), spring()) }
                    }
                } else {
                    lastTapTime = currentTime
                    lastTapPosition = down.position

                    var isPinching = false
                    var isDraggingToDismiss = false
                    var isSwipingUpForInfo = false
                    var hasMovedSignificantDistance = false
                    var triggeredLiveHold = false
                    val startPos = down.position

                    val liveHoldJob = if (item.isLivePhoto && scale.value <= 1.05f && !currentIsInfoSheetOpen) {
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

                        if (pointers.size >= 2 && !currentIsInfoSheetOpen) {
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
                                val actualZoomFactor = if (gestureScale != 0f) newScale / gestureScale else 1f

                                // Vừa zoom theo khoảng cách 2 ngón, vừa di chuyển theo điểm trung tâm (midpoint) của 2 ngón tay
                                val currentCentroid = (pos1 + pos2) / 2f
                                val previousCentroid = (prevPos1 + prevPos2) / 2f
                                val prevOriginX = previousCentroid.x - (width / 2f)
                                val prevOriginY = previousCentroid.y - (height / 2f)
                                val curOriginX = currentCentroid.x - (width / 2f)
                                val curOriginY = currentCentroid.y - (height / 2f)

                                val newX = curOriginX - (prevOriginX - gestureOffsetX) * actualZoomFactor
                                val newY = curOriginY - (prevOriginY - gestureOffsetY) * actualZoomFactor

                                gestureScale = newScale
                                if (newScale > 1f) {
                                    val maxOffsetX = (width * newScale - width) / 2f
                                    val maxOffsetY = (height * newScale - height) / 2f
                                    gestureOffsetX = newX.coerceIn(-maxOffsetX, maxOffsetX)
                                    gestureOffsetY = newY.coerceIn(-maxOffsetY, maxOffsetY)
                                } else {
                                    gestureOffsetX = newX
                                    gestureOffsetY = newY
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

                            if (gestureScale > 1.05f && !currentIsInfoSheetOpen) {
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
                                val absoluteDeltaX = abs(delta.x)
                                val absoluteDeltaY = abs(delta.y)
                                val totalDy = currentPos.y - startPos.y
                                val totalDx = currentPos.x - startPos.x

                                if (currentIsInfoSheetOpen) {
                                    if (isSwipingUpForInfo || (abs(totalDy) > 8f && abs(totalDy) > abs(totalDx) * 1.15f)) {
                                        isSwipingUpForInfo = true
                                        onSwipeUpDelta(delta.y)
                                        pointer.consume()
                                    }
                                } else if (!isSwipingUpForInfo && (isDraggingToDismiss || (delta.y > 0f && absoluteDeltaY > absoluteDeltaX * 1.5f && absoluteDeltaY > 10f))) {
                                    liveHoldJob?.cancel()
                                    isPlayingLivePhoto = false
                                    isLiveVideoRendered = false
                                    if (!isDraggingToDismiss) onDismissDragStart()
                                    isDraggingToDismiss = true

                                    gestureOffsetY += delta.y
                                    gestureOffsetX += delta.x

                                    val swipeProgress = (abs(gestureOffsetY) / (height * 0.8f)).coerceIn(0f, 1f)
                                    gestureScale = (1f - (swipeProgress * 0.4f)).coerceAtLeast(0.6f)
                                    val dimAlpha = (1f - (abs(gestureOffsetY) / (screenH / 2f))).coerceIn(0f, 1f)

                                    coroutineScope.launch {
                                        offsetX.snapTo(gestureOffsetX)
                                        offsetY.snapTo(gestureOffsetY)
                                        scale.snapTo(gestureScale)
                                        backgroundDimAlpha.snapTo(dimAlpha)
                                    }
                                    pointer.consume()
                                } else if (!isDraggingToDismiss && (isSwipingUpForInfo || (totalDy < -6f && abs(totalDy) > abs(totalDx) * 1.15f))) {
                                    liveHoldJob?.cancel()
                                    isSwipingUpForInfo = true
                                    onSwipeUpDelta(delta.y)
                                    pointer.consume()
                                }
                            }
                        }
                    }

                    liveHoldJob?.cancel()
                    if (isSwipingUpForInfo) {
                        onSwipeUpEnd()
                    } else if (triggeredLiveHold) {
                        isPlayingLivePhoto = false
                    } else if (!isPinching && !isDraggingToDismiss && !hasMovedSignificantDistance) {
                        pendingSingleTapJob?.cancel()
                        if (currentIsInfoSheetOpen) {
                            onTap()
                        } else {
                            pendingSingleTapJob = coroutineScope.launch {
                                delay(300L)
                                onTap()
                            }
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

            // LỚP VIDEO CỦA LIVE PHOTO
            if (item.isLivePhoto && isActivePage && (isPlayingLivePhoto || liveVideoAlpha > 0.01f) && liveExoPlayer != null) {
                val fallbackW = fullBitmap?.width ?: placeholderBitmap?.width ?: 0
                val fallbackH = fullBitmap?.height ?: placeholderBitmap?.height ?: 0
                val effVidW = if (liveVideoWidth > 0) liveVideoWidth else fallbackW
                val effVidH = if (liveVideoHeight > 0) liveVideoHeight else fallbackH

                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            isOpaque = false
                            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                applyCenterFitMatrix(this, effVidW, effVidH)
                            }
                            liveExoPlayer?.setVideoTextureView(this)
                        }
                    },
                    update = { textureView ->
                        liveExoPlayer?.setVideoTextureView(textureView)
                        applyCenterFitMatrix(textureView, effVidW, effVidH)
                    },
                    onRelease = { textureView ->
                        liveExoPlayer?.clearVideoTextureView(textureView)
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

            // Nút / Huy hiệu LIVE cho ảnh Live Photo
            if (item.isLivePhoto && !isInfoSheetOpen) {
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
    isInfoSheetOpen: Boolean = false,
    placeholderBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onTap: () -> Unit,
    onSwipeUpDelta: (Float) -> Unit = {},
    onSwipeUpEnd: () -> Unit = {},
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
    val currentIsInfoSheetOpen by rememberUpdatedState(isInfoSheetOpen)

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var gestureOffsetX = offsetX.value
            var gestureOffsetY = offsetY.value
            var gestureScale = scale.value
            var isDraggingToDismiss = false
            var isSwipingUpForInfo = false
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

                    val absoluteDeltaX = abs(delta.x)
                    val absoluteDeltaY = abs(delta.y)

                    val totalDy = currentPos.y - startPos.y
                    val totalDx = currentPos.x - startPos.x

                    if (currentIsInfoSheetOpen) {
                        if (isSwipingUpForInfo || (abs(totalDy) > 8f && abs(totalDy) > abs(totalDx) * 1.15f)) {
                            isSwipingUpForInfo = true
                            hasMoved = true
                            onSwipeUpDelta(delta.y)
                            pointer.consume()
                        }
                    } else if (!isSwipingUpForInfo && (isDraggingToDismiss || (delta.y > 0f && absoluteDeltaY > absoluteDeltaX * 1.5f && absoluteDeltaY > 10f))) {
                        if (!isDraggingToDismiss) onDismissDragStart()
                        isDraggingToDismiss = true

                        gestureOffsetY += delta.y
                        gestureOffsetX += delta.x

                        val swipeProgress = (abs(gestureOffsetY) / (screenH * 0.8f)).coerceIn(0f, 1f)
                        gestureScale = (1f - (swipeProgress * 0.4f)).coerceAtLeast(0.6f)

                        coroutineScope.launch {
                            offsetX.snapTo(gestureOffsetX)
                            offsetY.snapTo(gestureOffsetY)
                            scale.snapTo(gestureScale)
                        }
                        pointer.consume()
                    } else if (!isDraggingToDismiss && (isSwipingUpForInfo || (totalDy < -6f && abs(totalDy) > abs(totalDx) * 1.15f))) {
                        isSwipingUpForInfo = true
                        hasMoved = true
                        onSwipeUpDelta(delta.y)
                        pointer.consume()
                    }
                    if ((currentPos - startPos).getDistance() > 15f) {
                        hasMoved = true
                    }
                }
            }

            if (isSwipingUpForInfo) {
                onSwipeUpEnd()
            } else if (isDraggingToDismiss) {
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
            .background(Color.Black.copy(alpha = (1f - (abs(offsetY.value) / (screenH / 2f))).coerceIn(0f, 1f)))
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

private fun lonToTileX(lon: Double, zoom: Double): Double {
    val n = 2.0.pow(zoom)
    return ((lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0) * n
}

private fun latToTileY(lat: Double, zoom: Double): Double {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val latRad = Math.toRadians(clamped)
    val n = 2.0.pow(zoom)
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
}

private fun tileXToLon(tileX: Double, zoom: Double): Double {
    val n = 2.0.pow(zoom)
    return (tileX / n) * 360.0 - 180.0
}

private fun tileYToLat(tileY: Double, zoom: Double): Double {
    val n = 2.0.pow(zoom)
    val latRad = kotlin.math.atan(sinh(Math.PI * (1.0 - 2.0 * tileY / n)))
    return Math.toDegrees(latRad).coerceIn(-85.0511, 85.0511)
}

private fun formatMapSheetGroupDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown Date"
    val date = Date(timestamp)
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { time = date }

    val format = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    if (now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
    ) {
        return "Today"
    }

    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
        yesterday.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
    ) {
        return "Yesterday"
    }

    return format.format(date)
}

private fun applyCenterFitMatrix(textureView: TextureView, videoW: Int, videoH: Int) {
    val viewW = textureView.width.toFloat()
    val viewH = textureView.height.toFloat()
    if (viewW <= 0f || viewH <= 0f || videoW <= 0 || videoH <= 0) return

    val videoAspect = videoW.toFloat() / videoH.toFloat()
    val viewAspect = viewW / viewH

    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        scaleX = 1f
        scaleY = (viewW / videoAspect) / viewH
    } else {
        scaleX = (viewH * videoAspect) / viewW
        scaleY = 1f
    }

    val matrix = android.graphics.Matrix()
    matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
    textureView.setTransform(matrix)
}

private fun copyFileNameToClipboard(context: Context, fullFileName: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("File Name", fullFileName))
    Toast.makeText(context, "Copied: $fullFileName", Toast.LENGTH_SHORT).show()
}

/**
 * Định dạng vị trí hiển thị gọn gồm: Cấp Tỉnh/Thành phố + 1 cấp nhỏ hơn Tỉnh/Thành phố (Quận/Huyện hoặc Phường/Xã).
 */
private fun formatProvinceAndSubLevel(gps: MediaGpsInfo?): String {
    if (gps == null) return "No location data"

    val detailedParts = gps.detailedAddress
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val province = gps.admin1.trim().ifEmpty {
        if (detailedParts.size >= 2) detailedParts[detailedParts.size - 2]
        else detailedParts.lastOrNull().orEmpty()
    }

    val subCandidates = mutableListOf<String>()
    if (gps.district.isNotBlank()) {
        subCandidates.addAll(gps.district.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }
    if (subCandidates.isEmpty() && gps.ward.isNotBlank()) {
        subCandidates.addAll(gps.ward.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }
    if (subCandidates.isEmpty() && gps.placeName.isNotBlank()) {
        subCandidates.add(gps.placeName.trim())
    }
    if (subCandidates.isEmpty() && detailedParts.size >= 3) {
        val provIdx = detailedParts.indexOfFirst { it.equals(province, ignoreCase = true) }
        if (provIdx > 0) {
            subCandidates.add(detailedParts[provIdx - 1])
        } else {
            subCandidates.add(detailedParts[detailedParts.size - 3])
        }
    }

    val subLevel = subCandidates
        .lastOrNull { !it.equals(province, ignoreCase = true) }
        .orEmpty()

    return when {
        subLevel.isNotEmpty() && province.isNotEmpty() -> "$province, $subLevel"
        province.isNotEmpty() -> province
        subLevel.isNotEmpty() -> subLevel
        else -> String.format(Locale.US, "%.4f, %.4f", gps.latitude, gps.longitude)
    }
}

/**
 * Đọc tên thiết bị chụp ảnh / quay video (Make & Model) từ file SGV2:
 * 1. Hỗ trợ EXIF (JPEG/HEIC/PNG/WebP), XMP (tiff:Make / tiff:Model) và QuickTime::UserData / ItemList
 *    ngay cả trong ảnh HEIC/JPEG hoặc video nhúng của Live Photo.
 * 2. Với Video (MP4/MOV) hoặc Live Photo: duyệt trực tiếp cây Box nhị phân MP4/QuickTime (moov -> udta / meta / trak)
 *    bằng giải mã khối ngẫu nhiên O(1) để đọc chính xác các trường QuickTime::UserData (©mod, ©mak, modl, make, Model, Make,
 *    samsung smta, com.apple.quicktime.model, com.android.model) bất kể moov/udta nằm ở đầu, giữa hay cuối file.
 */
private fun extractDeviceNameFromVaultItem(
    context: Context,
    item: VaultItem,
    dek: ByteArray
): String? {
    try {
        val info = CryptoEngine.readSgv2FileInfo(context, item.uri, dek)
        val totalSize = info?.decryptedSize ?: 0L

        val headBytes = CryptoEngine.decryptSgv2FullSlice(
            context = context,
            uri = item.uri,
            dek = dek,
            plainOffset = 0L,
            maxLength = 512 * 1024
        )

        val effectiveTotal = if (info != null && info.isLivePhoto && info.liveVideoOffset > 0L && info.liveVideoSize > 0L) {
            info.liveVideoOffset + info.liveVideoSize
        } else {
            totalSize
        }

        val tailBytes = if (effectiveTotal > 256 * 1024L) {
            val tailLen = minOf(2 * 1024 * 1024L, effectiveTotal).toInt()
            CryptoEngine.decryptSgv2FullSlice(
                context = context,
                uri = item.uri,
                dek = dek,
                plainOffset = (effectiveTotal - tailLen).coerceAtLeast(0L),
                maxLength = tailLen
            )
        } else null

        // =========================================================================
        // ƯU TIÊN SỐ 1: "Model" trong QuickTime::UserData (udta / moov / QuickTime)
        // Nếu có trong bất kỳ phần nào của file (head, cây box MP4, hoặc tail) là dùng ngay!
        // =========================================================================
        if (headBytes != null && headBytes.isNotEmpty()) {
            parseQuickTimeUserDataModelPriority(headBytes)?.let { return it }
        }

        if (item.isVideo && totalSize > 32L) {
            extractDeviceFromMp4Boxes(
                context = context,
                uri = item.uri,
                dek = dek,
                baseOffset = 0L,
                streamLength = totalSize,
                onlyQuickTimeUserData = true
            )?.let { return it }
        } else if (info != null && info.isLivePhoto && info.liveVideoOffset > 0L && info.liveVideoSize > 32L) {
            extractDeviceFromMp4Boxes(
                context = context,
                uri = item.uri,
                dek = dek,
                baseOffset = info.liveVideoOffset,
                streamLength = info.liveVideoSize,
                onlyQuickTimeUserData = true
            )?.let { return it }
        }

        if (tailBytes != null && tailBytes.isNotEmpty()) {
            parseQuickTimeUserDataModelPriority(tailBytes)?.let { return it }
        }

        // =========================================================================
        // ƯU TIÊN SỐ 2: Các chuẩn QuickTime Metadata khác (keys/ilst) trong MP4 Boxes / Head / Tail
        // =========================================================================
        if (headBytes != null && headBytes.isNotEmpty()) {
            parseMp4MakeModel(headBytes)?.let { return it }
        }

        if (item.isVideo && totalSize > 32L) {
            extractDeviceFromMp4Boxes(
                context = context,
                uri = item.uri,
                dek = dek,
                baseOffset = 0L,
                streamLength = totalSize,
                onlyQuickTimeUserData = false
            )?.let { return it }
        } else if (info != null && info.isLivePhoto && info.liveVideoOffset > 0L && info.liveVideoSize > 32L) {
            extractDeviceFromMp4Boxes(
                context = context,
                uri = item.uri,
                dek = dek,
                baseOffset = info.liveVideoOffset,
                streamLength = info.liveVideoSize,
                onlyQuickTimeUserData = false
            )?.let { return it }
        }

        if (tailBytes != null && tailBytes.isNotEmpty()) {
            parseMp4MakeModel(tailBytes)?.let { return it }
        }

        // =========================================================================
        // ƯU TIÊN SỐ 3: EXIF & XMP truyền thống
        // =========================================================================
        if (headBytes != null && headBytes.isNotEmpty()) {
            try {
                val exif = ExifInterface(ByteArrayInputStream(headBytes))
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.trim('\u0000').orEmpty()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.trim('\u0000').orEmpty()
                formatMakeAndModel(make, model)?.let { return it }
            } catch (_: Exception) {}

            parseXmpMakeModel(headBytes)?.let { return it }
        }

        if (tailBytes != null && tailBytes.isNotEmpty()) {
            parseXmpMakeModel(tailBytes)?.let { return it }
        }
    } catch (_: Exception) {}
    return null
}

/**
 * Duyệt cây Box MP4/MOV ở mức O(1) (nhảy qua mdat/trak lớn) để đọc chính xác 'moov', 'udta' (QuickTime::UserData) và 'meta'.
 */
private fun extractDeviceFromMp4Boxes(
    context: Context,
    uri: android.net.Uri,
    dek: ByteArray,
    baseOffset: Long,
    streamLength: Long,
    onlyQuickTimeUserData: Boolean = false
): String? {
    val endOffset = baseOffset + streamLength
    var pos = baseOffset
    var topHop = 0

    while (pos + 8L <= endOffset && topHop < 64) {
        topHop++
        val header = CryptoEngine.decryptSgv2FullSlice(context, uri, dek, pos, 16) ?: break
        if (header.size < 8) break

        val size32 = readUInt32Be(header, 0)
        val type = String(header, 4, 4, Charsets.ISO_8859_1)
        val headerSize: Long
        val boxSize: Long

        when (size32) {
            1L -> {
                if (header.size < 16) break
                boxSize = readUInt64Be(header, 8)
                headerSize = 16L
            }
            0L -> {
                boxSize = endOffset - pos
                headerSize = 8L
            }
            else -> {
                boxSize = size32
                headerSize = 8L
            }
        }

        if (boxSize < headerSize || pos + boxSize > endOffset) break

        when (type) {
            "moov" -> {
                if (boxSize <= 2 * 1024 * 1024L) {
                    val moovBytes = CryptoEngine.decryptSgv2FullSlice(context, uri, dek, pos, boxSize.toInt())
                    if (moovBytes != null && moovBytes.isNotEmpty()) {
                        if (onlyQuickTimeUserData) {
                            parseQuickTimeUserDataModelPriority(moovBytes)?.let { return it }
                        } else {
                            parseMp4MakeModel(moovBytes)?.let { return it }
                            parseXmpMakeModel(moovBytes)?.let { return it }
                        }
                    }
                } else {
                    var childPos = pos + headerSize
                    val moovEnd = pos + boxSize
                    var childHop = 0
                    while (childPos + 8L <= moovEnd && childHop < 128) {
                        childHop++
                        val cHeader = CryptoEngine.decryptSgv2FullSlice(context, uri, dek, childPos, 16) ?: break
                        if (cHeader.size < 8) break
                        val cSize32 = readUInt32Be(cHeader, 0)
                        val cType = String(cHeader, 4, 4, Charsets.ISO_8859_1)
                        val cHeaderSize = if (cSize32 == 1L) 16L else 8L
                        val cBoxSize = when (cSize32) {
                            1L -> if (cHeader.size >= 16) readUInt64Be(cHeader, 8) else break
                            0L -> moovEnd - childPos
                            else -> cSize32
                        }
                        if (cBoxSize < cHeaderSize || childPos + cBoxSize > moovEnd) break

                        if (cType == "udta" || cType == "meta" || cType == "smta") {
                            val sliceLen = minOf(cBoxSize, 1024 * 1024L).toInt()
                            val boxBytes = CryptoEngine.decryptSgv2FullSlice(context, uri, dek, childPos, sliceLen)
                            if (boxBytes != null && boxBytes.isNotEmpty()) {
                                if (onlyQuickTimeUserData) {
                                    parseQuickTimeUserDataModelPriority(boxBytes)?.let { return it }
                                } else {
                                    parseMp4MakeModel(boxBytes)?.let { return it }
                                    parseXmpMakeModel(boxBytes)?.let { return it }
                                }
                            }
                        }
                        childPos += cBoxSize
                    }
                }
            }
            "udta", "meta", "uuid" -> {
                val sliceLen = minOf(boxSize, 1024 * 1024L).toInt()
                val boxBytes = CryptoEngine.decryptSgv2FullSlice(context, uri, dek, pos, sliceLen)
                if (boxBytes != null && boxBytes.isNotEmpty()) {
                    if (onlyQuickTimeUserData) {
                        parseQuickTimeUserDataModelPriority(boxBytes)?.let { return it }
                    } else {
                        parseMp4MakeModel(boxBytes)?.let { return it }
                        parseXmpMakeModel(boxBytes)?.let { return it }
                    }
                }
            }
        }

        pos += boxSize
    }
    return null
}

private fun readUInt16Be(bytes: ByteArray, offset: Int): Int {
    if (offset < 0 || offset + 2 > bytes.size) return 0
    return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
}

private fun readUInt32Be(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset + 4 > bytes.size) return 0L
    return ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)
}

private fun readUInt64Be(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset + 8 > bytes.size) return 0L
    return (readUInt32Be(bytes, offset) shl 32) or (readUInt32Be(bytes, offset + 4) and 0xFFFFFFFFL)
}

private fun formatMakeAndModel(rawMake: String, rawModel: String): String? {
    val make = cleanDeviceToken(rawMake)
    val model = cleanDeviceToken(rawModel)
    if (make.isEmpty() && model.isEmpty()) return null
    if (make.isEmpty()) return model
    if (model.isEmpty()) return make
    return if (model.startsWith(make, ignoreCase = true)) {
        model
    } else {
        "$make $model"
    }
}

private fun cleanDeviceToken(raw: String): String {
    val trimmed = raw
        .replace('\u0000', ' ')
        .replace(Regex("[\\x00-\\x1F\\x7F]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (trimmed.length < 2) return ""
    val lower = trimmed.lowercase(Locale.ROOT)
    if (lower in setOf("mdta", "mdir", "appl", "data", "ilst", "keys", "udta", "meta", "moov", "trak", "unknown")) {
        return ""
    }
    return trimmed
}

private fun parseXmpMakeModel(bytes: ByteArray): String? {
    return try {
        val text = String(bytes, Charsets.ISO_8859_1)
        val makeRegex = Regex("tiff:Make(?:\\s*=\\s*\"([^\"]+)\"|>(.+?)</tiff:Make>)", RegexOption.IGNORE_CASE)
        val modelRegex = Regex("tiff:Model(?:\\s*=\\s*\"([^\"]+)\"|>(.+?)</tiff:Model>)", RegexOption.IGNORE_CASE)
        val make = makeRegex.find(text)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim().orEmpty()
        val model = modelRegex.find(text)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim().orEmpty()
        formatMakeAndModel(make, model)
    } catch (_: Exception) {
        null
    }
}

/**
 * ƯU TIÊN SỐ 1: Trích xuất trực tiếp trường "Model" trong QuickTime::UserData (box 'udta').
 * Bao gồm:
 * - Các atom biểu diễn "Model" trong bảng ExifTool QuickTime::UserData:
 *   ©mod (0xA9 'm' 'o' 'd'), @mod (0x40 'm' 'o' 'd'), modl ('m' 'o' 'd' 'l'),
 *   ©MDL (0xA9 'M' 'D' 'L'), CNMN ('C' 'N' 'M' 'N'), MMMD ('M' 'M' 'M' 'D'),
 *   SMDL ('S' 'M' 'D' 'L'), smdl ('s' 'm' 'd' 'l').
 * - Trường hợp box 'udta' chứa trực tiếp chuỗi khóa "Model" hoặc atom 'meta' con bên trong 'udta' có khóa "Model".
 * Nếu có trường Model của QuickTime::UserData thì trả về ngay lập tức!
 */
private fun parseQuickTimeUserDataModelPriority(bytes: ByteArray): String? {
    try {
        var udtaModel = ""
        var udtaMake = ""

        // 1. Quét các Atom QuickTime::UserData biểu diễn "Model" (©mod, @mod, modl, ©MDL, CNMN, smdl, SMDL)
        var pos = 4
        while (pos <= bytes.size - 8) {
            val b0 = bytes[pos].toInt() and 0xFF
            val b1 = bytes[pos + 1].toInt() and 0xFF
            val b2 = bytes[pos + 2].toInt() and 0xFF
            val b3 = bytes[pos + 3].toInt() and 0xFF

            val isModelAtom = ((b0 == 0xA9 || b0 == 0x40) && b1 == 'm'.code && b2 == 'o'.code && b3 == 'd'.code) ||
                    (b0 == 'm'.code && b1 == 'o'.code && b2 == 'd'.code && b3 == 'l'.code) ||
                    (b0 == 0xA9 && b1 == 'M'.code && b2 == 'D'.code && b3 == 'L'.code) ||
                    (b0 == 'C'.code && b1 == 'N'.code && b2 == 'M'.code && b3 == 'N'.code) ||
                    (b0 == 's'.code && b1 == 'm'.code && b2 == 'd'.code && b3 == 'l'.code) ||
                    (b0 == 'S'.code && b1 == 'M'.code && b2 == 'D'.code && b3 == 'L'.code)

            val isMakeAtom = ((b0 == 0xA9 || b0 == 0x40) && b1 == 'm'.code && b2 == 'a'.code && b3 == 'k'.code) ||
                    (b0 == 'm'.code && b1 == 'a'.code && b2 == 'k'.code && b3 == 'e'.code) ||
                    (b0 == 0xA9 && b1 == 'm'.code && b2 == 'k'.code && b3 == 'r'.code)

            if (isModelAtom || isMakeAtom) {
                val boxStart = pos - 4
                val boxSize = readUInt32Be(bytes, boxStart).toInt()
                if (boxSize in 9..1024 && boxStart + boxSize <= bytes.size) {
                    val value = parseQuickTimeUserDataAtomPayload(bytes, pos + 4, boxStart + boxSize)
                    if (!value.isNullOrBlank()) {
                        if (isModelAtom && udtaModel.isEmpty()) {
                            udtaModel = value
                        } else if (isMakeAtom && udtaMake.isEmpty()) {
                            udtaMake = value
                        }
                    }
                }
            }
            pos++
        }

        if (udtaModel.isNotEmpty()) {
            return formatMakeAndModel(udtaMake, udtaModel) ?: udtaModel
        }

        // 2. Kiểm tra bên trong các box 'udta' xem có khóa "Model" hoặc atom con chứa Model không
        var uPos = 4
        while (uPos <= bytes.size - 8) {
            if (bytes[uPos] == 'u'.code.toByte() &&
                bytes[uPos + 1] == 'd'.code.toByte() &&
                bytes[uPos + 2] == 't'.code.toByte() &&
                bytes[uPos + 3] == 'a'.code.toByte()
            ) {
                val udtaStart = uPos - 4
                val udtaSize = readUInt32Be(bytes, udtaStart).toInt()
                if (udtaSize >= 12 && udtaStart + udtaSize <= bytes.size) {
                    val udtaSlice = bytes.copyOfRange(udtaStart + 8, udtaStart + udtaSize)
                    parseKeysIlstMakeModel(udtaSlice)?.let { return it }
                    parseBinaryTiffMakeModel(udtaSlice)?.let { return it }
                    val udtaText = String(udtaSlice, Charsets.ISO_8859_1)
                    val literalModel = Regex("(?:^|[^a-zA-Z])Model[^\\x20-\\x7E]{1,20}([\\x20-\\x7E]{2,48})")
                        .find(udtaText)?.groupValues?.getOrNull(1)?.let { cleanDeviceToken(it) }.orEmpty()
                    if (literalModel.isNotEmpty()) return literalModel
                }
            }
            uPos++
        }
    } catch (_: Exception) {}
    return null
}

private fun parseKeysIlstMakeModel(bytes: ByteArray): String? {
    var foundMake = ""
    var foundModel = ""
    var idx = 4
    while (idx <= bytes.size - 16) {
        if (bytes[idx] == 'k'.code.toByte() &&
            bytes[idx + 1] == 'e'.code.toByte() &&
            bytes[idx + 2] == 'y'.code.toByte() &&
            bytes[idx + 3] == 's'.code.toByte()
        ) {
            val keysBoxStart = idx - 4
            val keysBoxSize = readUInt32Be(bytes, keysBoxStart).toInt()
            if (keysBoxSize in 16..(bytes.size - keysBoxStart)) {
                val entryCount = readUInt32Be(bytes, keysBoxStart + 12).toInt()
                if (entryCount in 1..256) {
                    val keyNames = arrayOfNulls<String>(entryCount + 1)
                    var kPos = keysBoxStart + 16
                    val keysEnd = keysBoxStart + keysBoxSize
                    var kIdx = 1
                    while (kIdx <= entryCount && kPos + 8 <= keysEnd) {
                        val kSize = readUInt32Be(bytes, kPos).toInt()
                        if (kSize < 8 || kPos + kSize > keysEnd) break
                        val keyName = String(bytes, kPos + 8, kSize - 8, Charsets.UTF_8).trim()
                        keyNames[kIdx] = keyName
                        kPos += kSize
                        kIdx++
                    }

                    var searchPos = keysEnd
                    val searchLimit = minOf(bytes.size - 8, keysEnd + 4096)
                    while (searchPos <= searchLimit) {
                        if (bytes[searchPos + 4] == 'i'.code.toByte() &&
                            bytes[searchPos + 5] == 'l'.code.toByte() &&
                            bytes[searchPos + 6] == 's'.code.toByte() &&
                            bytes[searchPos + 7] == 't'.code.toByte()
                        ) {
                            val ilstSize = readUInt32Be(bytes, searchPos).toInt()
                            val ilstEnd = if (ilstSize >= 8 && searchPos + ilstSize <= bytes.size) {
                                searchPos + ilstSize
                            } else {
                                bytes.size
                            }
                            var itemPos = searchPos + 8
                            while (itemPos + 8 <= ilstEnd) {
                                val itemSize = readUInt32Be(bytes, itemPos).toInt()
                                val itemIndex = readUInt32Be(bytes, itemPos + 4).toInt()
                                if (itemSize < 8 || itemPos + itemSize > ilstEnd) break

                                if (itemIndex in 1..entryCount) {
                                    val rawKey = keyNames[itemIndex].orEmpty()
                                    val keyName = rawKey.lowercase(Locale.ROOT)
                                    val valStr = extractDataAtomString(bytes, itemPos + 8, itemPos + itemSize)
                                    if (!valStr.isNullOrBlank()) {
                                        when {
                                            rawKey == "Model" || keyName == "model" -> {
                                                // Ưu tiên tuyệt đối khóa "Model"
                                                foundModel = valStr
                                            }
                                            keyName.endsWith(".model") || keyName == "camera.model" -> {
                                                if (foundModel.isEmpty()) foundModel = valStr
                                            }
                                            keyName.endsWith(".make") || keyName.endsWith(".manufacturer") || keyName == "make" || keyName == "manufacturer" -> {
                                                if (foundMake.isEmpty()) foundMake = valStr
                                            }
                                        }
                                    }
                                }
                                itemPos += itemSize
                            }
                            break
                        }
                        searchPos++
                    }
                }
            }
        }
        idx++
    }
    if (foundModel.isNotEmpty() || foundMake.isNotEmpty()) {
        return formatMakeAndModel(foundMake, foundModel)
    }
    return null
}

private fun parseMp4MakeModel(bytes: ByteArray): String? {
    try {
        // 1. Ưu tiên số 1: QuickTime::UserData "Model" (©mod, modl, udta -> Model)
        parseQuickTimeUserDataModelPriority(bytes)?.let { return it }

        // 2. Giải mã bảng 'keys' -> 'ilst' (QuickTime Metadata mdta)
        parseKeysIlstMakeModel(bytes)?.let { return it }

        // 3. Quét khối TIFF nhị phân nếu được nhúng trong container
        parseBinaryTiffMakeModel(bytes)?.let { return it }

        // 4. Fallback Regex cho các thẻ key-value nhúng liền kề
        val text = String(bytes, Charsets.ISO_8859_1)
        val appleModel = Regex("com\\.apple\\.quicktime\\.model[^\\x20-\\x7E]{1,24}([\\x20-\\x7E]{3,40})")
            .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val appleMake = Regex("com\\.apple\\.quicktime\\.make[^\\x20-\\x7E]{1,24}([\\x20-\\x7E]{2,30})")
            .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (!appleModel.startsWith("com.", ignoreCase = true) && !appleModel.startsWith("mdta", ignoreCase = true)) {
            formatMakeAndModel(appleMake.takeIf { !it.startsWith("com.") }.orEmpty(), appleModel)?.let { return it }
        }

        val androidModel = Regex("com\\.android\\.model[^\\x20-\\x7E]{1,24}([\\x20-\\x7E]{2,40})")
            .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val androidMake = Regex("com\\.android\\.manufacturer[^\\x20-\\x7E]{1,24}([\\x20-\\x7E]{2,30})")
            .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (!androidModel.startsWith("com.", ignoreCase = true) && !androidModel.startsWith("mdta", ignoreCase = true)) {
            formatMakeAndModel(androidMake.takeIf { !it.startsWith("com.") }.orEmpty(), androidModel)?.let { return it }
        }
    } catch (_: Exception) {}
    return null
}

private fun parseQuickTimeUserDataAtomPayload(
    bytes: ByteArray,
    payloadStart: Int,
    payloadEnd: Int
): String? {
    val len = payloadEnd - payloadStart
    if (len <= 1 || payloadStart < 0 || payloadEnd > bytes.size) return null

    // Định dạng 1: Chứa sub-atom 'data'
    extractDataAtomString(bytes, payloadStart, payloadEnd)?.let { return it }

    // Định dạng 2: QuickTime International Text ([strLen: 2B][langCode: 2B][string])
    if (len >= 5) {
        val strLen = readUInt16Be(bytes, payloadStart)
        if (strLen in 2..(len - 4)) {
            val candidate = cleanDeviceToken(String(bytes, payloadStart + 4, strLen, Charsets.UTF_8))
            if (candidate.isNotEmpty()) return candidate
        }
    }

    // Định dạng 3: Chuỗi UTF-8 trực tiếp (bỏ qua tối đa 4 byte đầu nếu là byte điều khiển 0x00..0x1F)
    var rawOffset = payloadStart
    val maxSkip = minOf(payloadEnd, payloadStart + 4)
    while (rawOffset < maxSkip && (bytes[rawOffset].toInt() and 0xFF) < 0x20) {
        rawOffset++
    }
    val rawLen = payloadEnd - rawOffset
    if (rawLen >= 2) {
        val candidate = cleanDeviceToken(String(bytes, rawOffset, rawLen, Charsets.UTF_8))
        if (candidate.isNotEmpty()) return candidate
    }
    return null
}

private fun extractDataAtomString(bytes: ByteArray, start: Int, end: Int): String? {
    var p = start
    while (p + 16 <= end) {
        if (bytes[p + 4] == 'd'.code.toByte() &&
            bytes[p + 5] == 'a'.code.toByte() &&
            bytes[p + 6] == 't'.code.toByte() &&
            bytes[p + 7] == 'a'.code.toByte()
        ) {
            val dSize = readUInt32Be(bytes, p).toInt()
            val dEnd = if (dSize >= 16 && p + dSize <= end) p + dSize else end
            val strStart = p + 16
            if (dEnd > strStart) {
                val str = cleanDeviceToken(String(bytes, strStart, dEnd - strStart, Charsets.UTF_8))
                if (str.isNotEmpty()) return str
            }
        }
        p++
    }
    return null
}

private fun parseBinaryTiffMakeModel(bytes: ByteArray): String? {
    val len = bytes.size
    var i = 0
    while (i <= len - 16) {
        val isLe = bytes[i] == 0x49.toByte() && bytes[i + 1] == 0x49.toByte() &&
                bytes[i + 2] == 0x2A.toByte() && bytes[i + 3] == 0x00.toByte()
        val isBe = bytes[i] == 0x4D.toByte() && bytes[i + 1] == 0x4D.toByte() &&
                bytes[i + 2] == 0x00.toByte() && bytes[i + 3] == 0x2A.toByte()
        if (isLe || isBe) {
            fun u16(pos: Int): Int {
                if (pos < 0 || pos + 2 > len) return -1
                val b0 = bytes[pos].toInt() and 0xFF
                val b1 = bytes[pos + 1].toInt() and 0xFF
                return if (isLe) (b1 shl 8) or b0 else (b0 shl 8) or b1
            }
            fun u32(pos: Int): Int {
                if (pos < 0 || pos + 4 > len) return -1
                val b0 = bytes[pos].toInt() and 0xFF
                val b1 = bytes[pos + 1].toInt() and 0xFF
                val b2 = bytes[pos + 2].toInt() and 0xFF
                val b3 = bytes[pos + 3].toInt() and 0xFF
                return if (isLe) {
                    (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                } else {
                    (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
                }
            }
            val ifdRel = u32(i + 4)
            if (ifdRel in 8..(len - i - 2)) {
                val ifdPos = i + ifdRel
                val count = u16(ifdPos)
                if (count in 1..128 && ifdPos + 2 + count * 12 <= len) {
                    var make = ""
                    var model = ""
                    for (e in 0 until count) {
                        val entryPos = ifdPos + 2 + e * 12
                        val tag = u16(entryPos)
                        val type = u16(entryPos + 2)
                        val charCount = u32(entryPos + 4)
                        if (type == 2 && charCount in 2..64) {
                            val strPos = if (charCount <= 4) entryPos + 8 else i + u32(entryPos + 8)
                            if (strPos >= i && strPos + charCount <= len) {
                                val s = cleanDeviceToken(String(bytes, strPos, charCount, Charsets.UTF_8))
                                if (tag == 0x010F && make.isEmpty()) make = s
                                else if (tag == 0x0110 && model.isEmpty()) model = s
                            }
                        }
                    }
                    formatMakeAndModel(make, model)?.let { return it }
                }
            }
        }
        i++
    }
    return null
}

@Composable
fun DetailsGpsHeatmapMap(
    currentGps: MediaGpsInfo?,
    allVaultGps: List<MediaGpsInfo>,
    onMapTouchChanged: (Boolean) -> Unit = {},
    isFullScreen: Boolean = false,
    controlsBottomPadding: Dp = 0.dp,
    onViewportChanged: (centerLat: Double, centerLon: Double, zoom: Float, widthPx: Float, heightPx: Float) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxMapZoom = 21.5f
    val maxTileZoom = 19

    val initialCenterLat = currentGps?.latitude
        ?: allVaultGps.firstOrNull()?.latitude
        ?: 21.0031
    val initialCenterLon = currentGps?.longitude
        ?: allVaultGps.firstOrNull()?.longitude
        ?: 105.8202
    val initialZoom = if (currentGps != null) 15.5f else 5f

    var centerLat by remember(currentGps?.latitude, currentGps?.longitude) { mutableStateOf(initialCenterLat) }
    var centerLon by remember(currentGps?.latitude, currentGps?.longitude) { mutableStateOf(initialCenterLon) }
    var zoom by remember(currentGps?.latitude, currentGps?.longitude) { mutableFloatStateOf(initialZoom) }
    var mapWidthPx by remember { mutableFloatStateOf(0f) }
    var mapHeightPx by remember { mutableFloatStateOf(0f) }
    var tileLoadTick by remember { mutableIntStateOf(0) }
    var retryWave by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        MapTileCache.initDiskCache(context)
    }

    LaunchedEffect(centerLat, centerLon, zoom, mapWidthPx, mapHeightPx) {
        onViewportChanged(centerLat, centerLon, zoom, mapWidthPx, mapHeightPx)
    }

    fun requestTileAsync(zInt: Int, wrappedTx: Int, ty: Int) {
        val tileKey = "$zInt/$wrappedTx/$ty"
        if (MapTileCache.get(tileKey) != null) return
        if (!MapTileCache.markInFlight(tileKey)) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val bmp = MapTileCache.downloadTileBitmap(zInt, wrappedTx, ty)
                if (bmp != null) {
                    MapTileCache.put(tileKey, bmp)
                    withContext(Dispatchers.Main) {
                        MapTileCache.version.intValue++
                        tileLoadTick++
                    }
                }
            } finally {
                MapTileCache.unmarkInFlight(tileKey)
            }
        }
    }

    // Chủ động tải trước (Prefetch) cả mức zoom hiện tại (zInt) LẪN 2 mức zoom lân cận (zInt + 1 và zInt - 1)
    LaunchedEffect(centerLat, centerLon, zoom, retryWave) {
        val zInt = zoom.toInt().coerceIn(2, maxTileZoom)
        val numTiles = 1 shl zInt
        val cx = floor(lonToTileX(centerLon, zInt.toDouble())).toInt()
        val cy = floor(latToTileY(centerLat, zInt.toDouble())).toInt()

        var anyMissing = false
        for (dy in -2..2) {
            val ty = cy + dy
            if (ty !in 0 until numTiles) continue
            for (dx in -2..2) {
                val wrappedTx = (((cx + dx) % numTiles) + numTiles) % numTiles
                val key = "$zInt/$wrappedTx/$ty"
                if (MapTileCache.get(key) == null) {
                    anyMissing = true
                    requestTileAsync(zInt, wrappedTx, ty)
                }
            }
        }

        for (adjZ in listOf(zInt + 1, zInt - 1)) {
            if (adjZ !in 2..maxTileZoom) continue
            val adjNum = 1 shl adjZ
            val adjCx = floor(lonToTileX(centerLon, adjZ.toDouble())).toInt()
            val adjCy = floor(latToTileY(centerLat, adjZ.toDouble())).toInt()
            for (dy in -1..1) {
                val ty = adjCy + dy
                if (ty !in 0 until adjNum) continue
                for (dx in -1..1) {
                    val wrappedTx = (((adjCx + dx) % adjNum) + adjNum) % adjNum
                    requestTileAsync(adjZ, wrappedTx, ty)
                }
            }
        }

        if (anyMissing && retryWave < 5) {
            delay(1200L)
            retryWave++
        }
    }

    val fitAllPoints = {
        if (allVaultGps.isNotEmpty()) {
            val minLat = allVaultGps.minOf { it.latitude }
            val maxLat = allVaultGps.maxOf { it.latitude }
            val minLon = allVaultGps.minOf { it.longitude }
            val maxLon = allVaultGps.maxOf { it.longitude }
            centerLat = (minLat + maxLat) / 2.0
            centerLon = (minLon + maxLon) / 2.0
            val latSpan = (maxLat - minLat).coerceAtLeast(0.002)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.002)
            val maxSpan = maxOf(latSpan, lonSpan)
            zoom = when {
                maxSpan > 60.0 -> 2.5f
                maxSpan > 25.0 -> 4f
                maxSpan > 10.0 -> 5.5f
                maxSpan > 4.0 -> 7f
                maxSpan > 1.2 -> 8.5f
                maxSpan > 0.3 -> 10.5f
                maxSpan > 0.08 -> 12.5f
                maxSpan > 0.02 -> 14f
                else -> 15.5f
            }
        }
    }

    val mapShape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(mapShape)
            .background(Color(0xFF0E1014))
            .then(
                if (isFullScreen) Modifier
                else Modifier.border(0.8.dp, Color(0xFF24262D), mapShape)
            )
            .onSizeChanged { intSize ->
                mapWidthPx = intSize.width.toFloat()
                mapHeightPx = intSize.height.toFloat()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onMapTouchChanged(true)
                    var isPinchingMap = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            if (changes.isEmpty() || changes.all { !it.pressed }) break

                            val mapW = if (mapWidthPx > 0f) mapWidthPx else size.width.toFloat()
                            val mapH = if (mapHeightPx > 0f) mapHeightPx else size.height.toFloat()

                            if (changes.size >= 2) {
                                isPinchingMap = true
                                val p1 = changes[0]
                                val p2 = changes[1]
                                val curMid = (p1.position + p2.position) / 2f
                                val prevMid = (p1.previousPosition + p2.previousPosition) / 2f
                                val curDist = (p1.position - p2.position).getDistance()
                                val prevDist = (p1.previousPosition - p2.previousPosition).getDistance()

                                // 1. Xác định tọa độ địa lý đang nằm ngay dưới điểm trung tâm của 2 ngón tay ở frame trước (prevMid)
                                val zOld = zoom.toDouble()
                                val maxTileOld = 2.0.pow(zOld)
                                val anchorTileXOld = lonToTileX(centerLon, zOld) + (prevMid.x - mapW / 2f) / 512.0
                                val anchorTileYOld = latToTileY(centerLat, zOld) + (prevMid.y - mapH / 2f) / 512.0
                                val wrappedAnchorXOld = ((anchorTileXOld % maxTileOld) + maxTileOld) % maxTileOld
                                val anchorLon = tileXToLon(wrappedAnchorXOld, zOld)
                                val anchorLat = tileYToLat(anchorTileYOld.coerceIn(0.0, maxTileOld), zOld)

                                // 2. Tính mức zoom mới theo khoảng cách giữa 2 ngón tay
                                val newZoom = if (prevDist > 10f && curDist > 10f) {
                                    val ratio = (curDist / prevDist).toDouble()
                                    val zoomDelta = (ln(ratio) / ln(2.0)).toFloat()
                                    (zoom + zoomDelta).coerceIn(2f, maxMapZoom)
                                } else {
                                    zoom
                                }

                                // 3. Đặt tọa độ địa lý đó nằm đúng dưới điểm trung tâm mới của 2 ngón tay (curMid)
                                // -> bản đồ vừa zoom theo khoảng cách 2 ngón, vừa di chuyển bám sát theo điểm trung tâm của 2 ngón tay
                                val zNew = newZoom.toDouble()
                                val maxTileNew = 2.0.pow(zNew)
                                val anchorTileXNew = lonToTileX(anchorLon, zNew)
                                val anchorTileYNew = latToTileY(anchorLat, zNew)
                                val newCenterTileX = anchorTileXNew - (curMid.x - mapW / 2f) / 512.0
                                val newCenterTileY = anchorTileYNew - (curMid.y - mapH / 2f) / 512.0
                                val wrappedCenterTileX = ((newCenterTileX % maxTileNew) + maxTileNew) % maxTileNew

                                zoom = newZoom
                                centerLon = tileXToLon(wrappedCenterTileX, zNew)
                                centerLat = tileYToLat(newCenterTileY.coerceIn(0.0, maxTileNew), zNew)

                                changes.forEach { it.consume() }
                            } else if (changes.size == 1 && !isPinchingMap) {
                                val change = changes[0]
                                val delta = change.position - change.previousPosition
                                if (delta.getDistance() > 0.5f) {
                                    val zEff = zoom.toDouble()
                                    val curTileX = lonToTileX(centerLon, zEff) - (delta.x / 512.0)
                                    val curTileY = latToTileY(centerLat, zEff) - (delta.y / 512.0)
                                    val maxTile = 2.0.pow(zEff)
                                    val wrappedTileX = ((curTileX % maxTile) + maxTile) % maxTile
                                    centerLon = tileXToLon(wrappedTileX, zEff)
                                    centerLat = tileYToLat(curTileY.coerceIn(0.0, maxTile), zEff)
                                    change.consume()
                                }
                            }
                        }
                    } finally {
                        onMapTouchChanged(false)
                    }
                }
            }
    ) {
        val currentCacheVersion = MapTileCache.version.intValue
        val currentLocalTick = tileLoadTick

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (currentCacheVersion < 0 || currentLocalTick < 0) return@Canvas

            val canvasW = size.width
            val canvasH = size.height
            val zInt = zoom.toInt().coerceIn(2, maxTileZoom)
            val scaleFactor = 2.0.pow((zoom - zInt).toDouble()).toFloat()
            val tileSizePx = 512f * scaleFactor
            val numTiles = 1 shl zInt

            val centerTileX = lonToTileX(centerLon, zInt.toDouble())
            val centerTileY = latToTileY(centerLat, zInt.toDouble())

            val startTileX = floor(centerTileX - (canvasW / 2f) / tileSizePx).toInt()
            val endTileX = floor(centerTileX + (canvasW / 2f) / tileSizePx).toInt()
            val startTileY = floor(centerTileY - (canvasH / 2f) / tileSizePx).toInt().coerceAtLeast(0)
            val endTileY = floor(centerTileY + (canvasH / 2f) / tileSizePx).toInt().coerceAtMost(numTiles - 1)

            // 1. Vẽ các ô bản đồ Dark Retina HD (512x512) với FilterQuality.High + Parent/Child Fallback chống trễ khi Zoom
            for (ty in startTileY..endTileY) {
                for (tx in startTileX..endTileX) {
                    val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
                    val screenLeft = (canvasW / 2f) + ((tx - centerTileX) * tileSizePx).toFloat()
                    val screenTop = (canvasH / 2f) + ((ty - centerTileY) * tileSizePx).toFloat()

                    val tileKey = "$zInt/$wrappedTx/$ty"
                    val cachedBmp = MapTileCache.get(tileKey)

                    if (cachedBmp != null) {
                        val drawScale = (tileSizePx + 0.75f) / cachedBmp.width.toFloat().coerceAtLeast(1f)
                        translate(screenLeft, screenTop) {
                            scale(drawScale, drawScale, pivot = Offset.Zero) {
                                drawImage(
                                    image = cachedBmp,
                                    filterQuality = FilterQuality.High
                                )
                            }
                        }
                    } else {
                        var drawnFallback = false
                        for (diff in 1..4) {
                            val parentZ = zInt - diff
                            if (parentZ < 2) break
                            val parentX = wrappedTx shr diff
                            val parentY = ty shr diff
                            val parentBmp = MapTileCache.get("$parentZ/$parentX/$parentY")
                            if (parentBmp != null) {
                                val subDiv = 1 shl diff
                                val srcW = (parentBmp.width / subDiv).coerceAtLeast(1)
                                val srcH = (parentBmp.height / subDiv).coerceAtLeast(1)
                                val srcX = (wrappedTx and (subDiv - 1)) * srcW
                                val srcY = (ty and (subDiv - 1)) * srcH
                                drawImage(
                                    image = parentBmp,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset(screenLeft.toInt(), screenTop.toInt()),
                                    dstSize = IntSize((tileSizePx + 1.5f).toInt(), (tileSizePx + 1.5f).toInt()),
                                    filterQuality = FilterQuality.Medium
                                )
                                drawnFallback = true
                                break
                            }
                        }

                        if (!drawnFallback) {
                            drawRect(
                                color = Color(0xFF14171E),
                                topLeft = Offset(screenLeft, screenTop),
                                size = Size(tileSizePx, tileSizePx)
                            )
                        }

                        if (zInt + 1 <= maxTileZoom) {
                            val halfTile = (tileSizePx / 2f + 1f).toInt()
                            for (cyOff in 0..1) {
                                for (cxOff in 0..1) {
                                    val childBmp = MapTileCache.get("${zInt + 1}/${wrappedTx * 2 + cxOff}/${ty * 2 + cyOff}")
                                    if (childBmp != null) {
                                        drawImage(
                                            image = childBmp,
                                            srcOffset = IntOffset.Zero,
                                            srcSize = IntSize(childBmp.width, childBmp.height),
                                            dstOffset = IntOffset(
                                                (screenLeft + cxOff * (tileSizePx / 2f)).toInt(),
                                                (screenTop + cyOff * (tileSizePx / 2f)).toInt()
                                            ),
                                            dstSize = IntSize(halfTile, halfTile),
                                            filterQuality = FilterQuality.Medium
                                        )
                                    }
                                }
                            }
                        }

                        requestTileAsync(zInt, wrappedTx, ty)
                    }
                }
            }

            // 2. Vẽ các điểm HEATMAP:
            // - Khi zoom map sẽ KHÔNG hợp nhất (không gom cụm) các điểm heatmap với nhau.
            // - Mỗi location là 1 chấm tròn màu vàng có kích thước cố định so với màn hình vật lý (14.dp).
            // - Ở giữa chấm tròn là màu vàng thuần khiết (#FFFF00) có alpha 50%, mờ dần ra phía ngoài cho tới khi đạt alpha 0%.
            val dotRadiusPx = 14.dp.toPx()
            val centerPureYellow = Color(0xFFFFFF00).copy(alpha = 0.50f)
            val edgeTransparentYellow = Color(0xFFFFFF00).copy(alpha = 0.0f)

            val pointsToRender = if (allVaultGps.isNotEmpty()) {
                allVaultGps
            } else if (currentGps != null) {
                listOf(currentGps)
            } else {
                emptyList()
            }

            for (pt in pointsToRender) {
                val pTileX = lonToTileX(pt.longitude, zInt.toDouble())
                val pTileY = latToTileY(pt.latitude, zInt.toDouble())
                var deltaTileX = pTileX - centerTileX
                if (deltaTileX > numTiles / 2.0) deltaTileX -= numTiles
                else if (deltaTileX < -numTiles / 2.0) deltaTileX += numTiles

                val px = (canvasW / 2f) + (deltaTileX * tileSizePx).toFloat()
                val py = (canvasH / 2f) + ((pTileY - centerTileY) * tileSizePx).toFloat()

                if (px in -dotRadiusPx..(canvasW + dotRadiusPx) && py in -dotRadiusPx..(canvasH + dotRadiusPx)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to centerPureYellow,
                            1.0f to edgeTransparentYellow,
                            center = Offset(px, py),
                            radius = dotRadiusPx
                        ),
                        radius = dotRadiusPx,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Góc trên bên trái: Chip "... media" + nút Fit All nếu có nhiều ảnh
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(if (isFullScreen) Modifier.statusBarsPadding() else Modifier)
                .padding(top = if (isFullScreen) 12.dp else 8.dp, start = if (isFullScreen) 12.dp else 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFFF00).copy(alpha = 0.85f))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${maxOf(allVaultGps.size, if (currentGps != null) 1 else 0)} media",
                    fontFamily = GoogleSansRounded,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            if (allVaultGps.size > 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .clickable { fitAllPoints() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("map_fit_all_button")
                ) {
                    Text(
                        text = "Fit All (${allVaultGps.size})",
                        fontFamily = GoogleSansRounded,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Cụm 3 nút góc dưới bên phải xếp thành 1 HÀNG DỌC (Current -> Cộng -> Trừ)
        val btnSize = if (isFullScreen) 56.dp else 28.dp
        val btnCorner = if (isFullScreen) 14.dp else 7.dp
        val iconSize = if (isFullScreen) 32.dp else 16.dp
        val btnGap = if (isFullScreen) 8.dp else 4.dp
        val outerCircleRadius = if (isFullScreen) 20.dp else 10.dp
        val innerCircleRadius = if (isFullScreen) 9.6.dp else 4.8.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(btnGap),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(if (isFullScreen && controlsBottomPadding == 0.dp) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    end = if (isFullScreen) 16.dp else 8.dp,
                    bottom = if (isFullScreen) maxOf(16.dp, controlsBottomPadding) else 8.dp
                )
        ) {
            // Nút Current (vuông bo tròn với 2 hình tròn đồng tâm kích thước lớn hơn)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(btnCorner))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable {
                        if (currentGps != null) {
                            centerLat = currentGps.latitude
                            centerLon = currentGps.longitude
                            zoom = 16f
                        }
                    }
                    .testTag("map_focus_current_button")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.38f),
                        radius = outerCircleRadius.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = innerCircleRadius.toPx(),
                        center = center
                    )
                }
            }

            // Nút Cộng (+)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(btnCorner))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { zoom = (zoom + 1f).coerceIn(2f, maxMapZoom) }
                    .testTag("map_zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Zoom In",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // Nút Trừ (-)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(btnCorner))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { zoom = (zoom - 1f).coerceIn(2f, maxMapZoom) }
                    .testTag("map_zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Zoom Out",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}