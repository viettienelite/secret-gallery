package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    val thumbUri: Uri?,
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
                // Check if we still have permission to access it
                val hasPermission = context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri
                }
                if (hasPermission) {
                    _vaultUri.value = uri
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setVaultUri(context: Context, uri: Uri?) {
        _vaultUri.value = uri
        _dek.value = null
        _items.value = emptyList()
        _error.value = null

        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        if (uri != null) {
            prefs.edit().putString("saved_vault_uri", uri.toString()).apply()
        } else {
            prefs.edit().remove("saved_vault_uri").apply()
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun selectMedia(item: VaultItem?, bounds: FloatArray? = null) {
        _clickedItemBounds.value = bounds
        _selectedMedia.value = item
    }

    /**
     * Attempts to unlock the selected Vault with the password.
     */
    fun unlockVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val key = withContext(Dispatchers.IO) {
                    CryptoEngine.unlockVault(context, uri, password)
                }
                _dek.value = key
                loadVaultItems(context)
            } catch (e: Exception) {
                _error.value = "Incorrect password or corrupted Vault configuration."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Initializes a new Vault at the selected directory with the password.
     */
    fun createVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val key = withContext(Dispatchers.IO) {
                    CryptoEngine.initVault(context, uri, password)
                }
                _dek.value = key
                loadVaultItems(context)
            } catch (e: Exception) {
                _error.value = "Failed to create Vault: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Locks the vault by clearing sensitive keys and items.
     */
    fun lockVault() {
        _dek.value = null
        _items.value = emptyList()
        _selectedMedia.value = null
    }

    /**
     * Scans the selected SAF folder directly using query cursors for performance,
     * matching original files and thumbnail files.
     */
    fun loadVaultItems(context: Context) {
        val uri = _vaultUri.value ?: return
        val key = _dek.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val list = withContext(Dispatchers.IO) {
                    scanFiles(context, uri, key)
                }
                _items.value = list
            } catch (e: Exception) {
                _error.value = "Failed to load gallery items: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun scanFiles(context: Context, rootUri: Uri, key: ByteArray): List<VaultItem> {
        val result = mutableListOf<VaultItem>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            rootUri,
            DocumentsContract.getTreeDocumentId(rootUri)
        )
        
        val resolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val filesMap = mutableMapOf<String, Uri>()
        val filesSize = mutableMapOf<String, Long>()
        val filesLastModified = mutableMapOf<String, Long>()

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val docIdIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val lastModIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = if (docIdIdx != -1) cursor.getString(docIdIdx) else ""
                val displayName = if (nameIdx != -1) cursor.getString(nameIdx) else ""
                val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                val lastModified = if (lastModIdx != -1) cursor.getLong(lastModIdx) else 0L
                
                if (displayName.endsWith(".enc")) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
                    filesMap[displayName] = fileUri
                    filesSize[displayName] = size
                    filesLastModified[displayName] = lastModified
                }
            }
        }

        // Match original files with their thumbs
        for ((displayName, fileUri) in filesMap) {
            if (displayName == "vault.conf" || displayName.endsWith("_thumb.enc")) {
                continue
            }

            // Decrypt physical name
            val decryptedMeta = CryptoEngine.decryptFileName(displayName, key) ?: continue
            val (dateTaken, decryptedName) = if (decryptedMeta.contains('|')) {
                val parts = decryptedMeta.split('|', limit = 2)
                val ts = parts[0].toLongOrNull() ?: 0L
                val name = parts[1]
                Pair(ts, name)
            } else {
                Pair(filesLastModified[displayName] ?: 0L, decryptedMeta)
            }

            val isVideo = isVideoFile(decryptedName)
            val size = filesSize[displayName] ?: 0L

            // Match thumbnail: if original is X.enc, thumb is X_thumb.enc
            val baseName = displayName.substring(0, displayName.length - 4) // remove .enc
            val thumbName = "${baseName}_thumb.enc"
            val thumbUri = filesMap[thumbName]

            result.add(
                VaultItem(
                    originalName = decryptedName,
                    encryptedName = displayName,
                    uri = fileUri,
                    isVideo = isVideo,
                    thumbUri = thumbUri,
                    size = size,
                    dateTaken = dateTaken
                )
            )
        }

        return result.sortedByDescending { it.dateTaken }
    }

    /**
     * Imports a media file, encrypts its content block-by-block, extracts a 256x256 thumbnail,
     * encrypts the thumbnail, and saves both to the selected Vault directory.
     */
    fun importMedia(context: Context, sourceUri: Uri) {
        val vaultUriVal = _vaultUri.value ?: return
        val key = _dek.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    // 1. Get original file name
                    val originalName = getFileNameFromUri(context, sourceUri) ?: "import_${System.currentTimeMillis()}"
                    
                    // 1b. Get original date taken (camera taken date)
                    val dateTaken = getDateTakenFromUri(context, sourceUri)
                    
                    // 1c. Combine dateTaken and originalName securely
                    val combinedMetadata = "$dateTaken|$originalName"
                    
                    // 2. Generate encrypted physical file name
                    val encryptedName = CryptoEngine.encryptFileName(combinedMetadata, key)
                    
                    // 3. Create encrypted destination file in SAF
                    val encryptedFileUri = CryptoEngine.createChildFile(context, vaultUriVal, encryptedName)
                        ?: throw java.io.IOException("Could not create encrypted file in Vault")

                    // 4. Encrypt full original file block-by-block (4KB chunks)
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        context.contentResolver.openOutputStream(encryptedFileUri)?.use { output ->
                            CryptoEngine.encryptFileContent(input, output, key)
                        }
                    } ?: throw java.io.IOException("Cannot open input stream for source media")

                    // 5. Generate 256x256px thumbnail and encrypt it
                    try {
                        val thumbBitmap = generateThumbnail(context, sourceUri, isVideoFile(originalName))
                        if (thumbBitmap != null) {
                            val baos = ByteArrayOutputStream()
                            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                            val thumbBytes = baos.toByteArray()
                            
                            val baseName = encryptedName.substring(0, encryptedName.length - 4)
                            val thumbEncryptedName = "${baseName}_thumb.enc"
                            
                            val thumbFileUri = CryptoEngine.createChildFile(context, vaultUriVal, thumbEncryptedName)
                            if (thumbFileUri != null) {
                                ByteArrayInputStream(thumbBytes).use { tInput ->
                                    context.contentResolver.openOutputStream(thumbFileUri)?.use { tOutput ->
                                        CryptoEngine.encryptFileContent(tInput, tOutput, key)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace() // Thumbnail extraction is best-effort, do not fail import
                    }
                }
                // Reload items
                loadVaultItems(context)
            } catch (e: Exception) {
                _error.value = "Import failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Deletes a media item and its corresponding thumbnail sidecar.
     */
    fun deleteMedia(context: Context, item: VaultItem) {
        val rootUri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    // Delete original file
                    CryptoEngine.deleteFile(context, item.uri)
                    
                    // Delete thumbnail if exists
                    if (item.thumbUri != null) {
                        CryptoEngine.deleteFile(context, item.thumbUri)
                    }
                }
                _selectedMedia.value = null
                loadVaultItems(context)
            } catch (e: Exception) {
                _error.value = "Failed to delete item: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Helper functions ---

    private fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "m4v")
    }

    private fun extractMediaStoreId(context: Context, uri: Uri): Long? {
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId.contains(":")) {
                    val idPart = docId.substringAfter(':')
                    val id = idPart.toLongOrNull()
                    if (id != null) return id
                }
                val id = docId.toLongOrNull()
                if (id != null) return id
            }
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrEmpty()) {
                val id = lastSegment.toLongOrNull()
                if (id != null) return id
                if (lastSegment.contains(":")) {
                    val idPart = lastSegment.substringAfter(':')
                    val id2 = idPart.toLongOrNull()
                    if (id2 != null) return id2
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val uriStr = uri.toString()
            val decoded = Uri.decode(uriStr)
            if (decoded.contains("image:")) {
                val idStr = decoded.substringAfter("image:").takeWhile { it.isDigit() }
                val id = idStr.toLongOrNull()
                if (id != null) return id
            }
            if (decoded.contains("video:")) {
                val idStr = decoded.substringAfter("video:").takeWhile { it.isDigit() }
                val id = idStr.toLongOrNull()
                if (id != null) return id
            }
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        var nameIsReal = false

        val updateName = { newName: String, isReal: Boolean ->
            if (newName.isNotEmpty()) {
                if (!nameIsReal) {
                    name = newName
                    nameIsReal = isReal
                } else if (isReal) {
                    name = newName
                    nameIsReal = true
                }
            }
        }

        // Strategy 0: Try MediaStore _data column
        try {
            val dataName = queryDataColumn(context, uri)
            if (!dataName.isNullOrEmpty()) {
                updateName(dataName, !isGenericNumber(dataName))
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Strategy 1: OpenableColumns.DISPLAY_NAME
        if (!nameIsReal) {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val queriedName = cursor.getString(nameIndex)
                            if (!queriedName.isNullOrEmpty()) {
                                updateName(queriedName, !isGenericNumber(queriedName))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Strategy 1.5: Direct MediaStore Query via Extracted ID
        if (!nameIsReal) {
            val mediaId = extractMediaStoreId(context, uri)
            if (mediaId != null) {
                // Try as Image
                try {
                    val imageUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    context.contentResolver.query(imageUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val dispIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                            if (dispIdx != -1) {
                                val queriedName = cursor.getString(dispIdx)
                                if (!queriedName.isNullOrEmpty()) {
                                    updateName(queriedName, !isGenericNumber(queriedName))
                                }
                            }
                            
                            val dataIdx = cursor.getColumnIndex("_data")
                            if (dataIdx != -1) {
                                val path = cursor.getString(dataIdx)
                                if (!path.isNullOrEmpty()) {
                                    val fileName = path.substringAfterLast('/')
                                    if (fileName.isNotEmpty()) {
                                        updateName(fileName, !isGenericNumber(fileName))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }

                // Try as Video if still not real
                if (!nameIsReal) {
                    try {
                        val videoUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            mediaId
                        )
                        context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dispIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                                if (dispIdx != -1) {
                                    val queriedName = cursor.getString(dispIdx)
                                    if (!queriedName.isNullOrEmpty()) {
                                        updateName(queriedName, !isGenericNumber(queriedName))
                                    }
                                }
                                
                                val dataIdx = cursor.getColumnIndex("_data")
                                if (dataIdx != -1) {
                                    val path = cursor.getString(dataIdx)
                                    if (!path.isNullOrEmpty()) {
                                        val fileName = path.substringAfterLast('/')
                                        if (fileName.isNotEmpty()) {
                                            updateName(fileName, !isGenericNumber(fileName))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }

        // Strategy 2: DocumentsContract isDocumentUri
        if (!nameIsReal) {
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    if (docId.contains(":")) {
                        val split = docId.split(":")
                        val type = split[0]
                        val id = split.getOrNull(1)
                        if (id != null) {
                            if (type in setOf("image", "video", "audio")) {
                                val baseUri = when (type) {
                                    "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                    "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                    else -> null
                                }
                                if (baseUri != null) {
                                    val mediaUri = android.content.ContentUris.withAppendedId(baseUri, id.toLong())
                                    val qName = queryNameFromUri(context, mediaUri)
                                    if (!qName.isNullOrEmpty()) {
                                        updateName(qName, !isGenericNumber(qName))
                                    }
                                }
                            } else if (type == "primary") {
                                val pathSegment = docId.substringAfter(':')
                                val fileName = pathSegment.substringAfterLast('/')
                                if (fileName.isNotEmpty()) {
                                    updateName(fileName, !isGenericNumber(fileName))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Strategy 3: MediaStore Columns direct query
        if (!nameIsReal) {
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.TITLE
                )
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dispIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        val titleIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.TITLE)
                        
                        var dName: String? = null
                        if (dispIndex != -1) {
                            dName = cursor.getString(dispIndex)
                        }
                        if (dName.isNullOrEmpty() || isGenericNumber(dName)) {
                            if (titleIndex != -1) {
                                val title = cursor.getString(titleIndex)
                                if (!title.isNullOrEmpty()) dName = title
                            }
                        }
                        if (!dName.isNullOrEmpty()) {
                            updateName(dName, !isGenericNumber(dName))
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Strategy 4: lastPathSegment
        if (!nameIsReal) {
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrEmpty()) {
                val decodedSegment = Uri.decode(lastSegment)
                var part = decodedSegment.substringAfterLast('/')
                part = part.substringAfterLast(':')
                if (part.isNotEmpty()) {
                    updateName(part, !isGenericNumber(part))
                }
            }
        }

        // Strategy 5: Last resort fallback
        if (name == null) {
            val qName = queryNameFromUri(context, uri)
            if (qName != null) {
                updateName(qName, !isGenericNumber(qName))
            }
        }

        // Extension post processing
        if (name != null) {
            val ext = name!!.substringAfterLast('.', "")
            if (ext.isEmpty() || isGenericNumber(ext) || ext == name) {
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType != null) {
                    val mimeExt = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (mimeExt != null) {
                        name = if (name!!.contains(".")) {
                            name!!.substringBeforeLast('.') + "." + mimeExt
                        } else {
                            "$name.$mimeExt"
                        }
                    }
                }
            }
        }

        return name
    }

    private fun getDateTakenFromUri(context: Context, uri: Uri): Long {
        var dateTaken = 0L
        try {
            val projection = arrayOf("datetaken", "date_modified")
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateTakenIndex = cursor.getColumnIndex("datetaken")
                    if (dateTakenIndex != -1) {
                        dateTaken = cursor.getLong(dateTakenIndex)
                    }
                    if (dateTaken == 0L) {
                        val dateModifiedIndex = cursor.getColumnIndex("date_modified")
                        if (dateModifiedIndex != -1) {
                            dateTaken = cursor.getLong(dateModifiedIndex) * 1000L
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (dateTaken == 0L) {
            dateTaken = System.currentTimeMillis()
        }
        return dateTaken
    }

    private fun isGenericNumber(str: String): Boolean {
        val base = str.substringBeforeLast('.')
        if (base.isEmpty()) return false
        return base.all { it.isDigit() } && base.length in 1..15
    }

    private fun queryNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            // Strategy A: DISPLAY_NAME
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val queriedName = cursor.getString(nameIndex)
                        if (!queriedName.isNullOrEmpty()) {
                            name = queriedName
                        }
                    }
                }
            }
            
            // Strategy B: If empty or generic number, try MediaColumns.DISPLAY_NAME
            if (name.isNullOrEmpty() || isGenericNumber(name!!)) {
                context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val queriedName = cursor.getString(nameIndex)
                            if (!queriedName.isNullOrEmpty()) {
                                name = queriedName
                            }
                        }
                    }
                }
            }

            // Strategy C: If still empty or generic number, try path _data column
            if (name.isNullOrEmpty() || isGenericNumber(name!!)) {
                val dataPath = queryDataColumn(context, uri)
                if (!dataPath.isNullOrEmpty()) {
                    name = dataPath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    private fun queryDataColumn(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex("_data")
                    if (dataIndex != -1) {
                        val path = cursor.getString(dataIndex)
                        if (!path.isNullOrEmpty()) {
                            val fileName = path.substringAfterLast('/')
                            if (fileName.isNotEmpty()) {
                                return fileName
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun createCenterCroppedBitmap(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcWidth = src.width
        val srcHeight = src.height
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        
        val cropWidth: Int
        val cropHeight: Int
        val cropX: Int
        val cropY: Int
        
        if (srcRatio > targetRatio) {
            cropHeight = srcHeight
            cropWidth = (srcHeight * targetRatio).toInt()
            cropX = (srcWidth - cropWidth) / 2
            cropY = 0
        } else {
            cropWidth = srcWidth
            cropHeight = (srcWidth / targetRatio).toInt()
            cropX = 0
            cropY = (srcHeight - cropHeight) / 2
        }
        
        val cropped = Bitmap.createBitmap(src, cropX, cropY, cropWidth, cropHeight)
        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        if (cropped != src) {
            cropped.recycle()
        }
        return scaled
    }

    private fun createAspectScaledBitmap(src: Bitmap, maxDimension: Int): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= 0 || height <= 0) return src
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
    }

    private fun generateThumbnail(context: Context, uri: Uri, isVideo: Boolean): Bitmap? {
        return if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                // Get first frame
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    createAspectScaledBitmap(frame, 360)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        } else {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            
            // Calculate scale factor to target 360px on max dimension
            val width = options.outWidth
            val height = options.outHeight
            var inSampleSize = 1
            if (width > 360 || height > 360) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while (halfWidth / inSampleSize >= 360 && halfHeight / inSampleSize >= 360) {
                    inSampleSize *= 2
                }
            }
            
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
            
            bitmap?.let {
                createAspectScaledBitmap(it, 360)
            }
        }
    }
}
