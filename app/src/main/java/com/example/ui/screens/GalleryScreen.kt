package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.example.coil.EncryptedUriFetcher
import com.example.ui.VaultItem
import com.example.ui.VaultViewModel
import com.example.ui.VideoFramePickRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ------------------------------------------------------------------------------------
// CACHE BITMAP THUMB DÙNG CHUNG GIỮA GalleryScreen VÀ MediaViewerScreen
// ------------------------------------------------------------------------------------
object ThumbBitmapCache {
    private const val MAX_ENTRIES = 80

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

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
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
@Composable
private fun SquareProgressBox(
    progress: Float,
    isIndeterminate: Boolean,
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
                if (isIndeterminate) {
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
                    val stopDist = (progress.coerceIn(0f, 1f)) * totalLen
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
        }
    }
}

private data class EncryptingThumbState(
    val bitmap: Bitmap?,
    val isVideo: Boolean,
    val isLivePhoto: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val items by viewModel.items.collectAsState()
    val vaultUri by viewModel.vaultUri.collectAsState()
    val dek by viewModel.dek.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val importQueueTotal by viewModel.importQueueTotal.collectAsState()
    val importQueueIndex by viewModel.importQueueIndex.collectAsState()
    val currentImportThumb by viewModel.currentImportThumb.collectAsState()
    val currentImportIsVideo by viewModel.currentImportIsVideo.collectAsState()
    val currentImportIsLivePhoto by viewModel.currentImportIsLivePhoto.collectAsState()
    val isCancellingImport by viewModel.isCancellingImport.collectAsState()
    val videoFramePickRequest by viewModel.videoFramePickRequest.collectAsState()
    val error by viewModel.error.collectAsState()
    val animatingItem by viewModel.animatingItem.collectAsState()

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

    val folderName = remember(vaultUri) {
        val raw = vaultUri?.lastPathSegment?.substringAfterLast(':') ?: "Vault"
        raw.trim('/').substringAfterLast('/').ifEmpty { raw }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = folderName.uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = if (isSelectionMode) {
                            "${selectedEncryptedNames.size} / ${items.size} selected"
                        } else {
                            "${items.size} secure files"
                        }
                        val subtitleColor by animateColorAsState(
                            targetValue = if (isSelectionMode && selectedEncryptedNames.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(200),
                            label = "subtitle_color"
                        )
                        AnimatedContent(
                            targetState = subtitleText,
                            transitionSpec = {
                                fadeIn(tween(150)).togetherWith(fadeOut(tween(120)))
                            },
                            label = "subtitle_text_anim"
                        ) { textVal ->
                            Text(
                                text = textVal,
                                fontSize = 11.sp,
                                color = subtitleColor
                            )
                        }
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = isSelectionMode && items.isNotEmpty(),
                        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)),
                        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140))
                    ) {
                        TextButton(
                            onClick = {
                                selectedEncryptedNames = if (isFullVaultSelected) {
                                    emptySet()
                                } else {
                                    orderedKeys.toSet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("select_full_vault_button")
                        ) {
                            val tintColor by animateColorAsState(
                                targetValue = if (isFullVaultSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = tween(180),
                                label = "all_btn_tint"
                            )
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Select Full Vault",
                                tint = tintColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFullVaultSelected) "NONE" else "ALL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFullVaultSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val showDeleteAction = isSelectionMode && selectedEncryptedNames.isNotEmpty()
                    AnimatedContent(
                        targetState = showDeleteAction,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.75f, animationSpec = spring(dampingRatio = 0.7f)))
                                .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.75f, animationSpec = tween(140)))
                        },
                        label = "refresh_or_delete_action"
                    ) { isDelete ->
                        if (isDelete) {
                            IconButton(
                                onClick = { showBulkDeleteDialog = true },
                                modifier = Modifier.testTag("delete_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.loadVaultItems(context) },
                                modifier = Modifier.testTag("refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Grid"
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedEncryptedNames = emptySet()
                                autoScrollSpeedPx = 0f
                            } else {
                                isSelectionMode = true
                            }
                        },
                        modifier = Modifier.testTag("choose_edit_button")
                    ) {
                        AnimatedContent(
                            targetState = isSelectionMode,
                            transitionSpec = {
                                (fadeIn(tween(180)) + scaleIn(initialScale = 0.75f, animationSpec = spring(dampingRatio = 0.7f)))
                                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.75f, animationSpec = tween(140)))
                            },
                            label = "choose_edit_icon_anim"
                        ) { selecting ->
                            Icon(
                                imageVector = if (selecting) Icons.Filled.Close else Icons.Filled.Edit,
                                contentDescription = if (selecting) "Cancel Selection" else "Choose Items",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(180)),
                exit = scaleOut(animationSpec = tween(150)) + fadeOut(tween(150))
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = isImportMenuExpanded,
                        enter = fadeIn(tween(180)) +
                                slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                                ) +
                                scaleIn(initialScale = 0.85f, animationSpec = tween(180)),
                        exit = fadeOut(tween(130)) +
                                slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(130)) +
                                scaleOut(targetScale = 0.85f, animationSpec = tween(130))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
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

                    val fabRotation by animateFloatAsState(
                        targetValue = if (isImportMenuExpanded) 45f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "fab_icon_rotation"
                    )
                    FloatingActionButton(
                        onClick = { isImportMenuExpanded = !isImportMenuExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("import_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Import Media Menu",
                            modifier = Modifier.graphicsLayer {
                                rotationZ = fabRotation
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
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
                            .then(dragSelectModifier)
                            .testTag("gallery_grid"),
                        contentPadding = PaddingValues(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
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

                                // Cố định chiều cao 24.dp cho dòng tiêu đề ngày để không bị layout shift khi hiện nút Select day
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
                                    modifier = Modifier.animateItem()
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

            AnimatedVisibility(
                visible = isImportMenuExpanded,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120))
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

            AnimatedVisibility(
                visible = error != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter)
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
                // Khung viền tiến độ hình vuông bao sát quanh ô thumb 216x216 px (cách 6.dp mỗi cạnh)
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
                            // Đường tiến độ bao quanh hình vuông
                            SquareProgressBox(
                                progress = smoothImportProgress,
                                isIndeterminate = importProgress <= 0f || isCancellingImport,
                                activeColor = if (isCancellingImport) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                boxSize = squareProgressBoxSizeDp,
                                strokeWidth = 3.5.dp,
                                cornerRadius = 10.dp
                            ) {
                                // Hiệu ứng Fade In / Fade Out mượt mà khi đổi ảnh thumb của từng file đang mã hoá
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

                                            // Giữ icon video hoặc Live Photo ở góc dưới bên phải của preview thumb khi đang mã hoá
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

                            if (importProgress > 0f && !isCancellingImport) {
                                Text(
                                    text = "${(smoothImportProgress * 100).toInt()}%",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Text(
                                text = when {
                                    isCancellingImport -> "Cancelling & deleting imported files..."
                                    importProgress > 0f -> "Encrypting media blocks..."
                                    else -> "Preparing file..."
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
                                        text = if (isCancellingImport) "CANCELLING..." else "CANCEL IMPORT",
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
                        isExtractingExactFrame = false
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
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
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
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
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
    modifier: Modifier = Modifier
) {
    var itemBounds by remember { mutableStateOf<FloatArray?>(null) }

    val thumbUriWithTier = item.uri.buildUpon().appendQueryParameter("tier", "THUMB").build()
    val painter = if (imageLoader != null) {
        rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbUriWithTier)
                .size(Size.ORIGINAL)
                .build(),
            imageLoader = imageLoader
        )
    } else null

    val painterState = painter?.state
    var intrinsicWidth by remember { mutableStateOf(0f) }
    var intrinsicHeight by remember { mutableStateOf(0f) }

    val isImageLoaded = painterState is coil.compose.AsyncImagePainter.State.Success
    if (isImageLoaded) {
        val success = painterState as coil.compose.AsyncImagePainter.State.Success
        intrinsicWidth = success.result.drawable.intrinsicWidth.toFloat()
        intrinsicHeight = success.result.drawable.intrinsicHeight.toFloat()
    }

    val imageLoadedAlpha by animateFloatAsState(
        targetValue = if (isImageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
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

    LaunchedEffect(item.encryptedName, painterState) {
        val successState = painterState as? coil.compose.AsyncImagePainter.State.Success ?: return@LaunchedEffect
        val bmp = (successState.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        ThumbBitmapCache.put(item.encryptedName, bmp.asImageBitmap())
    }

    LaunchedEffect(intrinsicWidth, intrinsicHeight) {
        if (intrinsicWidth > 0f && intrinsicHeight > 0f) {
            itemBounds?.let { b ->
                val updated = floatArrayOf(b[0], b[1], b[2], b[3], intrinsicWidth, intrinsicHeight)
                itemBounds = updated
                onPositioned(updated)
            }
        }
    }

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
                val bounds = floatArrayOf(
                    position.x,
                    position.y,
                    size.width.toFloat(),
                    size.height.toFloat(),
                    intrinsicWidth,
                    intrinsicHeight
                )
                itemBounds = bounds
                onPositioned(bounds)
            }
            .clickable {
                onItemClick(itemBounds)
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

            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = item.originalName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = imageLoadedAlpha }
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