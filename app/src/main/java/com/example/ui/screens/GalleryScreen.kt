package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.view.TextureView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.example.coil.EncryptedUriFetcher
import com.example.media.EncryptedDataSourceFactory
import com.example.ui.VaultItem
import com.example.ui.VaultViewModel
import com.example.ui.VideoFramePickRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ------------------------------------------------------------------------------------
// CACHE BITMAP THUMB DÙNG CHUNG GIỮA GalleryScreen VÀ MediaViewerScreen
// ------------------------------------------------------------------------------------
object ThumbBitmapCache {
    private val maxCacheKb: Int = run {
        val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxMemKb / 10).coerceIn(12 * 1024, 32 * 1024)
    }

    val version = mutableIntStateOf(0)

    private val cache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap): Int {
            return ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
        }
    }

    private val blurredCache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(4096) {
        override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap): Int {
            return ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
        }
    }

    private val blurredAspectCache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(4096) {
        override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap): Int {
            return ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
        }
    }

    @Synchronized
    fun get(key: String): androidx.compose.ui.graphics.ImageBitmap? = cache.get(key)

    @Synchronized
    fun getBlurred(key: String): androidx.compose.ui.graphics.ImageBitmap? = blurredCache.get(key) ?: cache.get(key)

    @Synchronized
    fun getBlurredAspect(key: String): androidx.compose.ui.graphics.ImageBitmap? = blurredAspectCache.get(key) ?: cache.get(key)

    @Synchronized
    fun put(key: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        cache.put(key, bitmap)
        try {
            val src = bitmap.asAndroidBitmap()
            val bw = src.width.coerceAtLeast(1)
            val bh = src.height.coerceAtLeast(1)
            val edge = minOf(bw, bh)
            val cropX = (bw - edge) / 2
            val cropY = (bh - edge) / 2
            val square = Bitmap.createBitmap(src, cropX, cropY, edge, edge)
            val tiny = Bitmap.createScaledBitmap(square, 20, 20, true)
            val blurred = Bitmap.createScaledBitmap(tiny, 48, 48, true)
            if (square !== src) square.recycle()
            if (tiny !== blurred) tiny.recycle()
            blurredCache.put(key, blurred.asImageBitmap())

            val ratio = bw.toFloat() / bh.toFloat()
            val tinyAspectW = if (ratio >= 1f) 32 else (32f * ratio).toInt().coerceAtLeast(8)
            val tinyAspectH = if (ratio < 1f) 32 else (32f / ratio).toInt().coerceAtLeast(8)
            val tinyAspect = Bitmap.createScaledBitmap(src, tinyAspectW, tinyAspectH, true)
            val smoothAspect = Bitmap.createScaledBitmap(tinyAspect, tinyAspectW * 2, tinyAspectH * 2, true)
            if (tinyAspect !== smoothAspect && tinyAspect !== src) tinyAspect.recycle()
            blurredAspectCache.put(key, smoothAspect.asImageBitmap())
        } catch (_: Throwable) {}
        version.intValue++
    }

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
        blurredCache.remove(key)
        blurredAspectCache.remove(key)
        version.intValue++
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
        blurredCache.evictAll()
        blurredAspectCache.evictAll()
        version.intValue++
    }
}

private fun findMediaItemIndexAtPosition(
    gridState: LazyGridState,
    touchPos: Offset,
    keyToIndex: Map<String, Int>
): Int? {
    val visibleItems = gridState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    for (info in visibleItems) {
        val keyStr = info.key as? String ?: continue
        val mediaIdx = keyToIndex[keyStr] ?: continue
        val left = info.offset.x.toFloat()
        val top = info.offset.y.toFloat()
        val right = left + info.size.width
        val bottom = top + info.size.height
        if (touchPos.x in left..right && touchPos.y in top..bottom) {
            return mediaIdx
        }
    }

    var closestIdx: Int? = null
    var minDistanceSq = Float.MAX_VALUE
    for (info in visibleItems) {
        val keyStr = info.key as? String ?: continue
        val mediaIdx = keyToIndex[keyStr] ?: continue
        val centerX = info.offset.x + info.size.width / 2f
        val centerY = info.offset.y + info.size.height / 2f
        val dx = touchPos.x - centerX
        val dy = touchPos.y - centerY
        val distSq = dx * dx + dy * dy
        if (distSq < minDistanceSq) {
            minDistanceSq = distSq
            closestIdx = mediaIdx
        }
    }
    return closestIdx
}

/**
 * Đường viền tiến độ hình vuông bo góc (Square Progress Indicator) bao sát quanh ô thumbnail vuông.
 * Bắt đầu từ chính giữa cạnh trên (12 giờ) và chạy thuận chiều kim đồng hồ quanh 4 cạnh hình vuông.
 */
/**
 * Vòng tròn tiến trình (Process Circle) tự động lấp đầy 100% và vẽ dấu tick animation trước khi ẩn.
 */
@Composable
private fun AnimatedCircularProgressWithTick(
    isCompletingOrCompleted: Boolean,
    isTickVisible: Boolean,
    color: Color,
    trackColor: Color = color.copy(alpha = 0.24f),
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.5.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gallery_circle_spin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    val sweepProgress by animateFloatAsState(
        targetValue = if (isCompletingOrCompleted) 1f else 0.28f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "circle_sweep_100"
    )

    val tickProgress by animateFloatAsState(
        targetValue = if (isTickVisible) 1f else 0f,
        animationSpec = if (isTickVisible) {
            tween(durationMillis = 280, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 80)
        },
        label = "circle_tick_draw"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val diameter = this.size.minDimension - strokePx
        val topLeftOffset = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
        val arcSize = ComposeSize(diameter, diameter)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeftOffset,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        val startAngle = if (isCompletingOrCompleted) -90f else (spinRotation - 90f)
        val sweepAngle = (sweepProgress * 360f).coerceIn(20f, 360f)

        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeftOffset,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        if (tickProgress > 0.01f) {
            val w = this.size.width
            val h = this.size.height
            val fullTickPath = Path().apply {
                moveTo(w * 0.27f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.69f)
                lineTo(w * 0.75f, h * 0.34f)
            }
            val pm = PathMeasure()
            pm.setPath(fullTickPath, false)
            val tickSegment = Path()
            pm.getSegment(0f, pm.length * tickProgress.coerceIn(0f, 1f), tickSegment, true)

            drawPath(
                path = tickSegment,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun SquareProgressBox(
    progress: Float,
    isIndeterminate: Boolean,
    isCompleted: Boolean = false,
    activeColor: Color,
    trackColor: Color,
    boxSize: Dp,
    strokeWidth: Dp = 3.5.dp,
    cornerRadius: Dp = 10.dp,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "square_indeterminate")
    val indeterminatePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "square_phase"
    )

    val tickDrawProgress by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = if (isCompleted) {
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 100)
        },
        label = "square_tick_draw_progress"
    )

    val tickBadgeScale by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0.65f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "square_tick_badge_scale"
    )

    Box(
        modifier = Modifier.size(boxSize),
        contentAlignment = Alignment.Center
    ) {
        content()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val halfStroke = strokePx / 2f
            val r = cornerRadius.toPx().coerceAtMost((size.width - strokePx) / 2f)

            val left = halfStroke
            val top = halfStroke
            val right = size.width - halfStroke
            val bottom = size.height - halfStroke
            val midTopX = (left + right) / 2f

            // Xây dựng đường Path khép kín quanh hình vuông bo góc bắt đầu từ điểm giữa cạnh trên (12h)
            val fullSquarePath = Path().apply {
                moveTo(midTopX, top)
                lineTo(right - r, top)
                arcTo(
                    rect = Rect(right - 2 * r, top, right, top + 2 * r),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(right, bottom - r)
                arcTo(
                    rect = Rect(right - 2 * r, bottom - 2 * r, right, bottom),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(left + r, bottom)
                arcTo(
                    rect = Rect(left, bottom - 2 * r, left + 2 * r, bottom),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(left, top + r)
                arcTo(
                    rect = Rect(left, top, left + 2 * r, top + 2 * r),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(midTopX, top)
            }

            // Vẽ đường nền (track) bao quanh hình vuông
            drawPath(
                path = fullSquarePath,
                color = trackColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            val pathMeasure = PathMeasure()
            pathMeasure.setPath(fullSquarePath, false)
            val totalLen = pathMeasure.length

            if (totalLen > 0f) {
                val segmentPath = Path()
                if (isIndeterminate && !isCompleted) {
                    val segLen = totalLen * 0.28f
                    val startDist = indeterminatePhase * totalLen
                    val endDist = startDist + segLen
                    if (endDist <= totalLen) {
                        pathMeasure.getSegment(startDist, endDist, segmentPath, true)
                    } else {
                        pathMeasure.getSegment(startDist, totalLen, segmentPath, true)
                        pathMeasure.getSegment(0f, endDist - totalLen, segmentPath, true)
                    }
                } else {
                    val effectiveProg = if (isCompleted) 1f else progress.coerceIn(0f, 1f)
                    val stopDist = effectiveProg * totalLen
                    if (stopDist > 0.5f) {
                        pathMeasure.getSegment(0f, stopDist, segmentPath, true)
                    }
                }

                drawPath(
                    path = segmentPath,
                    color = activeColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Khi tiến trình hoàn tất 100%, vẽ huy hiệu tròn + dấu tick animation ở chính giữa
            if (tickDrawProgress > 0.01f) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val badgeRadius = (minOf(size.width, size.height) * 0.26f) * tickBadgeScale

                drawCircle(
                    color = Color.Black.copy(alpha = 0.68f * tickDrawProgress),
                    radius = badgeRadius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = activeColor.copy(alpha = tickDrawProgress),
                    radius = badgeRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val tickFullPath = Path().apply {
                    moveTo(cx - badgeRadius * 0.44f, cy + badgeRadius * 0.04f)
                    lineTo(cx - badgeRadius * 0.10f, cy + badgeRadius * 0.38f)
                    lineTo(cx + badgeRadius * 0.48f, cy - badgeRadius * 0.30f)
                }
                val tickMeasure = PathMeasure()
                tickMeasure.setPath(tickFullPath, false)
                val tickSeg = Path()
                tickMeasure.getSegment(0f, tickMeasure.length * tickDrawProgress.coerceIn(0f, 1f), tickSeg, true)

                drawPath(
                    path = tickSeg,
                    color = activeColor,
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private data class EncryptingThumbState(
    val bitmap: Bitmap?,
    val isVideo: Boolean,
    val isLivePhoto: Boolean
)

internal const val FLOATING_BAR_SPRING_DAMPING = 0.7f
internal const val FLOATING_BAR_SPRING_STIFFNESS = 300f

internal fun <T> floatingBarSpring(): androidx.compose.animation.core.SpringSpec<T> =
    spring(dampingRatio = 0.7f, stiffness = 300f)

enum class FloatingBarState {
    COLLAPSED,
    CONFIRM_DOWNLOAD,
    DOWNLOADING,
    DOWNLOAD_COMPLETED
}

/**
 * Lưu trạng thái chuyển tiếp liền mạch giữa Floating Bar của GalleryScreen và MediaViewerScreen
 * để khi đóng/mở ảnh không bao giờ bị nháy ẩn mất 1 frame, đồng thời width và các icon
 * co dãn / chuyển đổi liên tục bằng spring animation.
 */
internal object FloatingBarSharedState {
    var lastCenterX: Float = 0f
    var lastBottomY: Float = 0f
    var lastWindowX: Float = 0f
    var lastWindowY: Float = 0f
    var viewerMorphProgress: Float = 0f
    var liveSlotProgress: Float = 0f
    var selectionModeProgress: Float = 0f
    var deleteSelectedSlotProgress: Float = 0f
    var lastIsSelectionMode: Boolean = false
    var lastIsFullVaultSelected: Boolean = false
    var lastHasSelectedItems: Boolean = false
    var gridStateRef: LazyGridState? = null
    var gridWindowX: Float = 0f
    var gridWindowY: Float = 0f
}

internal fun DrawScope.drawGalleryGridBackdrop(
    barLeft: Float,
    barTop: Float,
    gridState: LazyGridState?,
    gridWindowX: Float,
    gridWindowY: Float,
    boundsMap: Map<String, FloatArray>,
    excludeEncryptedName: String? = null,
    alphaMultiplier: Float = 1f
) {
    val effAlpha = alphaMultiplier.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return

    val barRight = barLeft + size.width
    val barBottom = barTop + size.height
    val visibleInfoList = gridState?.layoutInfo?.visibleItemsInfo.orEmpty()

    if (visibleInfoList.isNotEmpty()) {
        for (info in visibleInfoList) {
            val keyStr = info.key as? String ?: continue
            if (excludeEncryptedName != null && keyStr == excludeEncryptedName) continue
            val tx = gridWindowX + info.offset.x.toFloat()
            val ty = gridWindowY + info.offset.y.toFloat()
            val tw = info.size.width.toFloat()
            val th = info.size.height.toFloat()
            if (tx + tw >= barLeft - 24f && tx <= barRight + 24f &&
                ty + th >= barTop - 24f && ty <= barBottom + 24f
            ) {
                val dstX = (tx - barLeft).toInt()
                val dstY = (ty - barTop).toInt()
                val dstW = tw.toInt().coerceAtLeast(1)
                val dstH = th.toInt().coerceAtLeast(1)
                if (keyStr.startsWith("header_")) {
                    drawRect(
                        color = Color(0xFFD4AF37).copy(alpha = 0.65f * effAlpha),
                        topLeft = Offset(dstX + 8.dp.toPx(), dstY + (dstH * 0.3f)),
                        size = ComposeSize(minOf(dstW.toFloat() * 0.35f, 120.dp.toPx()), (dstH * 0.45f).coerceAtLeast(8f))
                    )
                } else if (keyStr != "gallery_stats_header") {
                    val bmp = ThumbBitmapCache.getBlurred(keyStr) ?: ThumbBitmapCache.get(keyStr)
                    if (bmp != null) {
                        val bw = bmp.width
                        val bh = bmp.height
                        val edge = minOf(bw, bh).coerceAtLeast(1)
                        val srcX = (bw - edge) / 2
                        val srcY = (bh - edge) / 2
                        drawImage(
                            image = bmp,
                            srcOffset = IntOffset(srcX, srcY),
                            srcSize = IntSize(edge, edge),
                            dstOffset = IntOffset(dstX, dstY),
                            dstSize = IntSize(dstW, dstH),
                            alpha = effAlpha,
                            filterQuality = FilterQuality.Medium
                        )
                    } else {
                        drawRect(
                            color = Color(0xFF2D3033).copy(alpha = effAlpha),
                            topLeft = Offset(dstX.toFloat(), dstY.toFloat()),
                            size = ComposeSize(dstW.toFloat(), dstH.toFloat())
                        )
                    }
                }
            }
        }
    } else {
        for ((encName, b) in boundsMap) {
            if (excludeEncryptedName != null && encName == excludeEncryptedName) continue
            if (b.size < 4) continue
            val tx = b[0]
            val ty = b[1]
            val tw = b[2]
            val th = b[3]
            if (tx + tw >= barLeft - 24f && tx <= barRight + 24f &&
                ty + th >= barTop - 24f && ty <= barBottom + 24f
            ) {
                val bmp = ThumbBitmapCache.getBlurred(encName) ?: ThumbBitmapCache.get(encName) ?: continue
                val bw = bmp.width
                val bh = bmp.height
                val edge = minOf(bw, bh).coerceAtLeast(1)
                drawImage(
                    image = bmp,
                    srcOffset = IntOffset((bw - edge) / 2, (bh - edge) / 2),
                    srcSize = IntSize(edge, edge),
                    dstOffset = IntOffset((tx - barLeft).toInt(), (ty - barTop).toInt()),
                    dstSize = IntSize(tw.toInt().coerceAtLeast(1), th.toInt().coerceAtLeast(1)),
                    alpha = effAlpha,
                    filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}

/**
 * Header thống kê số lượng secure files, photos và videos nằm độc lập ở phía trên cùng của khu vực thumbnail.
 * Đảm bảo khoảng cách an toàn phía trên để không bị camera đục lỗ (punch-hole / display cutout) che khuất.
 */
@Composable
fun GalleryStatsHeader(
    totalFiles: Int,
    photoCount: Int,
    videoCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
            .padding(start = 8.dp, top = 28.dp, end = 8.dp, bottom = 10.dp)
            .testTag("gallery_stats_header")
    ) {
        Text(
            text = "$totalFiles secure files",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("gallery_stats_total_text")
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "$photoCount photos · $videoCount videos",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("gallery_stats_breakdown_text")
        )
    }
}

/**
 * Floating Bar dùng chung cho cả GalleryScreen và MediaViewerScreen:
 * - Chiều rộng (width) của bar ở trạng thái thu gọn co dãn linh hoạt theo đúng số lượng icon hiện có trong bar
 *   bằng spring(dampingRatio = 0.7f, stiffness = 300f).
 * - Hiệu ứng chuyển đổi các icon khi chuyển qua lại giữa Grid và Full Screen (hoặc khi bật/tắt chế độ chọn,
 *   vuốt qua ảnh Live Photo) diễn ra mượt mà bằng spring animation, không bao giờ bị biến mất 1 nhoáng.
 * - Hiệu ứng background blur thực sự (Modifier.blur + vẽ trực tiếp nội dung phía sau bar theo toạ độ cửa sổ thực tế)
 *   kết hợp nền xám trong suốt (alpha = 0.36f), không sử dụng viền (border).
 * - Khi nhấn Download: mở rộng mượt mà bằng spring(dampingRatio = 0.7f, stiffness = 300f) thành panel tỷ lệ 16:9
 *   với hàng tiêu đề (icon Download + "Confirm download") và 2 nút hành động (Cancel / OK -> Close / Show in folder).
 */
@Composable
fun GalleryFloatingBar(
    barState: FloatingBarState,
    onBarStateChange: (FloatingBarState) -> Unit,
    onConfirmDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onShowInFolder: () -> Unit,
    onLiveClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    isAddMenuExpanded: Boolean = false,
    isViewerMode: Boolean = false,
    isClosingToGrid: Boolean = false,
    isBarActive: Boolean = true,
    showLiveButton: Boolean = false,
    isSelectionMode: Boolean = false,
    isFullVaultSelected: Boolean = false,
    hasSelectedItems: Boolean = false,
    downloadProgress: Float = 0f,
    onRefreshClick: () -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onToggleSelectionMode: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    drawBackdrop: (DrawScope.(barLeftInWindow: Float, barTopInWindow: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val barYellow = MaterialTheme.colorScheme.primary

    // Duy nhất 1 Animatable điều khiển kích thước đóng/mở của Floating Bar (từ pill thu gọn <-> panel 16:9)
    val expansionProgress = remember { Animatable(0f) }

    // Các Animatable điều khiển co dãn chiều rộng & chuyển đổi icon bằng spring animation liền mạch giữa Grid và Full Screen
    val viewerMorphAnim = remember {
        Animatable(FloatingBarSharedState.viewerMorphProgress)
    }
    val liveSlotAnim = remember {
        Animatable(FloatingBarSharedState.liveSlotProgress)
    }
    val selectionModeAnim = remember {
        Animatable(FloatingBarSharedState.selectionModeProgress)
    }
    val deleteSelectedSlotAnim = remember {
        Animatable(FloatingBarSharedState.deleteSelectedSlotProgress)
    }

    val targetViewerMorph = if (isViewerMode && !isClosingToGrid) 1f else 0f
    val targetLiveSlot = if (isViewerMode && !isClosingToGrid && showLiveButton) 1f else 0f
    val effectiveSelectionMode = if (isViewerMode) FloatingBarSharedState.lastIsSelectionMode else isSelectionMode
    val effectiveFullVaultSelected = if (isViewerMode) FloatingBarSharedState.lastIsFullVaultSelected else isFullVaultSelected
    val effectiveHasSelectedItems = if (isViewerMode) FloatingBarSharedState.lastHasSelectedItems else hasSelectedItems
    val targetSelectionMode = if (!isViewerMode && isSelectionMode) 1f else if (isViewerMode && isClosingToGrid && effectiveSelectionMode) 1f else 0f
    val targetDeleteSelected = if (!isViewerMode && isSelectionMode && hasSelectedItems) 1f else if (isViewerMode && isClosingToGrid && effectiveSelectionMode && effectiveHasSelectedItems) 1f else 0f

    LaunchedEffect(isBarActive, targetViewerMorph) {
        if (!isBarActive) return@LaunchedEffect
        val sharedVal = FloatingBarSharedState.viewerMorphProgress
        if (abs(viewerMorphAnim.value - sharedVal) > 0.01f) {
            viewerMorphAnim.snapTo(sharedVal)
        }
        viewerMorphAnim.animateTo(
            targetValue = targetViewerMorph,
            animationSpec = floatingBarSpring()
        )
    }

    LaunchedEffect(isBarActive, targetLiveSlot) {
        if (!isBarActive) return@LaunchedEffect
        val sharedVal = FloatingBarSharedState.liveSlotProgress
        if (abs(liveSlotAnim.value - sharedVal) > 0.01f) {
            liveSlotAnim.snapTo(sharedVal)
        }
        liveSlotAnim.animateTo(
            targetValue = targetLiveSlot,
            animationSpec = floatingBarSpring()
        )
    }

    LaunchedEffect(isBarActive, targetSelectionMode) {
        if (!isBarActive) return@LaunchedEffect
        val sharedVal = FloatingBarSharedState.selectionModeProgress
        if (abs(selectionModeAnim.value - sharedVal) > 0.01f) {
            selectionModeAnim.snapTo(sharedVal)
        }
        selectionModeAnim.animateTo(
            targetValue = targetSelectionMode,
            animationSpec = floatingBarSpring()
        )
    }

    LaunchedEffect(isBarActive, targetDeleteSelected) {
        if (!isBarActive) return@LaunchedEffect
        val sharedVal = FloatingBarSharedState.deleteSelectedSlotProgress
        if (abs(deleteSelectedSlotAnim.value - sharedVal) > 0.01f) {
            deleteSelectedSlotAnim.snapTo(sharedVal)
        }
        deleteSelectedSlotAnim.animateTo(
            targetValue = targetDeleteSelected,
            animationSpec = floatingBarSpring()
        )
    }

    SideEffect {
        if (isBarActive) {
            FloatingBarSharedState.viewerMorphProgress = viewerMorphAnim.value
            FloatingBarSharedState.liveSlotProgress = liveSlotAnim.value
            FloatingBarSharedState.selectionModeProgress = selectionModeAnim.value
            FloatingBarSharedState.deleteSelectedSlotProgress = deleteSelectedSlotAnim.value
            if (!isViewerMode) {
                FloatingBarSharedState.lastIsSelectionMode = isSelectionMode
                FloatingBarSharedState.lastIsFullVaultSelected = isFullVaultSelected
                FloatingBarSharedState.lastHasSelectedItems = hasSelectedItems
            }
        }
    }

    // Giữ chế độ hiển thị của panel mở rộng trong suốt quá trình thu gọn để không bị nhảy layout khi đang đóng
    var activeExpandedMode by remember {
        mutableStateOf(
            if (barState == FloatingBarState.COLLAPSED) FloatingBarState.CONFIRM_DOWNLOAD else barState
        )
    }
    if (barState != FloatingBarState.COLLAPSED) {
        activeExpandedMode = barState
    }

    LaunchedEffect(barState) {
        val target = if (barState == FloatingBarState.COLLAPSED) 0f else 1f
        if (target == 1f && expansionProgress.value <= 0.001f) {
            expansionProgress.snapTo(0f)
        }
        expansionProgress.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
        )
        if (target == 0f) {
            expansionProgress.snapTo(0f)
            activeExpandedMode = FloatingBarState.CONFIRM_DOWNLOAD
        }
    }

    val completedTransition by animateFloatAsState(
        targetValue = if (activeExpandedMode == FloatingBarState.DOWNLOAD_COMPLETED) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "floating_bar_completed_transition"
    )

    val addIconRotation by animateFloatAsState(
        targetValue = if (isAddMenuExpanded) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "floating_bar_add_rotation"
    )

    var containerCenterX by remember { mutableFloatStateOf(FloatingBarSharedState.lastCenterX) }
    var containerBottomY by remember { mutableFloatStateOf(FloatingBarSharedState.lastBottomY) }
    var barWindowX by remember { mutableFloatStateOf(FloatingBarSharedState.lastWindowX) }
    var barWindowY by remember { mutableFloatStateOf(FloatingBarSharedState.lastWindowY) }

    val screenWidthPx = context.resources.displayMetrics.widthPixels.toFloat()
    val screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInWindow()
                val sz = coordinates.size
                if (sz.width > 0 && sz.height > 0) {
                    val cx = pos.x + sz.width / 2f
                    val by = pos.y + sz.height.toFloat()
                    containerCenterX = cx
                    containerBottomY = by
                    if (isBarActive) {
                        FloatingBarSharedState.lastCenterX = cx
                        FloatingBarSharedState.lastBottomY = by
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        val fullWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val collapsedHeightPx = with(density) { 48.dp.toPx() }
        // Tỷ lệ khung hình chính xác 16:9 khi mở rộng (tương tự tỷ lệ mini map trong Details Sheet)
        val expandedHeightPx = fullWidthPx * (9f / 16f)

        val viewerMorph = viewerMorphAnim.value
        val liveProg = liveSlotAnim.value
        val selProg = selectionModeAnim.value
        val delSelProg = deleteSelectedSlotAnim.value

        // Tính chiều rộng thu gọn (collapsed width) co dãn linh hoạt theo đúng số lượng icon hiện có bằng spring:
        // - Mỗi icon tiêu chuẩn rộng 48.dp, khoảng cách giữa các icon là 4.dp
        // - 3 icon tiêu chuẩn: 48*3 + 4*2 = 152.dp
        // - 4 icon tiêu chuẩn (có thêm icon Live hoặc Delete): 152.dp + 52.dp = 204.dp
        // - Chế độ chọn (DoneAll + ALL/NONE rộng 84.dp = 48.dp + 36.dp): 152.dp + 36.dp (+ 52.dp khi có mục được chọn)
        val gridCollapsedWidthDp = 152.dp + (36.dp * selProg) + (52.dp * delSelProg)
        val viewerCollapsedWidthDp = 152.dp + (52.dp * liveProg)
        val collapsedWidthDp = (gridCollapsedWidthDp + (viewerCollapsedWidthDp - gridCollapsedWidthDp) * viewerMorph)
            .coerceAtLeast(132.dp)
        val collapsedWidthPx = with(density) { collapsedWidthDp.toPx() }.coerceAtMost(fullWidthPx)

        val t = expansionProgress.value
        val currentWidthPx = (collapsedWidthPx + (fullWidthPx - collapsedWidthPx) * t)
            .coerceIn(collapsedWidthPx * 0.85f, fullWidthPx * 1.04f)
        val currentHeightPx = (collapsedHeightPx + (expandedHeightPx - collapsedHeightPx) * t)
            .coerceAtLeast(collapsedHeightPx * 0.85f)
        val currentWidthDp = with(density) { currentWidthPx.toDp() }
        val currentHeightDp = with(density) { currentHeightPx.toDp() }

        val clampedT = t.coerceIn(0f, 1f)
        val currentCornerDp = (24f - 6f * clampedT).dp
        val barShape = RoundedCornerShape(currentCornerDp)

        val collapsedContentAlpha = (1f - clampedT * 2.5f).coerceIn(0f, 1f)
        val expandedContentAlpha = ((clampedT - 0.18f) / 0.82f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .width(currentWidthDp)
                .height(currentHeightDp)
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInWindow()
                    barWindowX = pos.x
                    barWindowY = pos.y
                    if (isBarActive) {
                        FloatingBarSharedState.lastWindowX = pos.x
                        FloatingBarSharedState.lastWindowY = pos.y
                    }
                }
                .clip(barShape)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* Chặn sự kiện chạm xuyên qua floating bar xuống lớp bên dưới */ }
                .testTag(if (isViewerMode) "viewer_floating_bar" else "gallery_floating_bar")
        ) {
            // Lớp 1: Hiệu ứng Background Blur thực sự (làm mờ nội dung thực phía sau bar với nền đen đặc chặn hoàn toàn ảnh sắc nét bên dưới lộ lên)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 24.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color.Black)
                    val effCenterX = when {
                        containerCenterX > 0f -> containerCenterX
                        FloatingBarSharedState.lastCenterX > 0f -> FloatingBarSharedState.lastCenterX
                        else -> screenWidthPx / 2f
                    }
                    val effBottomY = when {
                        containerBottomY > 0f -> containerBottomY
                        FloatingBarSharedState.lastBottomY > 0f -> FloatingBarSharedState.lastBottomY
                        else -> screenHeightPx - 24.dp.toPx()
                    }
                    val exactBarLeft = effCenterX - size.width / 2f
                    val exactBarTop = effBottomY - size.height
                    drawBackdrop?.invoke(this, exactBarLeft, exactBarTop)
                }
            }

            // Lớp phủ xám trong suốt (tăng nhẹ alpha lên 0.36f) tạo bề mặt kính mờ rõ nét, tinh tế, không viền
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF38383C).copy(alpha = 0.36f))
            )

            // Lớp 2: Nội dung trạng thái thu gọn (COLLAPSED) với chuyển tiếp icon bằng spring giữa Grid và Full Screen
            if (collapsedContentAlpha > 0.001f) {
                val gridLayerAlpha = ((1f - viewerMorph * 1.15f).coerceIn(0f, 1f) * collapsedContentAlpha).coerceIn(0f, 1f)
                val gridLayerScale = (0.58f + 0.42f * (1f - viewerMorph)).coerceIn(0.45f, 1.12f)

                val viewerLayerAlpha = (((viewerMorph - 0.10f) / 0.90f).coerceIn(0f, 1f) * collapsedContentAlpha).coerceIn(0f, 1f)
                val viewerLayerScale = (0.58f + 0.42f * viewerMorph).coerceIn(0.45f, 1.12f)

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Cụm icon của Grid (!isViewerMode hoặc đang trong quá trình morph đóng/mở)
                    if (!isViewerMode || gridLayerAlpha > 0.004f) {
                        val leftSlotWidthDp = 48.dp + (36.dp * selProg.coerceAtLeast(0f))
                        val refreshAlpha = ((1f - selProg * 1.2f).coerceIn(0f, 1f) * gridLayerAlpha).coerceIn(0f, 1f)
                        val refreshScale = ((0.6f + 0.4f * (1f - selProg)).coerceIn(0.45f, 1.12f) * gridLayerScale)
                        val selectAllAlpha = (((selProg - 0.1f) / 0.9f).coerceIn(0f, 1f) * gridLayerAlpha).coerceIn(0f, 1f)
                        val selectAllScale = ((0.6f + 0.4f * selProg).coerceIn(0.45f, 1.12f) * gridLayerScale)

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Vị trí 1 (Cạnh trái): Refresh <-> Select All (ALL / NONE)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(leftSlotWidthDp)
                                    .height(48.dp)
                            ) {
                                if (!effectiveSelectionMode || refreshAlpha > 0.004f) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .requiredSize(48.dp)
                                            .graphicsLayer {
                                                alpha = refreshAlpha
                                                scaleX = refreshScale
                                                scaleY = refreshScale
                                            }
                                            .then(
                                                if (!isViewerMode && !effectiveSelectionMode) {
                                                    Modifier
                                                        .clickable { onRefreshClick() }
                                                        .testTag("refresh_button")
                                                } else Modifier
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Refresh Grid",
                                            tint = barYellow,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                if (effectiveSelectionMode || selectAllAlpha > 0.004f) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .width(84.dp)
                                            .height(48.dp)
                                            .graphicsLayer {
                                                alpha = selectAllAlpha
                                                scaleX = selectAllScale
                                                scaleY = selectAllScale
                                            }
                                            .then(
                                                if (!isViewerMode && effectiveSelectionMode) {
                                                    Modifier
                                                        .clickable { onToggleSelectAll() }
                                                        .testTag("select_full_vault_button")
                                                } else Modifier
                                            )
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DoneAll,
                                            contentDescription = "Select Full Vault",
                                            tint = barYellow,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (effectiveFullVaultSelected) "NONE" else "ALL",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = barYellow,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Vị trí 2 (Giữa): Nút dấu cộng (+) Import Media
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        alpha = gridLayerAlpha
                                        scaleX = gridLayerScale
                                        scaleY = gridLayerScale
                                    }
                                    .then(
                                        if (!isViewerMode) {
                                            Modifier
                                                .clickable { onAddClick() }
                                                .testTag("import_fab")
                                        } else Modifier
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Import Media Menu",
                                    tint = barYellow,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .graphicsLayer {
                                            rotationZ = addIconRotation
                                        }
                                )
                            }

                            // Vị trí 3 (Tuỳ chọn khi có mục được chọn): Nút xoá các mục đã chọn
                            val clampedDelSelWidth = delSelProg.coerceAtLeast(0f)
                            if ((effectiveSelectionMode && effectiveHasSelectedItems) || clampedDelSelWidth > 0.004f) {
                                Spacer(modifier = Modifier.width(4.dp * clampedDelSelWidth))
                                val delAlpha = (delSelProg.coerceIn(0f, 1f) * gridLayerAlpha).coerceIn(0f, 1f)
                                val delScale = ((0.55f + 0.45f * delSelProg).coerceIn(0.4f, 1.12f) * gridLayerScale)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width((48.dp * clampedDelSelWidth).coerceAtLeast(if (!isViewerMode && effectiveSelectionMode && effectiveHasSelectedItems) 1.dp else 0.dp))
                                        .height(48.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .requiredSize(48.dp)
                                            .graphicsLayer {
                                                alpha = delAlpha
                                                scaleX = delScale
                                                scaleY = delScale
                                            }
                                            .then(
                                                if (!isViewerMode && effectiveSelectionMode && effectiveHasSelectedItems) {
                                                    Modifier
                                                        .clickable { onDeleteClick() }
                                                        .testTag("delete_selected_button")
                                                } else Modifier
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete Selected",
                                            tint = barYellow,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Vị trí cuối (Cạnh phải): Nút Edit <-> Close Selection
                            val editAlpha = ((1f - selProg * 1.2f).coerceIn(0f, 1f) * gridLayerAlpha).coerceIn(0f, 1f)
                            val editScale = ((0.6f + 0.4f * (1f - selProg)).coerceIn(0.45f, 1.12f) * gridLayerScale)
                            val closeSelAlpha = (((selProg - 0.1f) / 0.9f).coerceIn(0f, 1f) * gridLayerAlpha).coerceIn(0f, 1f)
                            val closeSelScale = ((0.6f + 0.4f * selProg).coerceIn(0.45f, 1.12f) * gridLayerScale)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(
                                        if (!isViewerMode) {
                                            Modifier
                                                .clickable { onToggleSelectionMode() }
                                                .testTag("choose_edit_button")
                                        } else Modifier
                                    )
                            ) {
                                if (!effectiveSelectionMode || editAlpha > 0.004f) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Choose Items",
                                        tint = barYellow,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .graphicsLayer {
                                                alpha = editAlpha
                                                scaleX = editScale
                                                scaleY = editScale
                                            }
                                    )
                                }
                                if (effectiveSelectionMode || closeSelAlpha > 0.004f) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Cancel Selection",
                                        tint = barYellow,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .graphicsLayer {
                                                alpha = closeSelAlpha
                                                scaleX = closeSelScale
                                                scaleY = closeSelScale
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Cụm icon của Full Screen Viewer (isViewerMode hoặc đang trong quá trình morph đóng/mở)
                    if (isViewerMode || viewerLayerAlpha > 0.004f) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Vị trí 0 (Tuỳ chọn khi xem ảnh Live Photo): Nút Live co dãn bằng spring
                            val clampedLiveWidth = liveProg.coerceAtLeast(0f)
                            if (showLiveButton || clampedLiveWidth > 0.004f) {
                                val liveIconAlpha = (liveProg.coerceIn(0f, 1f) * viewerLayerAlpha).coerceIn(0f, 1f)
                                val liveIconScale = ((0.55f + 0.45f * liveProg).coerceIn(0.4f, 1.12f) * viewerLayerScale)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width((48.dp * clampedLiveWidth).coerceAtLeast(if (isViewerMode && showLiveButton) 1.dp else 0.dp))
                                        .height(48.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .requiredSize(48.dp)
                                            .graphicsLayer {
                                                alpha = liveIconAlpha
                                                scaleX = liveIconScale
                                                scaleY = liveIconScale
                                            }
                                            .then(
                                                if (isViewerMode && showLiveButton) {
                                                    Modifier
                                                        .clickable { onLiveClick() }
                                                        .testTag("live_photo_badge_button")
                                                } else Modifier
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MotionPhotosOn,
                                            contentDescription = "Play Live Once",
                                            tint = barYellow,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp * clampedLiveWidth))
                            }

                            // Vị trí 1: Nút Download
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        alpha = viewerLayerAlpha
                                        scaleX = viewerLayerScale
                                        scaleY = viewerLayerScale
                                    }
                                    .then(
                                        if (isViewerMode) {
                                            Modifier
                                                .clickable {
                                                    onBarStateChange(FloatingBarState.CONFIRM_DOWNLOAD)
                                                }
                                                .testTag("export_selected_button")
                                        } else Modifier
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FileDownload,
                                    contentDescription = "Download",
                                    tint = barYellow,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Vị trí 2: Nút Info (Details)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        alpha = viewerLayerAlpha
                                        scaleX = viewerLayerScale
                                        scaleY = viewerLayerScale
                                    }
                                    .then(
                                        if (isViewerMode) {
                                            Modifier
                                                .clickable { onInfoClick() }
                                                .testTag("viewer_info_button")
                                        } else Modifier
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "File Properties",
                                    tint = barYellow,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Vị trí 3: Nút Delete
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        alpha = viewerLayerAlpha
                                        scaleX = viewerLayerScale
                                        scaleY = viewerLayerScale
                                    }
                                    .then(
                                        if (isViewerMode) {
                                            Modifier
                                                .clickable { onDeleteClick() }
                                                .testTag("viewer_delete_button")
                                        } else Modifier
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete File",
                                    tint = barYellow,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Lớp 3: Nội dung Panel xác nhận tải xuống 16:9 (CONFIRM_DOWNLOAD / DOWNLOADING / DOWNLOAD_COMPLETED)
            if (expandedContentAlpha > 0.001f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = expandedContentAlpha
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .testTag("download_confirmation_panel"),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Hàng tiêu đề ở phía trên: gồm icon Download nằm bên trái và text "Confirm download" ngay bên cạnh cùng hàng
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = "Confirm download",
                            tint = barYellow,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Confirm download",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = barYellow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("confirm_download_title")
                        )
                    }

                    if (activeExpandedMode == FloatingBarState.DOWNLOADING) {
                        val pct = (downloadProgress.coerceIn(0f, 1f) * 100).toInt()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                color = barYellow,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Downloading... $pct%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = barYellow
                            )
                        }
                    }

                    // 2. Hai nút hành động ở phía dưới:
                    //    - Trước khi hoàn tất: Cancel (nền trắng có alpha) và OK (nền vàng solid, chữ/icon tương phản rõ)
                    //    - Khi tải thành công: Close (nền trắng có alpha) và Show in folder (nền vàng solid)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val confirmButtonsAlpha = (1f - completedTransition * 2f).coerceIn(0f, 1f)
                        val completedButtonsAlpha = ((completedTransition - 0.3f) / 0.7f).coerceIn(0f, 1f)

                        if (confirmButtonsAlpha > 0.001f) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = confirmButtonsAlpha
                                        val s = 0.92f + 0.08f * confirmButtonsAlpha
                                        scaleX = s
                                        scaleY = s
                                    },
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cancel: nền trắng có alpha, không dùng nền trắng đục
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .clickable {
                                            onCancelDownload()
                                        }
                                        .testTag("download_cancel_button")
                                ) {
                                    Text(
                                        text = "Cancel",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                // OK: nền vàng solid, text và icon có độ tương phản rõ ràng
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(barYellow)
                                        .clickable(enabled = activeExpandedMode != FloatingBarState.DOWNLOADING) {
                                            onConfirmDownload()
                                        }
                                        .testTag("download_ok_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "OK",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }

                        if (completedButtonsAlpha > 0.001f) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = completedButtonsAlpha
                                        val s = 0.92f + 0.08f * completedButtonsAlpha
                                        scaleX = s
                                        scaleY = s
                                    },
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Close: đóng trạng thái hoàn tất và đưa giao diện trở về floating bar thu gọn
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .clickable {
                                            onBarStateChange(FloatingBarState.COLLAPSED)
                                        }
                                        .testTag("download_close_button")
                                ) {
                                    Text(
                                        text = "Close",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                // Show in folder: mở đúng vị trí lưu file vừa tải xuống
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(barYellow)
                                        .clickable {
                                            onShowInFolder()
                                        }
                                        .testTag("download_show_in_folder_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FolderOpen,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Show in folder",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val items by viewModel.items.collectAsState()
    val dek by viewModel.dek.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val isProcessCompleted by viewModel.isProcessCompleted.collectAsState()
    val importQueueTotal by viewModel.importQueueTotal.collectAsState()
    val importQueueIndex by viewModel.importQueueIndex.collectAsState()
    val currentImportThumb by viewModel.currentImportThumb.collectAsState()
    val currentImportIsVideo by viewModel.currentImportIsVideo.collectAsState()
    val currentImportIsLivePhoto by viewModel.currentImportIsLivePhoto.collectAsState()
    val isCancellingImport by viewModel.isCancellingImport.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val videoFramePickRequest by viewModel.videoFramePickRequest.collectAsState()
    val error by viewModel.error.collectAsState()
    val animatingItem by viewModel.animatingItem.collectAsState()
    val selectedMedia by viewModel.selectedMedia.collectAsState()
    val boundsMap by viewModel.thumbnailBounds.collectAsState()
    var gridWindowX by remember { mutableFloatStateOf(0f) }
    var gridWindowY by remember { mutableFloatStateOf(0f) }

    // Làm mượt phần trăm tiến độ mã hoá (chạy mượt cả đường viền vuông lẫn con số %)
    val smoothImportProgress by animateFloatAsState(
        targetValue = importProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "smooth_import_progress"
    )

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedEncryptedNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var isImportMenuExpanded by remember { mutableStateOf(false) }
    var floatingBarState by remember { mutableStateOf(FloatingBarState.COLLAPSED) }

    // Trạng thái phát Live Photo 1 lần duy nhất khi bấm icon Live trên floating bar
    var oneShotLiveItemKey by remember { mutableStateOf<String?>(null) }
    var oneShotLiveTriggerId by remember { mutableIntStateOf(0) }

    val videoCount = remember(items) { items.count { it.isVideo } }
    val photoCount = remember(items, videoCount) { (items.size - videoCount).coerceAtLeast(0) }

    val orderedKeys = remember(items) { items.map { it.encryptedName } }
    val keyToIndexMap = remember(orderedKeys) {
        orderedKeys.withIndex().associate { (idx, key) -> key to idx }
    }

    val isFullVaultSelected = remember(items, selectedEncryptedNames) {
        items.isNotEmpty() && selectedEncryptedNames.size == items.size
    }

    val gridState = rememberLazyGridState()
    var autoScrollSpeedPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(autoScrollSpeedPx) {
        if (autoScrollSpeedPx != 0f) {
            while (autoScrollSpeedPx != 0f) {
                gridState.scrollBy(autoScrollSpeedPx)
                delay(16L)
            }
        }
    }

    LaunchedEffect(items) {
        val existingNames = items.map { it.encryptedName }.toSet()
        selectedEncryptedNames = selectedEncryptedNames.intersect(existingNames)
        if (items.isEmpty()) {
            isSelectionMode = false
        }
    }

    BackHandler {
        when {
            floatingBarState != FloatingBarState.COLLAPSED -> {
                if (floatingBarState == FloatingBarState.DOWNLOADING) {
                    viewModel.cancelImport()
                }
                floatingBarState = FloatingBarState.COLLAPSED
            }
            isImportMenuExpanded -> {
                isImportMenuExpanded = false
            }
            isSelectionMode -> {
                isSelectionMode = false
                selectedEncryptedNames = emptySet()
                autoScrollSpeedPx = 0f
            }
            else -> {
                viewModel.lockVault()
            }
        }
    }

    val customImageLoader = remember(dek) {
        dek?.let { key ->
            ImageLoader.Builder(context)
                .components {
                    add(EncryptedUriFetcher.Factory(key))
                }
                .build()
        }
    }

    val mediaPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        try {
            val wanted = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                wanted += android.Manifest.permission.READ_MEDIA_IMAGES
                wanted += android.Manifest.permission.READ_MEDIA_VIDEO
            } else {
                wanted += android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wanted += android.Manifest.permission.ACCESS_MEDIA_LOCATION
            }
            val missing = wanted.filter {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                mediaPermLauncher.launch(missing.toTypedArray())
            }
        } catch (_: Exception) {}
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            (context as? Activity)?.overridePendingTransition(0, 0)
        }
        if (uris.isNotEmpty()) {
            viewModel.importMultipleMedia(context, uris)
        }
    }

    val folderImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            (context as? Activity)?.overridePendingTransition(0, 0)
        }
        if (treeUri != null) {
            viewModel.importFolderMedia(context, treeUri)
        }
    }

    Scaffold(
        containerColor = Color(0xFF000000),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.background(Color(0xFF000000))
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF000000))
        ) {
            AnimatedContent(
                targetState = items.isEmpty() && !isLoading,
                transitionSpec = {
                    fadeIn(tween(240)).togetherWith(fadeOut(tween(180)))
                },
                label = "gallery_empty_or_grid"
            ) { isEmptyState ->
                if (isEmptyState) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        GalleryStatsHeader(
                            totalFiles = items.size,
                            photoCount = photoCount,
                            videoCount = videoCount
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = "Empty Vault",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your Secure Vault is Empty",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap the '+' button below to import photos or videos from your device. All files will be encrypted block-by-block with zero local tracking database.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 300.dp)
                            )
                        }
                    }
                } else {
                    val groupedItems = remember(items) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        items.groupBy { item ->
                            if (item.dateTaken == 0L) "Unknown" else sdf.format(java.util.Date(item.dateTaken))
                        }
                    }

                    val currentOrderedKeys by rememberUpdatedState(orderedKeys)
                    val currentKeyToIndex by rememberUpdatedState(keyToIndexMap)
                    val currentSelectedSet by rememberUpdatedState(selectedEncryptedNames)

                    val edgeScrollThresholdPx = with(density) { 64.dp.toPx() }
                    val maxAutoScrollStepPx = with(density) { 18.dp.toPx() }
                    val touchSlopPx = with(density) { 10.dp.toPx() }

                    val dragSelectModifier = Modifier.pointerInput(isSelectionMode) {
                        if (!isSelectionMode) return@pointerInput

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPos = down.position

                            var startIndex: Int? = null
                            var startedOnCheckboxCorner = false
                            for (info in gridState.layoutInfo.visibleItemsInfo) {
                                val keyStr = info.key as? String ?: continue
                                val idx = currentKeyToIndex[keyStr] ?: continue
                                val left = info.offset.x.toFloat()
                                val top = info.offset.y.toFloat()
                                val right = left + info.size.width
                                val bottom = top + info.size.height
                                if (startPos.x in left..right && startPos.y in top..bottom) {
                                    startIndex = idx
                                    if (startPos.x >= left + info.size.width * 0.5f &&
                                        startPos.y >= top + info.size.height * 0.5f
                                    ) {
                                        startedOnCheckboxCorner = true
                                    }
                                    break
                                }
                            }

                            if (startIndex == null) return@awaitEachGesture

                            var isDragSelecting = false
                            var baselineSelection: Set<String> = emptySet()
                            var targetSelectState = true

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val pos = change.position
                                val totalDx = pos.x - startPos.x
                                val totalDy = pos.y - startPos.y

                                if (!isDragSelecting) {
                                    val movedEnough = (pos - startPos).getDistance() > touchSlopPx
                                    if (movedEnough && (startedOnCheckboxCorner || abs(totalDx) > abs(totalDy) * 1.15f)) {
                                        isDragSelecting = true
                                        baselineSelection = currentSelectedSet
                                        val startKey = currentOrderedKeys.getOrNull(startIndex)
                                        targetSelectState = startKey == null || (startKey !in baselineSelection)
                                    } else if (movedEnough && abs(totalDy) > abs(totalDx) * 1.5f) {
                                        break
                                    }
                                }

                                if (isDragSelecting) {
                                    change.consume()

                                    val viewportH = size.height.toFloat()
                                    autoScrollSpeedPx = when {
                                        pos.y < edgeScrollThresholdPx -> {
                                            val ratio = ((edgeScrollThresholdPx - pos.y) / edgeScrollThresholdPx).coerceIn(0f, 1f)
                                            -maxAutoScrollStepPx * ratio
                                        }
                                        pos.y > viewportH - edgeScrollThresholdPx -> {
                                            val ratio = ((pos.y - (viewportH - edgeScrollThresholdPx)) / edgeScrollThresholdPx).coerceIn(0f, 1f)
                                            maxAutoScrollStepPx * ratio
                                        }
                                        else -> 0f
                                    }

                                    val hoveredIdx = findMediaItemIndexAtPosition(
                                        gridState = gridState,
                                        touchPos = pos,
                                        keyToIndex = currentKeyToIndex
                                    )

                                    if (hoveredIdx != null) {
                                        val minIdx = minOf(startIndex, hoveredIdx)
                                        val maxIdx = maxOf(startIndex, hoveredIdx)
                                        val keysInRange = currentOrderedKeys.subList(
                                            minIdx.coerceAtLeast(0),
                                            (maxIdx + 1).coerceAtMost(currentOrderedKeys.size)
                                        ).toSet()

                                        selectedEncryptedNames = if (targetSelectState) {
                                            baselineSelection + keysInRange
                                        } else {
                                            baselineSelection - keysInRange
                                        }
                                    }
                                }
                            }

                            autoScrollSpeedPx = 0f
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                gridWindowX = pos.x
                                gridWindowY = pos.y
                                FloatingBarSharedState.gridStateRef = gridState
                                FloatingBarSharedState.gridWindowX = pos.x
                                FloatingBarSharedState.gridWindowY = pos.y
                            }
                            .then(dragSelectModifier)
                            .testTag("gallery_grid"),
                        contentPadding = PaddingValues(top = 1.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        // Header thống kê nằm độc lập ở phía trên cùng của khu vực thumbnail
                        item(
                            key = "gallery_stats_header",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            GalleryStatsHeader(
                                totalFiles = items.size,
                                photoCount = photoCount,
                                videoCount = videoCount
                            )
                        }

                        groupedItems.forEach { (dateKey, itemsInGroup) ->
                            item(
                                key = "header_$dateKey",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                val displayHeader = remember(dateKey, itemsInGroup) {
                                    val firstItem = itemsInGroup.firstOrNull()
                                    if (firstItem != null && firstItem.dateTaken != 0L) {
                                        formatGroupDate(firstItem.dateTaken)
                                    } else {
                                        "Unknown Date"
                                    }
                                }

                                val groupKeys = remember(itemsInGroup) {
                                    itemsInGroup.map { it.encryptedName }.toSet()
                                }
                                val isDayAllSelected = remember(groupKeys, selectedEncryptedNames) {
                                    groupKeys.isNotEmpty() && selectedEncryptedNames.containsAll(groupKeys)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .clickable(enabled = isSelectionMode) {
                                            selectedEncryptedNames = if (isDayAllSelected) {
                                                selectedEncryptedNames - groupKeys
                                            } else {
                                                selectedEncryptedNames + groupKeys
                                            }
                                        }
                                        .padding(start = 8.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)
                                        .height(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = displayHeader,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("section_header_$dateKey")
                                    )

                                    AnimatedVisibility(
                                        visible = isSelectionMode,
                                        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.8f, animationSpec = tween(180)),
                                        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.8f, animationSpec = tween(140))
                                    ) {
                                        val dayBgColor by animateColorAsState(
                                            targetValue = if (isDayAllSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            animationSpec = tween(160),
                                            label = "day_check_bg"
                                        )
                                        val dayBorderColor by animateColorAsState(
                                            targetValue = if (isDayAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            animationSpec = tween(160),
                                            label = "day_check_border"
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    selectedEncryptedNames = if (isDayAllSelected) {
                                                        selectedEncryptedNames - groupKeys
                                                    } else {
                                                        selectedEncryptedNames + groupKeys
                                                    }
                                                }
                                                .padding(horizontal = 4.dp)
                                                .testTag("select_day_$dateKey")
                                        ) {
                                            Text(
                                                text = if (isDayAllSelected) "Selected" else "Select day",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = dayBorderColor
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(dayBgColor)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = dayBorderColor,
                                                        shape = RoundedCornerShape(5.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = isDayAllSelected,
                                                    enter = scaleIn(spring(dampingRatio = 0.65f)) + fadeIn(tween(120)),
                                                    exit = scaleOut(tween(100)) + fadeOut(tween(100))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Day Selected",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            items(itemsInGroup, key = { it.encryptedName }) { item ->
                                val isItemSelected = item.encryptedName in selectedEncryptedNames
                                GalleryGridItem(
                                    item = item,
                                    imageLoader = customImageLoader,
                                    isAnimating = animatingItem == item.encryptedName,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isItemSelected,
                                    onItemClick = { bounds ->
                                        if (isSelectionMode) {
                                            selectedEncryptedNames = if (isItemSelected) {
                                                selectedEncryptedNames - item.encryptedName
                                            } else {
                                                selectedEncryptedNames + item.encryptedName
                                            }
                                        } else {
                                            viewModel.selectMedia(item, bounds)
                                        }
                                    },
                                    onPositioned = { bounds -> viewModel.updateThumbnailBounds(item.encryptedName, bounds) },
                                    modifier = Modifier.animateItem(),
                                    dek = dek,
                                    isOneShotLivePlaying = oneShotLiveItemKey == item.encryptedName,
                                    onOneShotLiveFinished = {
                                        if (oneShotLiveItemKey == item.encryptedName) {
                                            oneShotLiveItemKey = null
                                        }
                                    },
                                    oneShotTriggerId = oneShotLiveTriggerId
                                )
                            }
                        }
                    }
                }
            }

            if (showBulkDeleteDialog) {
                val selectedItemsList = remember(items, selectedEncryptedNames) {
                    items.filter { it.encryptedName in selectedEncryptedNames }
                }
                AlertDialog(
                    onDismissRequest = { showBulkDeleteDialog = false },
                    title = {
                        Text(
                            text = "Delete ${selectedItemsList.size} Selected Media?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "This will permanently erase ${selectedItemsList.size} encrypted file(s) from your vault. This action cannot be undone."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showBulkDeleteDialog = false
                                val toDelete = selectedItemsList
                                toDelete.forEach { ThumbBitmapCache.remove(it.encryptedName) }
                                viewModel.deleteMultipleMedia(context, toDelete) {
                                    selectedEncryptedNames = emptySet()
                                    isSelectionMode = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("confirm_bulk_delete_button")
                        ) {
                            Text("DELETE", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBulkDeleteDialog = false }) {
                            Text("CANCEL")
                        }
                    }
                )
            }

            // Lớp phủ mờ khi mở menu Import (+)
            AnimatedVisibility(
                visible = isImportMenuExpanded && selectedMedia == null,
                enter = fadeIn(floatingBarSpring()),
                exit = fadeOut(floatingBarSpring()),
                modifier = Modifier.zIndex(999f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            isImportMenuExpanded = false
                        }
                )
            }

            // Cụm menu Import (+) và Floating Bar luôn nằm ở lớp trên cùng (zIndex cao nhất) ở cạnh dưới màn hình
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1000f)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .graphicsLayer {
                        alpha = if (selectedMedia != null) 0f else 1f
                    }
            ) {
                AnimatedVisibility(
                    visible = isImportMenuExpanded && floatingBarState == FloatingBarState.COLLAPSED,
                    enter = fadeIn(floatingBarSpring()) +
                            slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                            ) +
                            scaleIn(initialScale = 0.85f, animationSpec = floatingBarSpring()),
                    exit = fadeOut(floatingBarSpring()) +
                            slideOutVertically(
                                targetOffsetY = { it / 2 },
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                            ) +
                            scaleOut(targetScale = 0.85f, animationSpec = floatingBarSpring())
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    isImportMenuExpanded = false
                                    folderImportLauncher.launch(null)
                                }
                                .testTag("import_folder_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Import Folder",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Filled.CreateNewFolder,
                                    contentDescription = "Import Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    isImportMenuExpanded = false
                                    importLauncher.launch(arrayOf("image/*", "video/*"))
                                }
                                .testTag("import_files_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Select Files",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = "Select Files",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                GalleryFloatingBar(
                    barState = floatingBarState,
                    onBarStateChange = { newState ->
                        isImportMenuExpanded = false
                        floatingBarState = newState
                    },
                    onConfirmDownload = {
                        val targetItems = if (selectedEncryptedNames.isNotEmpty()) {
                            items.filter { it.encryptedName in selectedEncryptedNames }
                        } else {
                            items
                        }
                        if (targetItems.isEmpty()) {
                            floatingBarState = FloatingBarState.DOWNLOAD_COMPLETED
                        } else {
                            floatingBarState = FloatingBarState.DOWNLOADING
                            viewModel.exportMultipleMedia(
                                context = context,
                                itemsToExport = targetItems,
                                showOverlayLoading = false
                            ) {
                                floatingBarState = FloatingBarState.DOWNLOAD_COMPLETED
                                selectedEncryptedNames = emptySet()
                                isSelectionMode = false
                            }
                        }
                    },
                    onCancelDownload = {
                        if (floatingBarState == FloatingBarState.DOWNLOADING || isExporting) {
                            viewModel.cancelImport()
                        }
                        floatingBarState = FloatingBarState.COLLAPSED
                    },
                    onShowInFolder = {
                        viewModel.openExportedFolder(context)
                    },
                    onLiveClick = {
                        // Nhấn icon Live trên floating bar: phát nội dung Live đúng 1 lần duy nhất, không loop
                        val candidateLive = items.firstOrNull {
                            it.encryptedName in selectedEncryptedNames && it.isLivePhoto
                        } ?: run {
                            val visibleKeys = gridState.layoutInfo.visibleItemsInfo
                                .mapNotNull { it.key as? String }
                                .toSet()
                            items.firstOrNull { it.encryptedName in visibleKeys && it.isLivePhoto }
                                ?: items.firstOrNull { it.isLivePhoto }
                        }
                        if (candidateLive != null) {
                            oneShotLiveItemKey = candidateLive.encryptedName
                            oneShotLiveTriggerId++
                        }
                    },
                    onAddClick = {
                        isImportMenuExpanded = !isImportMenuExpanded
                    },
                    isAddMenuExpanded = isImportMenuExpanded,
                    isViewerMode = false,
                    isBarActive = selectedMedia == null,
                    isSelectionMode = isSelectionMode,
                    isFullVaultSelected = isFullVaultSelected,
                    hasSelectedItems = selectedEncryptedNames.isNotEmpty(),
                    downloadProgress = smoothImportProgress,
                    onRefreshClick = {
                        viewModel.loadVaultItems(context)
                    },
                    onToggleSelectAll = {
                        selectedEncryptedNames = if (isFullVaultSelected) {
                            emptySet()
                        } else {
                            orderedKeys.toSet()
                        }
                    },
                    onDeleteClick = {
                        showBulkDeleteDialog = true
                    },
                    onToggleSelectionMode = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedEncryptedNames = emptySet()
                            autoScrollSpeedPx = 0f
                        } else {
                            isSelectionMode = true
                        }
                    },
                    drawBackdrop = { barLeft, barTop ->
                        val thumbVer = ThumbBitmapCache.version.intValue
                        if (thumbVer >= 0) {
                            drawGalleryGridBackdrop(
                                barLeft = barLeft,
                                barTop = barTop,
                                gridState = gridState,
                                gridWindowX = gridWindowX,
                                gridWindowY = gridWindowY,
                                boundsMap = boundsMap,
                                excludeEncryptedName = animatingItem,
                                alphaMultiplier = 1f
                            )
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = error != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(160)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            ) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("DISMISS", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    Text(text = error ?: "")
                }
            }

            // Loading / Encryption Progress Overlay với tiến độ bao quanh hình vuông + ảnh thumb fade-in / fade-out
            AnimatedVisibility(
                visible = isLoading && videoFramePickRequest == null,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                val realThumbSizeDp = with(density) { VaultViewModel.THUMB_SIZE_PX.toDp() }
                val squareProgressBoxSizeDp = realThumbSizeDp + 12.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) {
                    val cardScale by animateFloatAsState(
                        targetValue = if (isLoading) 1f else 0.92f,
                        animationSpec = tween(durationMillis = if (isLoading) 200 else 150),
                        label = "import_progress_card_scale"
                    )
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .widthIn(min = 240.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SquareProgressBox(
                                progress = if (isProcessCompleted) 1f else smoothImportProgress,
                                isIndeterminate = (importProgress <= 0f && !isProcessCompleted) || isCancellingImport,
                                isCompleted = isProcessCompleted,
                                activeColor = if (isCancellingImport) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                boxSize = squareProgressBoxSizeDp,
                                strokeWidth = 3.5.dp,
                                cornerRadius = 10.dp
                            ) {
                                val thumbState = EncryptingThumbState(
                                    bitmap = currentImportThumb,
                                    isVideo = currentImportIsVideo,
                                    isLivePhoto = currentImportIsLivePhoto
                                )
                                AnimatedContent(
                                    targetState = thumbState,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing))
                                            .togetherWith(fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)))
                                    },
                                    contentKey = { it.bitmap },
                                    label = "encrypting_thumb_fade"
                                ) { state ->
                                    Box(
                                        modifier = Modifier
                                            .size(realThumbSizeDp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (state.bitmap != null) {
                                            Image(
                                                bitmap = state.bitmap.asImageBitmap(),
                                                contentDescription = "Encrypting Thumbnail Preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            if (state.isVideo || state.isLivePhoto) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.Black.copy(alpha = 0.65f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (state.isVideo) Icons.Filled.PlayArrow else Icons.Filled.MotionPhotosOn,
                                                        contentDescription = if (state.isVideo) "Video file" else "Live Photo",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if ((importProgress > 0f || isProcessCompleted) && !isCancellingImport) {
                                val pct = if (isProcessCompleted) 100 else (smoothImportProgress * 100).toInt().coerceIn(0, 100)
                                Text(
                                    text = "$pct%",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Text(
                                text = when {
                                    isCancellingImport && isExporting -> "Cancelling & cleaning up exported files..."
                                    isCancellingImport -> "Cancelling & deleting imported files..."
                                    isProcessCompleted -> "Completed!"
                                    isExporting && importProgress > 0f -> "Decrypting & exporting original media..."
                                    importProgress > 0f -> "Encrypting media blocks..."
                                    else -> "Processing..."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isCancellingImport) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (importQueueTotal > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "File ${importQueueIndex.coerceAtLeast(1)} / $importQueueTotal",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (importQueueTotal > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { viewModel.cancelImport() },
                                    enabled = !isCancellingImport,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("cancel_import_progress_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Cancel Import",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            isCancellingImport -> "CANCELLING..."
                                            isExporting -> "CANCEL EXPORT"
                                            else -> "CANCEL IMPORT"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // UI CHỌN KHUNG HÌNH (FRAME) TUỲ THÍCH ĐỂ LÀM THUMBNAIL CHO VIDEO NGAY BƯỚC ĐẦU
            videoFramePickRequest?.let { request ->
                VideoFramePickerDialog(
                    request = request,
                    onConfirm = { chosenBitmap ->
                        viewModel.confirmVideoFrameSelection(chosenBitmap)
                    },
                    onCancelImport = {
                        viewModel.cancelImport()
                    }
                )
            }
        }
    }
}

@Composable
fun VideoFramePickerDialog(
    request: VideoFramePickRequest,
    onConfirm: (Bitmap?) -> Unit,
    onCancelImport: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val realThumbSizeDp = with(density) { VaultViewModel.THUMB_SIZE_PX.toDp() }

    var durationMs by remember(request.uri, request.queueIndex) { mutableLongStateOf(0L) }
    var selectedPositionMs by remember(request.uri, request.queueIndex) { mutableFloatStateOf(0f) }
    var isDraggingSlider by remember(request.uri, request.queueIndex) { mutableStateOf(false) }
    var currentPreviewBitmap by remember(request.uri, request.queueIndex) { mutableStateOf<Bitmap?>(null) }
    var filmstripBitmaps by remember(request.uri, request.queueIndex) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isExtractingExactFrame by remember(request.uri, request.queueIndex) { mutableStateOf(false) }
    var isExtractFrameCompleting by remember(request.uri, request.queueIndex) { mutableStateOf(false) }
    var isExtractFrameTickVisible by remember(request.uri, request.queueIndex) { mutableStateOf(false) }
    var isInitialFrameLoaderVisible by remember(request.uri, request.queueIndex) { mutableStateOf(true) }
    var isInitialFrameCompleting by remember(request.uri, request.queueIndex) { mutableStateOf(false) }
    var isInitialFrameTickVisible by remember(request.uri, request.queueIndex) { mutableStateOf(false) }
    val goldColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(request.uri, request.queueIndex) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, request.uri)
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val firstFrame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                withContext(Dispatchers.Main) {
                    durationMs = dur.coerceAtLeast(1L)
                    currentPreviewBitmap = firstFrame
                    isInitialFrameCompleting = true
                    delay(180L)
                    isInitialFrameTickVisible = true
                    delay(320L)
                    isInitialFrameLoaderVisible = false
                }

                if (dur > 0L) {
                    val stripCount = 8
                    val strip = mutableListOf<Bitmap>()
                    for (i in 0 until stripCount) {
                        val timeUs = ((dur * i) / stripCount) * 1000L
                        val rawFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)
                        } else {
                            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { bmp ->
                                val scaled = Bitmap.createScaledBitmap(bmp, 120, 120, true)
                                if (scaled != bmp) bmp.recycle()
                                scaled
                            }
                        }
                        if (rawFrame != null) strip.add(rawFrame)
                    }
                    withContext(Dispatchers.Main) {
                        filmstripBitmaps = strip
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(selectedPositionMs, isDraggingSlider, request.uri, request.queueIndex) {
        if (durationMs <= 0L) return@LaunchedEffect
        val targetUs = (selectedPositionMs.toLong().coerceIn(0L, durationMs)) * 1000L

        if (isDraggingSlider) {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, request.uri)
                    val syncBmp = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (syncBmp != null) {
                        withContext(Dispatchers.Main) {
                            currentPreviewBitmap = syncBmp
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        } else {
            delay(100L)
            isExtractFrameCompleting = false
            isExtractFrameTickVisible = false
            isExtractingExactFrame = true
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, request.uri)
                    val exactBmp = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (exactBmp != null) {
                        withContext(Dispatchers.Main) {
                            currentPreviewBitmap = exactBmp
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        isExtractFrameCompleting = true
                        delay(180L)
                        isExtractFrameTickVisible = true
                        delay(320L)
                        isExtractingExactFrame = false
                        isExtractFrameCompleting = false
                        isExtractFrameTickVisible = false
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Dùng nút USE THIS FRAME hoặc CANCEL */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 14.dp)
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Video Thumbnail",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = request.fileName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${request.queueIndex} / ${request.queueTotal}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = currentPreviewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected Video Frame",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxW = size.width
                            val boxH = size.height
                            val bmpW = bmp.width.toFloat().coerceAtLeast(1f)
                            val bmpH = bmp.height.toFloat().coerceAtLeast(1f)

                            val fitScale = minOf(boxW / bmpW, boxH / bmpH)
                            val drawnW = bmpW * fitScale
                            val drawnH = bmpH * fitScale
                            val drawnLeft = (boxW - drawnW) / 2f
                            val drawnTop = (boxH - drawnH) / 2f

                            val squareSize = minOf(drawnW, drawnH)
                            val sqLeft = (boxW - squareSize) / 2f
                            val sqTop = (boxH - squareSize) / 2f
                            val sqRight = sqLeft + squareSize
                            val sqBottom = sqTop + squareSize

                            val dimColor = Color.Black.copy(alpha = 0.48f)

                            if (drawnW > squareSize) {
                                drawRect(
                                    color = dimColor,
                                    topLeft = Offset(drawnLeft, drawnTop),
                                    size = ComposeSize(sqLeft - drawnLeft, drawnH)
                                )
                                drawRect(
                                    color = dimColor,
                                    topLeft = Offset(sqRight, drawnTop),
                                    size = ComposeSize((drawnLeft + drawnW) - sqRight, drawnH)
                                )
                            } else if (drawnH > squareSize) {
                                drawRect(
                                    color = dimColor,
                                    topLeft = Offset(drawnLeft, drawnTop),
                                    size = ComposeSize(drawnW, sqTop - drawnTop)
                                )
                                drawRect(
                                    color = dimColor,
                                    topLeft = Offset(drawnLeft, sqBottom),
                                    size = ComposeSize(drawnW, (drawnTop + drawnH) - sqBottom)
                                )
                            }

                            drawRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = Offset(sqLeft, sqTop),
                                size = ComposeSize(squareSize, squareSize),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            val cornerLen = minOf(20.dp.toPx(), squareSize * 0.22f)
                            val cornerStroke = 3.5.dp.toPx()
                            drawLine(goldColor, Offset(sqLeft, sqTop), Offset(sqLeft + cornerLen, sqTop), cornerStroke)
                            drawLine(goldColor, Offset(sqLeft, sqTop), Offset(sqLeft, sqTop + cornerLen), cornerStroke)
                            drawLine(goldColor, Offset(sqRight - cornerLen, sqTop), Offset(sqRight, sqTop), cornerStroke)
                            drawLine(goldColor, Offset(sqRight, sqTop), Offset(sqRight, sqTop + cornerLen), cornerStroke)
                            drawLine(goldColor, Offset(sqLeft, sqBottom - cornerLen), Offset(sqLeft, sqBottom), cornerStroke)
                            drawLine(goldColor, Offset(sqLeft, sqBottom), Offset(sqLeft + cornerLen, sqBottom), cornerStroke)
                            drawLine(goldColor, Offset(sqRight - cornerLen, sqBottom), Offset(sqRight, sqBottom), cornerStroke)
                            drawLine(goldColor, Offset(sqRight, sqBottom - cornerLen), Offset(sqRight, sqBottom), cornerStroke)
                        }
                    }
                    if (bmp == null || isInitialFrameLoaderVisible) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(27.dp))
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedCircularProgressWithTick(
                                isCompletingOrCompleted = isInitialFrameCompleting,
                                isTickVisible = isInitialFrameTickVisible,
                                color = MaterialTheme.colorScheme.primary,
                                size = 36.dp,
                                strokeWidth = 3.dp
                            )
                        }
                    }

                    // PREVIEW THUMBNAIL KÍCH THƯỚC THẬT (Hình vuông 216 x 216 px) Ở GÓC TRÊN BÊN PHẢI VỚI FADE IN / OUT
                    // Dùng tên đầy đủ androidx.compose.animation.AnimatedVisibility để tránh xung đột với ColumnScope của Column bên ngoài
                    androidx.compose.animation.AnimatedVisibility(
                        visible = bmp != null,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(180)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(realThumbSizeDp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                )
                        ) {
                            bmp?.let { previewBmp ->
                                Image(
                                    bitmap = previewBmp.asImageBitmap(),
                                    contentDescription = "Actual 1:1 Thumbnail Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${formatMillisPrecise(selectedPositionMs.toLong())} / ${formatMillisPrecise(durationMs)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // THANH TUA THỜI GIAN GỘP CHUNG VỚI BAR THUMB HORIZONTAL (FILMSTRIP SCRUBBER)
                val currentDurationMs by rememberUpdatedState(durationMs)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .pointerInput(request.uri, request.queueIndex) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val barWidth = size.width.toFloat().coerceAtLeast(1f)
                                val dur = currentDurationMs.toFloat().coerceAtLeast(1f)

                                isDraggingSlider = true
                                selectedPositionMs = ((down.position.x / barWidth).coerceIn(0f, 1f)) * dur
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    selectedPositionMs = ((change.position.x / barWidth).coerceIn(0f, 1f)) * dur
                                    change.consume()
                                }
                                isDraggingSlider = false
                            }
                        }
                        .testTag("video_thumb_slider")
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = filmstripBitmaps.isNotEmpty(),
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            filmstripBitmaps.forEach { stripBmp ->
                                Image(
                                    bitmap = stripBmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dur = durationMs.toFloat().coerceAtLeast(1f)
                        val fraction = (selectedPositionMs / dur).coerceIn(0f, 1f)
                        val playheadX = (fraction * size.width).coerceIn(3.dp.toPx(), size.width - 3.dp.toPx())

                        drawRect(
                            color = Color.Black.copy(alpha = 0.22f),
                            topLeft = Offset.Zero,
                            size = ComposeSize(playheadX, size.height)
                        )

                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            topLeft = Offset(playheadX - 3.5.dp.toPx(), 0f),
                            size = ComposeSize(7.dp.toPx(), size.height),
                            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
                        )

                        drawRoundRect(
                            color = goldColor,
                            topLeft = Offset(playheadX - 2.dp.toPx(), 1.5.dp.toPx()),
                            size = ComposeSize(4.dp.toPx(), size.height - 3.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(-1000L to "-1s", -100L to "-0.1s", 100L to "+0.1s", 1000L to "+1s").forEach { (deltaMs, label) ->
                        OutlinedButton(
                            onClick = {
                                isDraggingSlider = false
                                selectedPositionMs = (selectedPositionMs + deltaMs).coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f))
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancelImport,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(0.42f)
                            .height(48.dp)
                            .testTag("cancel_import_video_dialog_button")
                    ) {
                        Text(
                            text = "CANCEL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { onConfirm(currentPreviewBitmap) },
                        enabled = !isExtractingExactFrame,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        ),
                        modifier = Modifier
                            .weight(0.58f)
                            .height(48.dp)
                            .testTag("confirm_video_frame_button")
                    ) {
                        AnimatedContent(
                            targetState = isExtractingExactFrame,
                            transitionSpec = {
                                fadeIn(tween(140)).togetherWith(fadeOut(tween(100)))
                            },
                            label = "use_frame_btn_state"
                        ) { extracting ->
                            if (extracting) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    AnimatedCircularProgressWithTick(
                                        isCompletingOrCompleted = isExtractFrameCompleting,
                                        isTickVisible = isExtractFrameTickVisible,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        size = 18.dp,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "EXTRACTING...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Text(
                                    text = "USE THIS FRAME",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMillisPrecise(millis: Long): String {
    val safeMs = millis.coerceAtLeast(0L)
    val totalSec = safeMs / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val tenths = (safeMs % 1000) / 100
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
}

@Composable
fun GalleryGridItem(
    item: VaultItem,
    imageLoader: ImageLoader?,
    isAnimating: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onItemClick: (FloatArray?) -> Unit,
    onPositioned: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
    dek: ByteArray? = null,
    isOneShotLivePlaying: Boolean = false,
    onOneShotLiveFinished: () -> Unit = {},
    oneShotTriggerId: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val itemBoundsRef = remember(item.encryptedName) { arrayOfNulls<FloatArray>(1) }
    var cachedThumb by remember(item.encryptedName) {
        mutableStateOf(ThumbBitmapCache.get(item.encryptedName))
    }

    val imageRequest = remember(item.uri, context) {
        val thumbUriWithTier = item.uri.buildUpon().appendQueryParameter("tier", "THUMB").build()
        ImageRequest.Builder(context)
            .data(thumbUriWithTier)
            .size(Size.ORIGINAL)
            .build()
    }

    val painter = if (cachedThumb == null && imageLoader != null) {
        rememberAsyncImagePainter(
            model = imageRequest,
            imageLoader = imageLoader
        )
    } else null

    val painterState = painter?.state
    var intrinsicWidth by remember(item.encryptedName) {
        mutableFloatStateOf(cachedThumb?.width?.toFloat() ?: 0f)
    }
    var intrinsicHeight by remember(item.encryptedName) {
        mutableFloatStateOf(cachedThumb?.height?.toFloat() ?: 0f)
    }

    val isImageLoaded = cachedThumb != null || painterState is coil.compose.AsyncImagePainter.State.Success
    if (painterState is coil.compose.AsyncImagePainter.State.Success) {
        intrinsicWidth = painterState.result.drawable.intrinsicWidth.toFloat()
        intrinsicHeight = painterState.result.drawable.intrinsicHeight.toFloat()
    }

    val imageLoadedAlpha by animateFloatAsState(
        targetValue = if (isImageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = if (cachedThumb != null) 0 else 180, easing = FastOutSlowInEasing),
        label = "thumb_fade_in"
    )

    val selectedScale by animateFloatAsState(
        targetValue = if (isSelectionMode && isSelected) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "grid_item_selected_scale"
    )
    val selectedCornerDp by animateDpAsState(
        targetValue = if (isSelectionMode && isSelected) 8.dp else 0.dp,
        animationSpec = tween(180),
        label = "grid_item_selected_corner"
    )

    // Trạng thái nhấn giữ (hold) để loop Live Photo trên thumbnail vs nhấn nút Live (phát 1 lần)
    var isHoldingLiveThumb by remember(item.encryptedName) { mutableStateOf(false) }
    var isLiveThumbVideoRendered by remember(item.encryptedName) { mutableStateOf(false) }
    var liveThumbVideoW by remember(item.encryptedName) { mutableIntStateOf(0) }
    var liveThumbVideoH by remember(item.encryptedName) { mutableIntStateOf(0) }
    var liveThumbPlayer by remember(item.encryptedName) { mutableStateOf<ExoPlayer?>(null) }

    val currentOnOneShotFinished by rememberUpdatedState(onOneShotLiveFinished)
    val shouldPlayLiveThumb = item.isLivePhoto && dek != null && (isHoldingLiveThumb || isOneShotLivePlaying)

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode && isHoldingLiveThumb) {
            isHoldingLiveThumb = false
        }
    }

    LaunchedEffect(shouldPlayLiveThumb, isHoldingLiveThumb, isOneShotLivePlaying, oneShotTriggerId, dek) {
        val key = dek
        if (item.isLivePhoto && key != null && (isHoldingLiveThumb || isOneShotLivePlaying)) {
            val loopMode = if (isHoldingLiveThumb) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (liveThumbPlayer == null) {
                val liveUri = item.uri.buildUpon().appendQueryParameter("live", "1").build()
                liveThumbPlayer = ExoPlayer.Builder(context).build().apply {
                    val mediaSource = ProgressiveMediaSource.Factory(
                        EncryptedDataSourceFactory(context, key)
                    ).createMediaSource(MediaItem.fromUri(liveUri))
                    setMediaSource(mediaSource)
                    repeatMode = loopMode
                    volume = 0f
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                liveThumbVideoW = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
                                liveThumbVideoH = videoSize.height
                            }
                        }

                        override fun onRenderedFirstFrame() {
                            isLiveThumbVideoRendered = true
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED && repeatMode == Player.REPEAT_MODE_OFF) {
                                isLiveThumbVideoRendered = false
                                currentOnOneShotFinished()
                            }
                        }
                    })
                }
            } else {
                liveThumbPlayer?.repeatMode = loopMode
                liveThumbPlayer?.seekTo(0L)
            }
            liveThumbPlayer?.playWhenReady = true
            liveThumbPlayer?.play()
        } else {
            isLiveThumbVideoRendered = false
            liveThumbPlayer?.let { player ->
                player.playWhenReady = false
                player.stop()
                player.clearVideoSurface()
                player.release()
            }
            liveThumbPlayer = null
        }
    }

    DisposableEffect(item.encryptedName) {
        onDispose {
            isHoldingLiveThumb = false
            isLiveThumbVideoRendered = false
            liveThumbPlayer?.let { player ->
                player.playWhenReady = false
                player.stop()
                player.clearVideoSurface()
                player.release()
            }
            liveThumbPlayer = null
        }
    }

    val liveThumbAlpha by animateFloatAsState(
        targetValue = if (shouldPlayLiveThumb && isLiveThumbVideoRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "live_thumb_video_alpha"
    )

    LaunchedEffect(item.encryptedName, painterState) {
        val successState = painterState as? coil.compose.AsyncImagePainter.State.Success ?: return@LaunchedEffect
        val bmp = (successState.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val imgBmp = bmp.asImageBitmap()
        ThumbBitmapCache.put(item.encryptedName, imgBmp)
        cachedThumb = imgBmp
    }

    LaunchedEffect(intrinsicWidth, intrinsicHeight) {
        if (intrinsicWidth > 0f && intrinsicHeight > 0f) {
            itemBoundsRef[0]?.let { b ->
                if (abs(b[4] - intrinsicWidth) > 0.5f || abs(b[5] - intrinsicHeight) > 0.5f) {
                    val updated = floatArrayOf(b[0], b[1], b[2], b[3], intrinsicWidth, intrinsicHeight)
                    itemBoundsRef[0] = updated
                    onPositioned(updated)
                }
            }
        }
    }

    val currentOnItemClick by rememberUpdatedState(onItemClick)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                alpha = if (isAnimating) 0f else 1f
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                val size = coordinates.size
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val prev = itemBoundsRef[0]
                if (prev == null ||
                    abs(prev[0] - position.x) > 0.5f ||
                    abs(prev[1] - position.y) > 0.5f ||
                    abs(prev[2] - w) > 0.5f ||
                    abs(prev[3] - h) > 0.5f
                ) {
                    val bounds = floatArrayOf(
                        position.x,
                        position.y,
                        w,
                        h,
                        intrinsicWidth,
                        intrinsicHeight
                    )
                    itemBoundsRef[0] = bounds
                    onPositioned(bounds)
                }
            }
            .pointerInput(item.encryptedName, item.isLivePhoto, isSelectionMode, dek != null) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    var movedTooFar = false
                    var holdActivated = false
                    var releasedNormally = false

                    val holdJob: Job? = if (item.isLivePhoto && !isSelectionMode && dek != null) {
                        coroutineScope.launch {
                            delay(240L)
                            if (!movedTooFar) {
                                holdActivated = true
                                isHoldingLiveThumb = true
                            }
                        }
                    } else null

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            if (changes.isEmpty() || changes.all { !it.pressed }) {
                                releasedNormally = true
                                break
                            }
                            val activeChange = changes.firstOrNull { it.id == down.id } ?: changes.first()
                            val dist = (activeChange.position - startPos).getDistance()
                            if (!holdActivated) {
                                if (activeChange.isConsumed || dist > touchSlop) {
                                    movedTooFar = true
                                    holdJob?.cancel()
                                    break
                                }
                            } else {
                                activeChange.consume()
                            }
                        }
                    } finally {
                        holdJob?.cancel()
                        isHoldingLiveThumb = false
                    }

                    if (releasedNormally && !holdActivated && !movedTooFar) {
                        currentOnItemClick(itemBoundsRef[0])
                    }
                }
            }
            .testTag("grid_item_${item.encryptedName}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = selectedScale
                    scaleY = selectedScale
                }
                .clip(RoundedCornerShape(selectedCornerDp))
        ) {
            if (!isImageLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            item.isVideo -> Icons.Filled.Videocam
                            item.isLivePhoto -> Icons.Filled.MotionPhotosOn
                            else -> Icons.Filled.Image
                        },
                        contentDescription = "Placeholder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            val thumbBmp = cachedThumb
            if (thumbBmp != null) {
                Image(
                    bitmap = thumbBmp,
                    contentDescription = item.originalName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = item.originalName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = imageLoadedAlpha }
                )
            }

            if (item.isLivePhoto && (shouldPlayLiveThumb || liveThumbAlpha > 0.01f) && liveThumbPlayer != null) {
                val effW = if (liveThumbVideoW > 0) liveThumbVideoW else intrinsicWidth.toInt()
                val effH = if (liveThumbVideoH > 0) liveThumbVideoH else intrinsicHeight.toInt()
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            isOpaque = false
                            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                applyCenterCropMatrix(this, effW, effH)
                            }
                            liveThumbPlayer?.setVideoTextureView(this)
                        }
                    },
                    update = { textureView ->
                        liveThumbPlayer?.setVideoTextureView(textureView)
                        applyCenterCropMatrix(textureView, effW, effH)
                    },
                    onRelease = { textureView ->
                        liveThumbPlayer?.clearVideoTextureView(textureView)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = liveThumbAlpha }
                )
            }
        }

        // Nhãn báo Video hoặc Live Photo ở góc dưới
        if (item.isVideo || item.isLivePhoto) {
            Box(
                modifier = Modifier
                    .align(if (isSelectionMode) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (item.isVideo) Icons.Filled.PlayArrow else Icons.Filled.MotionPhotosOn,
                    contentDescription = if (item.isVideo) "Video file" else "Live Photo",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            enter = scaleIn(animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f)) + fadeIn(tween(150)),
            exit = scaleOut(animationSpec = tween(130)) + fadeOut(tween(130)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val checkBgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
                animationSpec = tween(150),
                label = "checkbox_bg_color"
            )
            val checkBorderColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                animationSpec = tween(150),
                label = "checkbox_border_color"
            )
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(checkBgColor)
                    .border(
                        width = 1.5.dp,
                        color = checkBorderColor,
                        shape = RoundedCornerShape(5.dp)
                    )
                    .testTag("checkbox_${item.encryptedName}"),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(tween(100)),
                    exit = scaleOut(tween(90)) + fadeOut(tween(90))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun applyCenterCropMatrix(textureView: TextureView, videoW: Int, videoH: Int) {
    val viewW = textureView.width.toFloat()
    val viewH = textureView.height.toFloat()
    if (viewW <= 0f || viewH <= 0f || videoW <= 0 || videoH <= 0) return

    val videoAspect = videoW.toFloat() / videoH.toFloat()
    val viewAspect = viewW / viewH

    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        scaleX = videoAspect / viewAspect
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    }

    val matrix = android.graphics.Matrix()
    matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
    textureView.setTransform(matrix)
}

private fun formatGroupDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown Date"
    val date = java.util.Date(timestamp)
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { time = date }

    val format = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())

    if (now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)) {
        return "Today"
    }

    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
        yesterday.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)) {
        return "Yesterday"
    }

    return format.format(date)
}