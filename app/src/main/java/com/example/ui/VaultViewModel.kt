package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class VaultItem(
    val originalName: String,
    val encryptedName: String,
    val uri: Uri,
    val isVideo: Boolean,
    val isLivePhoto: Boolean = false,
    val size: Long,
    val dateTaken: Long
)

/**
 * Yêu cầu hiển thị UI cho phép người dùng tự chọn frame bất kỳ của video
 * để làm ảnh bìa (tạo cả Tier SCREEN 1080p và Tier THUMB 216p) khi import.
 */
data class VideoFramePickRequest(
    val uri: Uri,
    val fileName: String,
    val queueIndex: Int,
    val queueTotal: Int
)

private data class PendingImportItem(
    val index: Int,
    val uri: Uri,
    val originalName: String,
    val isVideo: Boolean,
    val companionVideoUri: Uri? = null,
    val companionVideoSize: Long = 0L
)

private class PrecomputedTiers(
    val thumbBytes: ByteArray,
    val screenBytes: ByteArray,
    val dimensions: Pair<Int, Int>
)

class VaultViewModel : ViewModel() {

    companion object {
        const val THUMB_SIZE_PX = 216
    }

    // Danh sách các thư mục Vault đã ghi nhớ ở trang chủ
    private val _savedVaults = MutableStateFlow<List<Uri>>(emptyList())
    val savedVaults: StateFlow<List<Uri>> = _savedVaults.asStateFlow()

    // Vault đang được người dùng click chọn để nhập password / mở khóa
    private val _vaultUri = MutableStateFlow<Uri?>(null)
    val vaultUri: StateFlow<Uri?> = _vaultUri.asStateFlow()

    private val _dek = MutableStateFlow<ByteArray?>(null)
    val dek: StateFlow<ByteArray?> = _dek.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _importQueueTotal = MutableStateFlow(0)
    val importQueueTotal: StateFlow<Int> = _importQueueTotal.asStateFlow()

    private val _importQueueIndex = MutableStateFlow(0)
    val importQueueIndex: StateFlow<Int> = _importQueueIndex.asStateFlow()

    // Bitmap preview kích thước thật (216x216 px) và cờ báo loại file (video / Live Photo) đang được mã hoá
    private val _currentImportThumb = MutableStateFlow<Bitmap?>(null)
    val currentImportThumb: StateFlow<Bitmap?> = _currentImportThumb.asStateFlow()

    private val _currentImportIsVideo = MutableStateFlow(false)
    val currentImportIsVideo: StateFlow<Boolean> = _currentImportIsVideo.asStateFlow()

    private val _currentImportIsLivePhoto = MutableStateFlow(false)
    val currentImportIsLivePhoto: StateFlow<Boolean> = _currentImportIsLivePhoto.asStateFlow()

    // Trạng thái đang huỷ import & dọn dẹp các file đã lỡ tạo
    private val _isCancellingImport = MutableStateFlow(false)
    val isCancellingImport: StateFlow<Boolean> = _isCancellingImport.asStateFlow()

    private var importJob: Job? = null
    private val isImportCancelled = AtomicBoolean(false)

    private val _videoFramePickRequest = MutableStateFlow<VideoFramePickRequest?>(null)
    val videoFramePickRequest: StateFlow<VideoFramePickRequest?> = _videoFramePickRequest.asStateFlow()
    private var videoFrameDeferred: CompletableDeferred<Bitmap?>? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedMedia = MutableStateFlow<VaultItem?>(null)
    val selectedMedia: StateFlow<VaultItem?> = _selectedMedia.asStateFlow()

    private val _clickedItemBounds = MutableStateFlow<FloatArray?>(null)
    val clickedItemBounds: StateFlow<FloatArray?> = _clickedItemBounds.asStateFlow()

    private val _thumbnailBounds = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    val thumbnailBounds: StateFlow<Map<String, FloatArray>> = _thumbnailBounds.asStateFlow()

    private val _animatingItem = MutableStateFlow<String?>(null)
    val animatingItem: StateFlow<String?> = _animatingItem.asStateFlow()

    fun updateThumbnailBounds(encryptedName: String, bounds: FloatArray) {
        _thumbnailBounds.value = _thumbnailBounds.value + (encryptedName to bounds)
    }

    fun setAnimatingItem(encryptedName: String?) {
        _animatingItem.value = encryptedName
    }

    /**
     * Được gọi từ VideoFramePickerDialog khi người dùng đã chọn xong frame ưng ý.
     */
    fun confirmVideoFrameSelection(chosenBitmap: Bitmap?) {
        videoFrameDeferred?.complete(chosenBitmap)
        videoFrameDeferred = null
    }

    /**
     * Huỷ toàn bộ quá trình import bất cứ lúc nào (cả khi đang chọn thumb video lẫn khi đang mã hoá)
     * và xoá sạch toàn bộ các file đã lỡ mã hoá trong đợt import hiện tại.
     */
    fun cancelImport() {
        if (!_isLoading.value && _videoFramePickRequest.value == null) return
        isImportCancelled.set(true)
        _isCancellingImport.value = true
        _videoFramePickRequest.value = null
        videoFrameDeferred?.cancel(CancellationException("Import cancelled by user"))
        videoFrameDeferred = null
        importJob?.cancel(CancellationException("Import cancelled by user"))
    }

    /**
     * Tải danh sách các Vault đã ghi nhớ từ SharedPreferences khi mở app.
     */
    fun loadSavedVaultUri(context: Context) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val persistedSet = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

        val loadedUris = mutableListOf<Uri>()
        val jsonStr = prefs.getString("saved_vault_uris_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val uStr = arr.optString(i)
                    if (uStr.isNotEmpty() && uStr in persistedSet) {
                        val parsed = Uri.parse(uStr)
                        if (parsed !in loadedUris) loadedUris.add(parsed)
                    }
                }
            } catch (_: Exception) {}
        }

        val legacyUriStr = prefs.getString("saved_vault_uri", null)
        if (!legacyUriStr.isNullOrEmpty() && legacyUriStr in persistedSet) {
            try {
                val legacyUri = Uri.parse(legacyUriStr)
                if (legacyUri !in loadedUris) {
                    loadedUris.add(0, legacyUri)
                }
            } catch (_: Exception) {}
        }

        _savedVaults.value = loadedUris
        saveVaultListToPrefs(context, loadedUris)
    }

    private fun saveVaultListToPrefs(context: Context, list: List<Uri>) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        list.forEach { arr.put(it.toString()) }
        prefs.edit()
            .putString("saved_vault_uris_json", arr.toString())
            .apply()
    }

    fun setVaultUri(context: Context, uri: Uri?) {
        _dek.value?.fill(0)
        _dek.value = null
        _items.value = emptyList()
        _error.value = null

        if (uri == null) {
            _vaultUri.value = null
            return
        }

        val currentList = _savedVaults.value.toMutableList()
        currentList.remove(uri)
        currentList.add(0, uri)
        _savedVaults.value = currentList
        saveVaultListToPrefs(context, currentList)

        _vaultUri.value = uri
    }

    fun removeSavedVault(context: Context, uri: Uri) {
        val updated = _savedVaults.value.filterNot { it == uri }
        _savedVaults.value = updated
        saveVaultListToPrefs(context, updated)
        if (_vaultUri.value == uri) {
            _vaultUri.value = null
            _error.value = null
        }
    }

    fun setError(message: String?) {
        _error.value = message
    }

    fun clearError() { _error.value = null }

    fun selectMedia(item: VaultItem?, bounds: FloatArray? = null) {
        _clickedItemBounds.value = bounds; _selectedMedia.value = item
    }

    /**
     * Mở khoá Vault và quét sẵn toàn bộ danh sách file TRƯỚC KHI gán _dek.value.
     * Nhờ đó ngay khoảnh khắc đầu tiên chuyển sang GalleryScreen, _items.value đã có đầy đủ dữ liệu,
     * triệt tiêu hoàn toàn lỗi nháy thông báo "Your Secure Vault is Empty" khi mới mở khoá.
     */
    fun unlockVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val (unlockedDek, initialItems) = withContext(Dispatchers.IO) {
                    val key = CryptoEngine.unlockVault(context, uri, password)
                    val scanned = scanFiles(context, uri, key)
                    key to scanned
                }
                _items.value = initialItems
                _dek.value = unlockedDek
            } catch (e: Exception) {
                _error.value = "Incorrect password."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val (newDek, initialItems) = withContext(Dispatchers.IO) {
                    val key = CryptoEngine.initVault(context, uri, password)
                    val scanned = scanFiles(context, uri, key)
                    key to scanned
                }
                _items.value = initialItems
                _dek.value = newDek
            } catch (e: Exception) {
                _error.value = "Failed to create Vault"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun lockVault() {
        cancelImport()
        _dek.value?.fill(0)
        _dek.value = null
        _vaultUri.value = null
        _items.value = emptyList()
        _selectedMedia.value = null
        _error.value = null
    }

    fun loadVaultItems(context: Context) {
        val uri = _vaultUri.value ?: return
        val key = _dek.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _items.value = withContext(Dispatchers.IO) { scanFiles(context, uri, key) }
            } catch (e: Exception) {
                _error.value = "Failed to load: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun scanFiles(context: Context, rootUri: Uri, key: ByteArray): List<VaultItem> {
        val result = mutableListOf<VaultItem>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri))
        val resolver = context.contentResolver
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val displayName = cursor.getString(1)
                val size = cursor.getLong(2)

                if (displayName.endsWith(".enc") && displayName != "vault.conf") {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
                    val decryptedMeta = CryptoEngine.decryptFileName(displayName, key) ?: continue

                    val parts = decryptedMeta.split('|', limit = 3)
                    val dateTaken: Long
                    val isLiveTagged: Boolean
                    val decryptedName: String

                    if (parts.size == 3 && parts[1] == "LIVE") {
                        dateTaken = parts[0].toLongOrNull() ?: 0L
                        isLiveTagged = true
                        decryptedName = parts[2]
                    } else if (parts.size >= 2) {
                        dateTaken = parts[0].toLongOrNull() ?: 0L
                        isLiveTagged = false
                        decryptedName = decryptedMeta.substringAfter('|')
                    } else {
                        dateTaken = 0L
                        isLiveTagged = false
                        decryptedName = decryptedMeta
                    }

                    val isVideo = isVideoFile(decryptedName)
                    val isLivePhoto = isLiveTagged || (!isVideo && (CryptoEngine.readSgv2FileInfo(context, fileUri, key)?.isLivePhoto == true))

                    result.add(
                        VaultItem(
                            originalName = decryptedName,
                            encryptedName = displayName,
                            uri = fileUri,
                            isVideo = isVideo,
                            isLivePhoto = isLivePhoto,
                            size = size,
                            dateTaken = dateTaken
                        )
                    )
                }
            }
        }
        return result.sortedByDescending { it.dateTaken }
    }

    /** Import 1 file duy nhất - giữ lại để tương thích ngược, chỉ là wrapper gọi importMultipleMedia. */
    fun importMedia(context: Context, sourceUri: Uri) {
        importMultipleMedia(context, listOf(sourceUri))
    }

    /**
     * Bulk Import bằng cách chọn 1 Folder:
     * Quét đệ quy toàn bộ các file ảnh & video hợp lệ bên trong thư mục được chọn,
     * sau đó chuyển thẳng vào hàng đợi importMultipleMedia.
     */
    fun importFolderMedia(context: Context, folderTreeUri: Uri) {
        if (_vaultUri.value == null || _dek.value == null) return

        importJob?.cancel()
        isImportCancelled.set(false)
        _isCancellingImport.value = false

        importJob = viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _error.value = null

            val discoveredUris = try {
                withContext(Dispatchers.IO) {
                    collectMediaUrisFromTree(context, folderTreeUri)
                }
            } catch (e: CancellationException) {
                _isLoading.value = false
                _isCancellingImport.value = false
                return@launch
            } catch (e: Exception) {
                _isLoading.value = false
                _isCancellingImport.value = false
                _error.value = "Failed to scan folder: ${e.message}"
                return@launch
            }

            if (isImportCancelled.get()) {
                _isLoading.value = false
                _isCancellingImport.value = false
                return@launch
            }

            if (discoveredUris.isEmpty()) {
                _isLoading.value = false
                _isCancellingImport.value = false
                _error.value = "No photos or videos found in the selected folder."
                return@launch
            }

            runImportPipeline(context, discoveredUris)
        }
    }

    private fun collectMediaUrisFromTree(context: Context, treeUri: Uri): List<Uri> {
        val result = mutableListOf<Uri>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

        fun traverseDirectory(parentDocId: String) {
            if (isImportCancelled.get()) throw CancellationException("Import cancelled")
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")
                    val docId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: ""
                    val mimeType = cursor.getString(2) ?: ""

                    if (displayName.startsWith(".") || displayName == "vault.conf" || displayName.endsWith(".enc")) {
                        continue
                    }

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        traverseDirectory(docId)
                    } else if (isSupportedMediaFile(displayName, mimeType)) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        result.add(fileUri)
                    }
                }
            }
        }

        traverseDirectory(rootDocId)
        return result
    }

    private fun isSupportedMediaFile(fileName: String, mimeType: String): Boolean {
        if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "gif", "bmp", "dng")
        return ext in imageExts || isVideoFile(fileName)
    }

    /**
     * Import nhiều file cùng lúc.
     */
    fun importMultipleMedia(context: Context, sourceUris: List<Uri>) {
        if (_vaultUri.value == null || _dek.value == null || sourceUris.isEmpty()) return

        importJob?.cancel()
        isImportCancelled.set(false)
        _isCancellingImport.value = false

        importJob = viewModelScope.launch {
            runImportPipeline(context, sourceUris)
        }
    }

    /**
     * Ghép cặp tự động các Live Photo dạng 2 file rời (ví dụ iOS/macOS: IMG_1234.HEIC/JPG + IMG_1234.MOV/MP4)
     * nếu được chọn cùng nhau và file video có thời lượng ngắn (<= 6 giây).
     */
    private fun buildPendingImportItems(context: Context, sourceUris: List<Uri>): List<PendingImportItem> {
        data class RawEntry(val uri: Uri, val name: String, val isVideo: Boolean, val baseKey: String)

        val rawList = sourceUris.mapIndexed { idx, uri ->
            if (isImportCancelled.get()) throw CancellationException("Import cancelled")
            val name = getFileNameFromUri(context, uri) ?: "import_${System.currentTimeMillis()}_$idx"
            val isVid = isVideoFile(name)
            val baseKey = name.substringBeforeLast('.').lowercase(Locale.ROOT)
            RawEntry(uri, name, isVid, baseKey)
        }

        val imageBaseKeys = rawList.filter { !it.isVideo }.map { it.baseKey }.toSet()
        val pairedVideoByBase = mutableMapOf<String, Pair<Uri, Long>>()

        rawList.filter { it.isVideo && it.baseKey in imageBaseKeys }.forEach { vidEntry ->
            val ext = vidEntry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext == "mov" || ext == "mp4") {
                val vidSize = getFileSizeFromUri(context, vidEntry.uri)
                val durationMs = getVideoDurationMs(context, vidEntry.uri)
                if (vidSize > 0L && durationMs in 100L..6500L) {
                    pairedVideoByBase[vidEntry.baseKey] = vidEntry.uri to vidSize
                }
            }
        }

        val consumedVideoUris = pairedVideoByBase.values.map { it.first }.toSet()
        val finalItems = mutableListOf<PendingImportItem>()

        rawList.forEach { entry ->
            if (entry.uri in consumedVideoUris) return@forEach
            val companion = if (!entry.isVideo) pairedVideoByBase[entry.baseKey] else null
            finalItems.add(
                PendingImportItem(
                    index = finalItems.size,
                    uri = entry.uri,
                    originalName = entry.name,
                    isVideo = entry.isVideo,
                    companionVideoUri = companion?.first,
                    companionVideoSize = companion?.second ?: 0L
                )
            )
        }

        return finalItems
    }

    private fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Pipeline import 3 giai đoạn có hỗ trợ Live Photo, Cancel & Rollback.
     */
    private suspend fun runImportPipeline(context: Context, sourceUris: List<Uri>) {
        val vaultUriVal = _vaultUri.value ?: return
        val key = _dek.value ?: return
        if (sourceUris.isEmpty()) return

        _isLoading.value = true
        _importProgress.value = 0f
        _importQueueTotal.value = sourceUris.size
        _importQueueIndex.value = 0
        _currentImportThumb.value = null
        _currentImportIsVideo.value = false
        _currentImportIsLivePhoto.value = false
        _error.value = null

        val failedNames = mutableListOf<String>()
        val createdVaultUris = Collections.synchronizedList(mutableListOf<Uri>())

        try {
            val pendingItems = withContext(Dispatchers.IO) {
                buildPendingImportItems(context, sourceUris)
            }
            val totalFiles = pendingItems.size.coerceAtLeast(1)
            _importQueueTotal.value = totalFiles

            // GIAI ĐOẠN 1: Chọn thumbnail cho TOÀN BỘ video ngay bước đầu
            val videoItems = pendingItems.filter { it.isVideo }
            val precomputedVideoTiers = mutableMapOf<Int, PrecomputedTiers>()

            if (videoItems.isNotEmpty()) {
                videoItems.forEachIndexed { videoIdx, videoItem ->
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")

                    val deferred = CompletableDeferred<Bitmap?>()
                    videoFrameDeferred = deferred
                    _videoFramePickRequest.value = VideoFramePickRequest(
                        uri = videoItem.uri,
                        fileName = videoItem.originalName,
                        queueIndex = videoIdx + 1,
                        queueTotal = videoItems.size
                    )

                    val chosenBitmap = deferred.await()
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")

                    val tiers = withContext(Dispatchers.IO) {
                        val (thumbBytes, screenBytes, dimensions) = generateTierBitmaps(
                            context = context,
                            uri = videoItem.uri,
                            isVideo = true,
                            chosenVideoFrame = chosenBitmap,
                            recycleInputBitmap = false
                        )
                        PrecomputedTiers(thumbBytes, screenBytes, dimensions)
                    }
                    precomputedVideoTiers[videoItem.index] = tiers
                }
                _videoFramePickRequest.value = null
            }

            // GIAI ĐOẠN 2: Mã hoá liên tục toàn bộ danh sách file
            withContext(Dispatchers.IO) {
                pendingItems.forEachIndexed { index, item ->
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")
                    _importQueueIndex.value = index + 1
                    try {
                        importSingleFile(
                            context = context,
                            vaultUriVal = vaultUriVal,
                            key = key,
                            item = item,
                            precomputedTiers = precomputedVideoTiers[item.index],
                            createdVaultUris = createdVaultUris
                        ) { fileProgress ->
                            if (isImportCancelled.get()) {
                                throw CancellationException("Import cancelled")
                            }
                            _importProgress.value = (index + fileProgress) / totalFiles
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedNames.add(item.originalName)
                        _importProgress.value = (index + 1f) / totalFiles
                    }
                }
            }

            if (isImportCancelled.get()) throw CancellationException("Import cancelled")

            _items.value = withContext(Dispatchers.IO) { scanFiles(context, vaultUriVal, key) }

            if (failedNames.isNotEmpty()) {
                _error.value = if (failedNames.size == 1) {
                    "Failed to import: ${failedNames.first()}"
                } else {
                    "Failed to import ${failedNames.size} of $totalFiles files"
                }
            }

            // GIAI ĐOẠN 3: Rung 3 cái dài báo hiệu hoàn tất
            vibrateThreeTimes(context)
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                val snapshot = synchronized(createdVaultUris) { createdVaultUris.toList() }
                snapshot.forEach { uriToDelete ->
                    CryptoEngine.deleteFile(context, uriToDelete)
                }
                _items.value = scanFiles(context, vaultUriVal, key)
            }
        } catch (e: Exception) {
            if (isImportCancelled.get()) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val snapshot = synchronized(createdVaultUris) { createdVaultUris.toList() }
                    snapshot.forEach { uriToDelete ->
                        CryptoEngine.deleteFile(context, uriToDelete)
                    }
                    _items.value = scanFiles(context, vaultUriVal, key)
                }
            } else {
                _error.value = "Import failed: ${e.message}"
            }
        } finally {
            withContext(NonCancellable) {
                _videoFramePickRequest.value = null
                videoFrameDeferred = null
                _currentImportThumb.value = null
                _currentImportIsVideo.value = false
                _currentImportIsLivePhoto.value = false
                _isCancellingImport.value = false
                isImportCancelled.set(false)
                _isLoading.value = false
                _importProgress.value = 0f
                _importQueueTotal.value = 0
                _importQueueIndex.value = 0
            }
        }
    }

    private fun importSingleFile(
        context: Context,
        vaultUriVal: Uri,
        key: ByteArray,
        item: PendingImportItem,
        precomputedTiers: PrecomputedTiers? = null,
        createdVaultUris: MutableList<Uri>,
        onProgress: (Float) -> Unit
    ) {
        if (isImportCancelled.get()) throw CancellationException("Import cancelled")

        val sourceUri = item.uri
        val originalName = item.originalName
        val isVideo = item.isVideo
        val sourceSize = getFileSizeFromUri(context, sourceUri)

        val embeddedMotion = if (!isVideo && item.companionVideoUri == null && sourceSize > 0L) {
            detectEmbeddedMotionVideo(context, sourceUri, sourceSize)
        } else null

        val isLivePhoto = !isVideo && (item.companionVideoUri != null || embeddedMotion != null)
        val liveVideoOffset = embeddedMotion?.first ?: 0L
        val liveVideoSize = embeddedMotion?.second ?: 0L

        val (thumbBytes, screenBytes, dimensions) = if (precomputedTiers != null) {
            Triple(precomputedTiers.thumbBytes, precomputedTiers.screenBytes, precomputedTiers.dimensions)
        } else {
            generateTierBitmaps(
                context = context,
                uri = sourceUri,
                isVideo = isVideo,
                chosenVideoFrame = null
            )
        }

        val rawThumbBmp = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
        if (rawThumbBmp != null) {
            _currentImportIsVideo.value = isVideo
            _currentImportIsLivePhoto.value = isLivePhoto
            val cropped = cropCenterSquareBitmap(rawThumbBmp, THUMB_SIZE_PX)
            _currentImportThumb.value = cropped
        }

        if (isImportCancelled.get()) throw CancellationException("Import cancelled")

        val dateTaken = getActualDateTaken(context, sourceUri, isVideo)
        val combinedMetadata = if (isLivePhoto) {
            "$dateTaken|LIVE|$originalName"
        } else {
            "$dateTaken|$originalName"
        }
        val encryptedName = CryptoEngine.encryptFileName(combinedMetadata, key)

        val encryptedFileUri = CryptoEngine.createChildFile(context, vaultUriVal, encryptedName)
            ?: throw java.io.IOException("Could not create SGV2 file in Vault")

        createdVaultUris.add(encryptedFileUri)

        try {
            CryptoEngine.encryptSgv2File(
                context = context,
                sourceUri = sourceUri,
                destUri = encryptedFileUri,
                thumbBytes = thumbBytes,
                screenBytes = screenBytes,
                dek = key,
                sourceSize = if (isLivePhoto && liveVideoOffset > 0L) liveVideoOffset else sourceSize,
                mediaWidth = dimensions.first,
                mediaHeight = dimensions.second,
                liveVideoOffset = liveVideoOffset,
                liveVideoSize = liveVideoSize,
                companionVideoUri = item.companionVideoUri,
                companionVideoSize = item.companionVideoSize,
                onProgress = { progress -> onProgress(progress) }
            )
        } catch (e: Exception) {
            if (!isImportCancelled.get()) {
                CryptoEngine.deleteFile(context, encryptedFileUri)
                createdVaultUris.remove(encryptedFileUri)
            }
            throw e
        }
    }

    private fun detectEmbeddedMotionVideo(context: Context, uri: Uri, totalFileSize: Long): Pair<Long, Long>? {
        if (totalFileSize < 16384L) return null
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { fis ->
                val channel = fis.channel

                val headSize = minOf(65536L, totalFileSize).toInt()
                val headBuf = ByteBuffer.allocate(headSize)
                channel.read(headBuf, 0L)
                val headStr = String(headBuf.array(), Charsets.ISO_8859_1)

                val xmpOffsetRegexes = listOf(
                    Regex("""MicroVideoOffset\s*[=:]\s*"(\d+)""""),
                    Regex("""<GCamera:MicroVideoOffset>(\d+)</GCamera:MicroVideoOffset>"""),
                    Regex("""Item:Semantic="MotionPhoto"[^>]*Item:Length="(\d+)""""),
                    Regex("""Item:Length="(\d+)"[^>]*Item:Semantic="MotionPhoto"""")
                )

                for (regex in xmpOffsetRegexes) {
                    val match = regex.find(headStr)
                    val videoLen = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: continue
                    if (videoLen in 4096L..(totalFileSize - 1024L)) {
                        val candidateOffset = totalFileSize - videoLen
                        if (isMp4HeaderAt(channel, candidateOffset, totalFileSize)) {
                            return candidateOffset to videoLen
                        }
                    }
                }

                val maxTailScan = minOf(24L * 1024L * 1024L, totalFileSize - 1024L)
                if (maxTailScan <= 4096L) return null

                val scanStart = (totalFileSize - maxTailScan).coerceAtLeast(1024L)
                val chunkSize = 256 * 1024
                val overlap = 32
                val chunkBuf = ByteArray(chunkSize + overlap)
                var pos = scanStart

                val samsungMarker = "MotionPhoto_Data".toByteArray(Charsets.US_ASCII)
                val validVideoBrands = setOf("mp41", "mp42", "isom", "iso2", "avc1", "qt  ", "3gp4", "3gp5", "mmp4")

                while (pos < totalFileSize - 16) {
                    val toRead = minOf(chunkBuf.size.toLong(), totalFileSize - pos).toInt()
                    val bb = ByteBuffer.wrap(chunkBuf, 0, toRead)
                    var readBytes = 0
                    while (readBytes < toRead) {
                        val n = channel.read(bb, pos + readBytes)
                        if (n <= 0) break
                        readBytes += n
                    }
                    if (readBytes < 16) break

                    var i = 0
                    while (i <= readBytes - 16) {
                        if (chunkBuf[i] == samsungMarker[0] &&
                            chunkBuf[i + 1] == samsungMarker[1] &&
                            chunkBuf[i + 2] == samsungMarker[2] &&
                            i + 24 <= readBytes
                        ) {
                            var matchSamsung = true
                            for (k in samsungMarker.indices) {
                                if (chunkBuf[i + k] != samsungMarker[k]) {
                                    matchSamsung = false
                                    break
                                }
                            }
                            if (matchSamsung) {
                                val mp4Start = pos + i + samsungMarker.size
                                if (isMp4HeaderAt(channel, mp4Start, totalFileSize)) {
                                    return mp4Start to (totalFileSize - mp4Start)
                                }
                            }
                        }

                        if (chunkBuf[i + 4] == 'f'.code.toByte() &&
                            chunkBuf[i + 5] == 't'.code.toByte() &&
                            chunkBuf[i + 6] == 'y'.code.toByte() &&
                            chunkBuf[i + 7] == 'p'.code.toByte()
                        ) {
                            val boxSize = ((chunkBuf[i].toInt() and 0xFF) shl 24) or
                                    ((chunkBuf[i + 1].toInt() and 0xFF) shl 16) or
                                    ((chunkBuf[i + 2].toInt() and 0xFF) shl 8) or
                                    (chunkBuf[i + 3].toInt() and 0xFF)
                            val brand = String(chunkBuf, i + 8, 4, Charsets.US_ASCII)
                            if (boxSize in 16..128 && brand in validVideoBrands) {
                                val mp4Start = pos + i
                                val mp4Len = totalFileSize - mp4Start
                                if (mp4Start >= 1024L && mp4Len >= 4096L) {
                                    return mp4Start to mp4Len
                                }
                            }
                        }
                        i++
                    }

                    pos += chunkSize
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isMp4HeaderAt(channel: java.nio.channels.FileChannel, offset: Long, totalSize: Long): Boolean {
        if (offset < 0L || offset + 12L > totalSize) return false
        val buf = ByteBuffer.allocate(12)
        var read = 0
        while (read < 12) {
            val n = channel.read(buf, offset + read)
            if (n <= 0) break
            read += n
        }
        if (read < 12) return false
        val arr = buf.array()
        return arr[4] == 'f'.code.toByte() &&
                arr[5] == 't'.code.toByte() &&
                arr[6] == 'y'.code.toByte() &&
                arr[7] == 'p'.code.toByte()
    }

    private fun cropCenterSquareBitmap(source: Bitmap, targetSizePx: Int): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val edge = minOf(w, h)
        val offsetX = (w - edge) / 2
        val offsetY = (h - edge) / 2
        val cropped = Bitmap.createBitmap(source, offsetX, offsetY, edge, edge)
        if (cropped.width == targetSizePx && cropped.height == targetSizePx) {
            return cropped
        }
        val scaled = Bitmap.createScaledBitmap(cropped, targetSizePx, targetSizePx, true)
        if (scaled !== cropped && cropped !== source) {
            cropped.recycle()
        }
        return scaled
    }

    private fun vibrateThreeTimes(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            val timings = longArrayOf(0L, 380L, 160L, 380L, 160L, 450L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                        .build()
                    vibrator.vibrate(effect, attrs)
                } else {
                    vibrator.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateTierBitmaps(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        chosenVideoFrame: Bitmap? = null,
        recycleInputBitmap: Boolean = true
    ): Triple<ByteArray, ByteArray, Pair<Int, Int>> {
        var origW = 0
        var origH = 0

        val baseBmp: Bitmap = if (isVideo) {
            if (chosenVideoFrame != null) {
                origW = chosenVideoFrame.width
                origH = chosenVideoFrame.height
                chosenVideoFrame
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    origW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    origH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            origW = options.outWidth.coerceAtLeast(0)
            origH = options.outHeight.coerceAtLeast(0)

            val exifRotation = getExifRotationDegrees(context, uri)
            if (exifRotation == 90 || exifRotation == 270) {
                val tmp = origW
                origW = origH
                origH = tmp
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(options, 2048, 2048)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            if (exifRotation != 0) {
                val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        }

        val bw = baseBmp.width.coerceAtLeast(1)
        val bh = baseBmp.height.coerceAtLeast(1)
        if (origW <= 0 || origH <= 0) {
            origW = bw
            origH = bh
        }

        val screenW = minOf(1080, bw)
        val screenH = (bh * (screenW.toFloat() / bw)).toInt().coerceAtLeast(1)
        val screenBmp = Bitmap.createScaledBitmap(baseBmp, screenW, screenH, true)

        val thumbBmp = createSmoothThumbnail(screenBmp, THUMB_SIZE_PX)

        val compressFormat = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        val targetQuality = 75

        val thumbBaos = ByteArrayOutputStream()
        thumbBmp.compress(compressFormat, targetQuality, thumbBaos)

        val screenBaos = ByteArrayOutputStream()
        screenBmp.compress(compressFormat, targetQuality, screenBaos)

        if (baseBmp != screenBmp && (recycleInputBitmap || baseBmp !== chosenVideoFrame)) {
            baseBmp.recycle()
        }
        if (thumbBmp != screenBmp) thumbBmp.recycle()
        if (screenBmp !== chosenVideoFrame || recycleInputBitmap) {
            screenBmp.recycle()
        }

        return Triple(thumbBaos.toByteArray(), screenBaos.toByteArray(), Pair(origW, origH))
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun createSmoothThumbnail(source: Bitmap, targetMinEdge: Int): Bitmap {
        var current = source
        while (true) {
            val cw = current.width
            val ch = current.height

            if (cw <= targetMinEdge * 2 || ch <= targetMinEdge * 2) {
                break
            }

            val nextBmp = Bitmap.createScaledBitmap(current, cw / 2, ch / 2, true)

            if (current != source) {
                current.recycle()
            }
            current = nextBmp
        }

        val scale = targetMinEdge.toFloat() / minOf(current.width, current.height)
        val finalW = (current.width * scale).toInt().coerceAtLeast(1)
        val finalH = (current.height * scale).toInt().coerceAtLeast(1)

        val result = Bitmap.createScaledBitmap(current, finalW, finalH, true)

        if (current != source) {
            current.recycle()
        }

        return result
    }

    fun deleteMedia(context: Context, item: VaultItem) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                withContext(Dispatchers.IO) { CryptoEngine.deleteFile(context, item.uri) }
                _selectedMedia.value = null; loadVaultItems(context)
            } catch (e: Exception) {} finally { _isLoading.value = false }
        }
    }

    fun deleteMultipleMedia(context: Context, itemsToDelete: List<VaultItem>, onFinished: (() -> Unit)? = null) {
        if (itemsToDelete.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    itemsToDelete.forEach { item ->
                        CryptoEngine.deleteFile(context, item.uri)
                    }
                }
                _selectedMedia.value = null
                loadVaultItems(context)
                onFinished?.invoke()
            } catch (e: Exception) {
                _error.value = "Failed to delete selected files: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun isVideoFile(fileName: String) = fileName.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "m4v")

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        } catch (e: Exception) {}
        if (name == null) name = uri.lastPathSegment
        return name
    }

    private fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var size = -1L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1 && !cursor.isNull(idx)) size = cursor.getLong(idx)
                }
            }
        } catch (e: Exception) {}
        if (size <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length >= 0) size = afd.length
                }
            } catch (e: Exception) {}
        }
        return size
    }

    private fun getActualDateTaken(context: Context, uri: Uri, isVideo: Boolean): Long {
        try {
            context.contentResolver.query(uri, arrayOf("datetaken"), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("datetaken")
                    if (idx != -1) {
                        val dt = it.getLong(idx)
                        if (dt > 0L) return dt
                    }
                }
            }
        } catch (e: Exception) {}

        try {
            if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    if (!dateStr.isNullOrEmpty()) {
                        val formats = arrayOf(
                            SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
                            SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        )
                        for (format in formats) {
                            try {
                                val parsed = format.parse(dateStr)
                                if (parsed != null) return parsed.time
                            } catch (e: Exception) {}
                        }
                    }
                } finally {
                    retriever.release()
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        val exif = ExifInterface(inputStream)
                        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                        if (!dateTime.isNullOrEmpty()) {
                            try {
                                val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                                val parsed = format.parse(dateTime)
                                if (parsed != null) return parsed.time
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            context.contentResolver.query(uri, arrayOf("date_modified"), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("date_modified")
                    if (idx != -1) {
                        val modified = it.getLong(idx)
                        if (modified > 0L) return modified * 1000L
                    }
                }
            }
        } catch (e: Exception) {}

        return System.currentTimeMillis()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (options.outHeight > reqH || options.outWidth > reqW) {
            val halfH = options.outHeight / 2
            val halfW = options.outWidth / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) inSampleSize *= 2
        }
        return inSampleSize
    }
}