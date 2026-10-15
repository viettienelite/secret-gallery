package com.example.crypto

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

object CryptoEngine {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 600000
    private const val KEY_LENGTH = 256
    private const val BLOCK_SIZE = 4096
    private const val TAG_SIZE_BITS = 128
    private const val TAG_SIZE_BYTES = 16
    private const val ENCRYPTED_BLOCK_SIZE = BLOCK_SIZE + TAG_SIZE_BYTES // 4112 bytes

    private const val FLAG_HAS_DIMENSIONS = 1
    private const val FLAG_IS_LIVE_PHOTO = 2

    val SGV2_MAGIC = "SGV2".toByteArray(Charsets.UTF_8)

    enum class Tier { THUMB, SCREEN, FULL }

    /**
     * Thông tin metadata đọc trực tiếp O(1) từ 96-byte Header của SGV2,
     * bao gồm cả thông tin Live Photo (vị trí và kích thước video nhúng trong Tier FULL).
     */
    data class Sgv2FileInfo(
        val width: Int,
        val height: Int,
        val decryptedSize: Long,
        val isLivePhoto: Boolean = false,
        val liveVideoOffset: Long = 0L,
        val liveVideoSize: Long = 0L
    )

    fun deriveKek(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun initVault(context: Context, directoryUri: Uri, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).apply { random.nextBytes(this) }
        val dek = ByteArray(32).apply { random.nextBytes(this) }
        val kek = deriveKek(password, salt)
        val dekIv = ByteArray(12).apply { random.nextBytes(this) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(TAG_SIZE_BITS, dekIv))
        val encryptedDek = cipher.doFinal(dek)

        val json = JSONObject().apply {
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("encryptedDek", Base64.encodeToString(encryptedDek, Base64.NO_WRAP))
            put("dekIv", Base64.encodeToString(dekIv, Base64.NO_WRAP))
        }

        val vaultConfUri = createChildFile(context, directoryUri, "vault.conf")
            ?: throw java.io.IOException("Cannot create vault.conf")
        context.contentResolver.openOutputStream(vaultConfUri)?.use { out ->
            out.write(json.toString().toByteArray(Charsets.UTF_8))
        }
        return dek
    }

    fun unlockVault(context: Context, directoryUri: Uri, password: String): ByteArray {
        val vaultConfUri = findChildUri(context, directoryUri, "vault.conf")
            ?: throw java.io.FileNotFoundException("vault.conf not found.")
        val jsonStr = context.contentResolver.openInputStream(vaultConfUri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw java.io.IOException("Cannot read vault.conf")

        val json = JSONObject(jsonStr)
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val encryptedDek = Base64.decode(json.getString("encryptedDek"), Base64.NO_WRAP)
        val dekIv = Base64.decode(json.getString("dekIv"), Base64.NO_WRAP)

        val kek = deriveKek(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(TAG_SIZE_BITS, dekIv))
        return cipher.doFinal(encryptedDek)
    }

    fun hasVaultConfig(context: Context, directoryUri: Uri): Boolean {
        return findChildUri(context, directoryUri, "vault.conf") != null
    }

    fun encryptFileName(name: String, dek: ByteArray): String {
        val random = SecureRandom()
        val iv = ByteArray(12).apply { random.nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
        val ciphertextBytes = cipher.doFinal(name.toByteArray(Charsets.UTF_8))
        val resultBytes = iv + ciphertextBytes
        val base64Url = Base64.encodeToString(resultBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$base64Url.enc"
    }

    fun decryptFileName(encryptedName: String, dek: ByteArray): String? {
        return try {
            val basePart = if (encryptedName.endsWith(".enc")) encryptedName.substring(0, encryptedName.length - 4) else encryptedName
            val decodedBytes = Base64.decode(basePart, Base64.URL_SAFE or Base64.NO_WRAP)
            val iv = decodedBytes.copyOfRange(0, 12)
            val ciphertext = decodedBytes.copyOfRange(12, decodedBytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }

    fun calculateBlockIv(fileNonce: ByteArray, blockIndex: Long): ByteArray {
        val iv = fileNonce.clone()
        for (i in 0 until 8) {
            val byteValue = ((blockIndex ushr (i * 8)) and 0xFF).toByte()
            iv[4 + i] = (iv[4 + i].toInt() xor byteValue.toInt()).toByte()
        }
        return iv
    }

    fun getDecryptedLength(encryptedSizeOfFullTier: Long): Long {
        if (encryptedSizeOfFullTier <= 12) return 0
        val dataSize = encryptedSizeOfFullTier - 12
        val fullBlocks = dataSize / ENCRYPTED_BLOCK_SIZE
        val remaining = dataSize % ENCRYPTED_BLOCK_SIZE
        val remainingPlaintext = if (remaining > TAG_SIZE_BYTES) remaining - TAG_SIZE_BYTES else 0
        return fullBlocks * BLOCK_SIZE + remainingPlaintext
    }

    private fun encryptBlob(data: ByteArray, dek: ByteArray): ByteArray {
        val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        val ciphertext = cipher.doFinal(data)
        return nonce + ciphertext
    }

    private fun decryptBlob(blob: ByteArray, dek: ByteArray): ByteArray {
        val nonce = blob.copyOfRange(0, 12)
        val ciphertext = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Tạo file SGV2 chứa cả 3 Tier. Hỗ trợ đầy đủ cả ảnh tĩnh, video và Live Photo / Motion Photo:
     * - Với Motion Photo nhúng sẵn trong 1 file (Google/Samsung/Xiaomi/OPPO): lưu liveVideoOffset và liveVideoSize.
     * - Với Live Photo dạng cặp 2 file (ảnh + companionVideoUri .mov/.mp4): nối luồng byte của companionVideoUri
     *   ngay sau luồng ảnh gốc trong Tier FULL để gói gọn thành 1 file SGV2 duy nhất.
     */
    fun encryptSgv2File(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        thumbBytes: ByteArray,
        screenBytes: ByteArray,
        dek: ByteArray,
        sourceSize: Long = -1L,
        mediaWidth: Int = 0,
        mediaHeight: Int = 0,
        liveVideoOffset: Long = 0L,
        liveVideoSize: Long = 0L,
        companionVideoUri: Uri? = null,
        companionVideoSize: Long = 0L,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val thumbCipher = encryptBlob(thumbBytes, dek)
        val screenCipher = encryptBlob(screenBytes, dek)

        val hasDimensions = mediaWidth > 0 && mediaHeight > 0
        val isLivePhoto = (liveVideoOffset > 0L && liveVideoSize > 0L) || (companionVideoUri != null && companionVideoSize > 0L)
        val effectiveLiveOffset = if (companionVideoUri != null && sourceSize > 0L) sourceSize else liveVideoOffset
        val effectiveLiveSize = if (companionVideoUri != null && companionVideoSize > 0L) companionVideoSize else liveVideoSize
        val totalPlainSize = when {
            companionVideoUri != null && sourceSize > 0L && companionVideoSize > 0L -> sourceSize + companionVideoSize
            sourceSize > 0L -> sourceSize
            else -> -1L
        }

        var flags = 0
        if (hasDimensions) flags = flags or FLAG_HAS_DIMENSIONS
        if (isLivePhoto) flags = flags or FLAG_IS_LIVE_PHOTO

        // Tạo plaintext header 64 byte:
        // [0..7]   thumbCipher.size (Long)
        // [8..15]  screenCipher.size (Long)
        // [16..19] flags (Int: bit 0 = hasDimensions, bit 1 = isLivePhoto)
        // [20..23] mediaWidth (Int)
        // [24..27] mediaHeight (Int)
        // [28..35] sourceSize (Long - kích thước phần ảnh gốc)
        // [36..43] effectiveLiveOffset (Long - vị trí bắt đầu MP4/MOV trong Tier FULL)
        // [44..51] effectiveLiveSize (Long - độ dài byte của MP4/MOV trong Tier FULL)
        // [52..63] dự phòng (zero-fill)
        val headerPlain = ByteBuffer.allocate(64).apply {
            putLong(thumbCipher.size.toLong())
            putLong(screenCipher.size.toLong())
            putInt(flags)
            putInt(if (hasDimensions) mediaWidth else 0)
            putInt(if (hasDimensions) mediaHeight else 0)
            putLong(if (sourceSize > 0L) sourceSize else 0L)
            putLong(if (isLivePhoto) effectiveLiveOffset else 0L)
            putLong(if (isLivePhoto) effectiveLiveSize else 0L)
        }.array()

        val headerNonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, headerNonce))
        val headerCiphertext = cipher.doFinal(headerPlain)

        context.contentResolver.openOutputStream(destUri)?.use { out ->
            out.write(SGV2_MAGIC)
            out.write(headerNonce)
            out.write(headerCiphertext)

            out.write(thumbCipher)
            out.write(screenCipher)

            context.contentResolver.openInputStream(sourceUri)?.use { primaryInput ->
                if (companionVideoUri != null) {
                    context.contentResolver.openInputStream(companionVideoUri)?.use { companionInput ->
                        val combinedInput = java.io.SequenceInputStream(primaryInput, companionInput)
                        encryptFileContent(combinedInput, out, dek, totalPlainSize, onProgress)
                    } ?: encryptFileContent(primaryInput, out, dek, totalPlainSize, onProgress)
                } else {
                    encryptFileContent(primaryInput, out, dek, totalPlainSize, onProgress)
                }
            }
        }
    }

    /**
     * Đọc nhanh thông tin file (width, height, decryptedSize, thông tin Live Photo) trong O(1) từ 96 bytes Header.
     */
    fun readSgv2FileInfo(context: Context, uri: Uri, dek: ByteArray): Sgv2FileInfo? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return try {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { fis ->
                val channel = fis.channel
                val totalFileSize = channel.size()
                if (totalFileSize < 96) return null

                val headerBuffer = ByteBuffer.allocate(96)
                while (headerBuffer.hasRemaining()) {
                    if (channel.read(headerBuffer) == -1) break
                }
                headerBuffer.flip()
                if (headerBuffer.remaining() < 96) return null

                val magic = ByteArray(4)
                headerBuffer.get(magic)
                if (!magic.contentEquals(SGV2_MAGIC)) return null

                val headerNonce = ByteArray(12)
                headerBuffer.get(headerNonce)
                val headerCipher = ByteArray(80)
                headerBuffer.get(headerCipher)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, headerNonce))
                val headerPlain = ByteBuffer.wrap(cipher.doFinal(headerCipher))

                val thumbLen = headerPlain.long
                val screenLen = headerPlain.long
                val flags = headerPlain.int
                val width = headerPlain.int
                val height = headerPlain.int
                val storedOriginalSize = headerPlain.long
                val liveOffset = headerPlain.long
                val liveSize = headerPlain.long

                val offsetFull = 96L + thumbLen + screenLen
                val fullTierEncryptedSize = (totalFileSize - offsetFull).coerceAtLeast(0L)
                val realSize = if (storedOriginalSize > 0L) {
                    storedOriginalSize
                } else {
                    getDecryptedLength(fullTierEncryptedSize)
                }

                val hasDim = (flags and FLAG_HAS_DIMENSIONS) != 0
                val isLive = ((flags and FLAG_IS_LIVE_PHOTO) != 0) && liveOffset > 0L && liveSize > 0L

                Sgv2FileInfo(
                    width = if (hasDim && width > 0) width else 0,
                    height = if (hasDim && height > 0) height else 0,
                    decryptedSize = realSize,
                    isLivePhoto = isLive,
                    liveVideoOffset = if (isLive) liveOffset else 0L,
                    liveVideoSize = if (isLive) liveSize else 0L
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Block encryption cho Tier 3.
     * Báo tiến độ mịn theo từng nấc 0.2% (500 bước) để thanh tiến trình trên UI chạy mượt mà.
     */
    private fun encryptFileContent(
        input: InputStream,
        output: OutputStream,
        dek: ByteArray,
        totalSize: Long = -1L,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val fileNonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        output.write(fileNonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(dek, "AES")
        val buffer = ByteArray(BLOCK_SIZE)
        var blockIndex = 0L
        var bytesRead: Int
        var totalBytesRead = 0L
        var lastReportedStep = -1

        while (input.read(buffer).also { bytesRead = it } != -1) {
            val blockIv = calculateBlockIv(fileNonce, blockIndex)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE_BITS, blockIv))
            output.write(cipher.doFinal(buffer, 0, bytesRead))
            blockIndex++
            totalBytesRead += bytesRead

            if (totalSize > 0 && onProgress != null) {
                val progress = (totalBytesRead.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                val step = (progress * 500).toInt() // Cập nhật mỗi 0.2%
                if (step != lastReportedStep) {
                    lastReportedStep = step
                    onProgress(progress)
                }
            }
        }
        onProgress?.invoke(1f)
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read == -1) throw IOException("Unexpected EOF while reading tier blob ($offset / ${target.size})")
            offset += read
        }
    }

    fun getSgv2TierStream(context: Context, uri: Uri, tier: Tier, dek: ByteArray): InputStream? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val fis = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        return try {
            val channel = fis.channel

            val headerBuffer = ByteBuffer.allocate(96)
            while (headerBuffer.hasRemaining()) {
                if (channel.read(headerBuffer) == -1) break
            }
            headerBuffer.flip()
            if (headerBuffer.remaining() < 96) {
                fis.close()
                return null
            }

            val magic = ByteArray(4)
            headerBuffer.get(magic)
            if (!magic.contentEquals(SGV2_MAGIC)) {
                fis.close()
                throw IllegalStateException("Not an SGV2 format")
            }

            val headerNonce = ByteArray(12)
            headerBuffer.get(headerNonce)
            val headerCipher = ByteArray(80)
            headerBuffer.get(headerCipher)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, headerNonce))
            val headerPlain = ByteBuffer.wrap(cipher.doFinal(headerCipher))

            val thumbLen = headerPlain.long
            val screenLen = headerPlain.long

            val offsetThumb = 96L
            val offsetScreen = offsetThumb + thumbLen
            val offsetFull = offsetScreen + screenLen

            when (tier) {
                Tier.THUMB -> {
                    channel.position(offsetThumb)
                    val blob = ByteArray(thumbLen.toInt())
                    readFully(fis, blob)
                    fis.close()
                    ByteArrayInputStream(decryptBlob(blob, dek))
                }
                Tier.SCREEN -> {
                    channel.position(offsetScreen)
                    val blob = ByteArray(screenLen.toInt())
                    readFully(fis, blob)
                    fis.close()
                    ByteArrayInputStream(decryptBlob(blob, dek))
                }
                Tier.FULL -> {
                    channel.position(offsetFull)
                    BlockDecryptingInputStream(fis, dek)
                }
            }
        } catch (e: Exception) {
            try { fis.close() } catch (_: Exception) {}
            throw e
        }
    }

    private fun getSafeDocumentId(uri: Uri) = if (DocumentsContract.isTreeUri(uri)) DocumentsContract.getTreeDocumentId(uri) else DocumentsContract.getDocumentId(uri)
    fun findChildUri(context: Context, rootUri: Uri, displayName: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, getSafeDocumentId(rootUri))
        context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == displayName) return DocumentsContract.buildDocumentUriUsingTree(rootUri, cursor.getString(0))
            }
        }
        return null
    }
    fun createChildFile(context: Context, parentUri: Uri, displayName: String, mimeType: String = "application/octet-stream"): Uri? {
        val targetUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, getSafeDocumentId(parentUri))
        return try { DocumentsContract.createDocument(context.contentResolver, targetUri, mimeType, displayName) } catch (e: Exception) { null }
    }
    fun deleteFile(context: Context, docUri: Uri) = try { DocumentsContract.deleteDocument(context.contentResolver, docUri) } catch (e: Exception) { false }
}