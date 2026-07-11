package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import com.example.coil.EncryptedUriFetcher
import com.example.ui.VaultItem
import com.example.ui.VaultViewModel

// ------------------------------------------------------------------------------------
// CACHE BITMAP THUMB DÙNG CHUNG GIỮA GalleryScreen VÀ MediaViewerScreen
// TẠI SAO CẦN CÁI NÀY: GalleryGridItem load thumb qua rememberAsyncImagePainter (Coil) ->
// kết quả chỉ là 1 Painter sống bên trong đúng Composable đó, MediaViewerScreen không có
// cách nào "mượn" lại được. Hơn nữa GalleryScreen và MediaViewerScreen mỗi bên tự dựng 1
// ImageLoader riêng (2 instance khác nhau -> 2 memory cache khác nhau) nên dù có gọi qua
// Coil cũng vẫn cache-miss. Y hệt myapp (Gallery.kt): bitmap decode cho ô grid được giữ
// trong closure và truyền thẳng sang openFullscreen(...), KHÔNG decode lại lần 2.
// Ở đây ta làm tương đương bằng 1 cache Bitmap nhỏ, key theo encryptedName: GalleryGridItem
// ghi vào ngay khi Coil decode xong, MediaViewerScreen đọc ra dùng ngay nếu có, chỉ decode
// thủ công (BitmapFactory) khi cache-miss (ví dụ mở thẳng bằng deep link, chưa từng qua Grid).
// ------------------------------------------------------------------------------------
object ThumbBitmapCache {
    private const val MAX_ENTRIES = 80

    // accessOrder = true -> LRU thật sự (mục vừa get() sẽ nhảy lên "mới nhất")
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Back gesture locks the vault (returning to home/welcome screen)
    BackHandler {
        viewModel.lockVault()
    }
    val items by viewModel.items.collectAsState()
    val vaultUri by viewModel.vaultUri.collectAsState()
    val dek by viewModel.dek.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val animatingItem by viewModel.animatingItem.collectAsState()

    // Build the custom secure Coil ImageLoader using our custom decryptor fetcher
    val customImageLoader = remember(dek) {
        dek?.let { key ->
            ImageLoader.Builder(context)
                .components {
                    add(EncryptedUriFetcher.Factory(key))
                }
                .build()
        }
    }

    // SAF File Picker to import media (*/* but we check image/video)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMedia(context, uri)
        }
    }

    val folderName = remember(vaultUri) {
        vaultUri?.lastPathSegment?.substringAfterLast(':') ?: "Vault"
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
                        Text(
                            text = "${items.size} secure files",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadVaultItems(context) },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh Grid"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.lockVault() },
                        modifier = Modifier.testTag("lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            FloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("image/*", "video/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("import_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Import Media"
                )
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
            if (items.isEmpty() && !isLoading) {
                // Empty State Screen
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

                // Grid Screen (5 columns as requested)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("gallery_grid"),
                    contentPadding = PaddingValues(vertical = 2.dp), // Bỏ padding ngang, giữ padding dọc
                    horizontalArrangement = Arrangement.spacedBy(2.dp), // Giảm khoảng cách còn 1 nửa
                    verticalArrangement = Arrangement.spacedBy(2.dp)  // Giảm khoảng cách còn 1 nửa
                ) {
                    groupedItems.forEach { (dateKey, itemsInGroup) ->
                        // Add Date Section Header spanning all 5 columns
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

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = displayHeader,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("section_header_$dateKey")
                                )
                            }
                        }

                        // Add the grid items for this date group
                        items(itemsInGroup, key = { it.encryptedName }) { item ->
                            GalleryGridItem(
                                item = item,
                                imageLoader = customImageLoader,
                                isAnimating = animatingItem == item.encryptedName,
                                onItemClick = { bounds -> viewModel.selectMedia(item, bounds) },
                                onPositioned = { bounds -> viewModel.updateThumbnailBounds(item.encryptedName, bounds) }
                            )
                        }
                    }
                }
            }

            // Error Toast or banner overlay
            error?.let { err ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("DISMISS", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    Text(text = err)
                }
            }

            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Encrypting media blocks...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Debug log dialog removed
        }
    }
}

@Composable
fun GalleryGridItem(
    item: VaultItem,
    imageLoader: ImageLoader?,
    isAnimating: Boolean = false,
    onItemClick: (FloatArray?) -> Unit,
    onPositioned: (FloatArray) -> Unit
) {
    var itemBounds by remember { mutableStateOf<FloatArray?>(null) }

    val painter = if (item.thumbUri != null && imageLoader != null) {
        rememberAsyncImagePainter(
            model = item.thumbUri,
            imageLoader = imageLoader
        )
    } else null

    val painterState = painter?.state
    var intrinsicWidth by remember { mutableStateOf(0f) }
    var intrinsicHeight by remember { mutableStateOf(0f) }

    if (painterState is coil.compose.AsyncImagePainter.State.Success) {
        intrinsicWidth = painterState.result.drawable.intrinsicWidth.toFloat()
        intrinsicHeight = painterState.result.drawable.intrinsicHeight.toFloat()
    }

    // Ghi thẳng bitmap vừa decode xong vào cache dùng chung (xem ThumbBitmapCache ở đầu
    // file). Chỉ ghi 1 lần khi Coil vừa Success cho item này, không ghi lại mỗi recomposition.
    LaunchedEffect(item.encryptedName, painterState) {
        val successState = painterState as? coil.compose.AsyncImagePainter.State.Success ?: return@LaunchedEffect
        val bmp = (successState.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        ThumbBitmapCache.put(item.encryptedName, bmp.asImageBitmap())
    }

    // FIX (giống logic myapp: luôn lấy đúng kích thước thật của ảnh đã load,
    // không tin vào một giá trị "chụp" từ trước có thể còn là 0).
    // onGloballyPositioned chỉ bắn lại khi position/size của Box đổi. Nhưng Box này
    // bị ép .aspectRatio(1f) cố định vuông, nên khi Coil load xong thumbnail và
    // intrinsicWidth/Height cập nhật từ 0 -> giá trị thật, layout KHÔNG đổi
    // -> onGloballyPositioned không re-fire -> bounds gửi lên ViewModel bị "kẹt"
    // ở intrinsic = 0 mãi mãi. Đây là nguyên nhân gốc gây méo ảnh.
    // Nên phải tự đẩy lại bounds mỗi khi biết được kích thước thật của ảnh.
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
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                alpha = if (isAnimating) 0f else 1f
            }
            // Đã xóa bỏ .clip(RoundedCornerShape(...)) để thumb không bo tròn
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
                // itemBounds đã luôn được cập nhật intrinsic thật (xem LaunchedEffect ở trên),
                // nên không cần tự dựng lại FloatArray thủ công nữa.
                onItemClick(itemBounds)
            }
            .testTag("grid_item_${item.encryptedName}")
    ) {
        if (painter != null) {
            // Decrypt and load thumbnail sidecar securely on-the-fly, avoiding raw file loading to prevent OOM
            Image(
                painter = painter,
                contentDescription = item.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback placeholder (Never touches raw original file to prevent OOM)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                    contentDescription = "Placeholder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Overlay status indicators
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video file",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
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

    // Check if it's today
    if (now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)) {
        return "Today"
    }

    // Check if it's yesterday
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
        yesterday.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)) {
        return "Yesterday"
    }

    return format.format(date)
}