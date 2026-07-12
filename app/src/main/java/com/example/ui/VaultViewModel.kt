package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultItem(
    val originalName: String,
    val encryptedName: String,
    val uri: Uri,
    val isVideo: Boolean,
    val size: Long,
    val dateTaken: Long
)

class VaultViewModel : ViewModel() {

    private val _vaultUri = MutableStateFlow<Uri?>(null)
    val vaultUri: StateFlow<Uri?> = _vaultUri.asStateFlow()

    private val _dek = MutableStateFlow<ByteArray?>(null)
    val dek: StateFlow<ByteArray?> = _dek.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    fun loadSavedVaultUri(context: Context) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("saved_vault_uri", null)
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                val hasPermission = context.contentResolver.persistedUriPermissions.any { it.uri == uri }
                if (hasPermission) _vaultUri.value = uri
            } catch (e: Exception) {}
        }
    }

    fun setVaultUri(context: Context, uri: Uri?) {
        _vaultUri.value = uri; _dek.value = null; _items.value = emptyList(); _error.value = null
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        if (uri != null) prefs.edit().putString("saved_vault_uri", uri.toString()).apply()
        else prefs.edit().remove("saved_vault_uri").apply()
    }

    fun clearError() { _error.value = null }

    fun selectMedia(item: VaultItem?, bounds: FloatArray? = null) {
        _clickedItemBounds.value = bounds; _selectedMedia.value = item
    }

    fun unlockVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                _dek.value = withContext(Dispatchers.IO) { CryptoEngine.unlockVault(context, uri, password) }
                loadVaultItems(context)
            } catch (e: Exception) { _error.value = "Incorrect password." } finally { _isLoading.value = false }
        }
    }

    fun createVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                _dek.value = withContext(Dispatchers.IO) { CryptoEngine.initVault(context, uri, password) }
                loadVaultItems(context)
            } catch (e: Exception) { _error.value = "Failed to create Vault" } finally { _isLoading.value = false }
        }
    }

    fun lockVault() { _dek.value = null; _items.value = emptyList(); _selectedMedia.value = null }

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
                    val parts = decryptedMeta.split('|', limit = 2)
                    val dateTaken = if (parts.size == 2) parts[0].toLongOrNull() ?: 0L else 0L
                    val decryptedName = if (parts.size == 2) parts[1] else decryptedMeta

                    result.add(
                        VaultItem(
                            originalName = decryptedName,
                            encryptedName = displayName,
                            uri = fileUri,
                            isVideo = isVideoFile(decryptedName),
                            size = size,
                            dateTaken = dateTaken
                        )
                    )
                }
            }
        }
        return result.sortedByDescending { it.dateTaken }
    }

    fun importMedia(context: Context, sourceUri: Uri) {
        val vaultUriVal = _vaultUri.value ?: return
        val key = _dek.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    val originalName = getFileNameFromUri(context, sourceUri) ?: "import_${System.currentTimeMillis()}"
                    val isVideo = isVideoFile(originalName)

                    // Sử dụng hàm getActualDateTaken đọc thẳng từ EXIF/Video Metadata
                    val dateTaken = getActualDateTaken(context, sourceUri, isVideo)

                    val combinedMetadata = "$dateTaken|$originalName"
                    val encryptedName = CryptoEngine.encryptFileName(combinedMetadata, key)

                    val encryptedFileUri = CryptoEngine.createChildFile(context, vaultUriVal, encryptedName)
                        ?: throw java.io.IOException("Could not create SGV2 file in Vault")

                    // Khởi tạo 2 tier resize (Thumbnail & Screen)
                    val (thumbBytes, screenBytes) = generateTierBitmaps(context, sourceUri, isVideo)

                    // Lưu toàn bộ 3 Tier gộp 1 File
                    CryptoEngine.encryptSgv2File(
                        context = context,
                        sourceUri = sourceUri,
                        destUri = encryptedFileUri,
                        thumbBytes = thumbBytes,
                        screenBytes = screenBytes,
                        dek = key
                    )
                }
                loadVaultItems(context)
            } catch (e: Exception) {
                _error.value = "Import failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Trong file VaultViewModel.kt
    private fun generateTierBitmaps(context: Context, uri: Uri, isVideo: Boolean): Pair<ByteArray, ByteArray> {
        val baseBmp = if (isVideo) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(options, 2048, 2048)
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
                ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val bw = baseBmp.width
        val bh = baseBmp.height

        // 1. TẠO TIER SCREEN (Giữ nguyên 1080)
        val screenScale = if (bw > 0) 1080f / bw else 1f
        val screenW = minOf(1080, bw)
        val screenH = (bh * (screenW.toFloat() / bw)).toInt().coerceAtLeast(1)
        val screenBmp = Bitmap.createScaledBitmap(baseBmp, screenW, screenH, true)

        // 2. TẠO TIER THUMB (Giữ nguyên 216)
        val thumbBmp = createSmoothThumbnail(screenBmp, 216)

        // 3. CHUYỂN ĐỔI SANG WEBP VÀ QUALITY 75
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

        if (baseBmp != screenBmp && !isVideo) baseBmp.recycle()
        thumbBmp.recycle()
        screenBmp.recycle()

        return Pair(thumbBaos.toByteArray(), screenBaos.toByteArray())
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

    /**
     * Hàm lấy ngày chụp thực tế đáng tin cậy.
     * Đọc trực tiếp EXIF Metadata từ luồng file byte để lấy chuẩn xác,
     * phòng trường hợp DocumentProvider/SAF trả về null.
     */
    private fun getActualDateTaken(context: Context, uri: Uri, isVideo: Boolean): Long {
        // 1. Thử lấy từ datetaken của MediaStore (Nhanh nhất)
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

        // 2. Mở file stream đọc trực tiếp Metadata bên trong để lấy chuẩn ngày
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

        // 3. Fallback check ngày sửa đổi file gần nhất từ Provider
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

        // 4. Nếu toàn bộ metadata bị mất/hỏng, buộc phải dùng ngày hiện tại
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