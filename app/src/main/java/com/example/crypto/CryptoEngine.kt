package com.example.crypto

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.FileInputStream
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

    val SGV2_MAGIC = "SGV2".toByteArray(Charsets.UTF_8)

    enum class Tier { THUMB, SCREEN, FULL }

    // --- Giữ nguyên các hàm deriveKek, initVault, unlockVault, hasVaultConfig, calculateBlockIv ---
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

    // Tên file chỉ còn 1 tier mã hóa, không lưu thumb_enc riêng nữa
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

    // --- CÁC HÀM XỬ LÝ FORMAT SGV2 MỚI ---

    private fun encryptBlob(data: ByteArray, dek: ByteArray): ByteArray {
        val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        val ciphertext = cipher.doFinal(data)
        return nonce + ciphertext // Tự chứa Nonce + Payload + Tag
    }

    private fun decryptBlob(blob: ByteArray, dek: ByteArray): ByteArray {
        val nonce = blob.copyOfRange(0, 12)
        val ciphertext = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Tạo file SGV2 chứa cả 3 Tier. Tier Full được đọc trực tiếp từ InputStream để
     * bảo toàn 100% EXIF/Gainmap của file gốc mà không qua decode.
     */
    fun encryptSgv2File(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        thumbBytes: ByteArray,
        screenBytes: ByteArray,
        dek: ByteArray,
        sourceSize: Long = -1L,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val thumbCipher = encryptBlob(thumbBytes, dek)
        val screenCipher = encryptBlob(screenBytes, dek)

        // Tạo plaintext header 64 byte
        val headerPlain = ByteBuffer.allocate(64).apply {
            putLong(thumbCipher.size.toLong())
            putLong(screenCipher.size.toLong())
            putInt(0) // flags dự phòng
            // Phần còn lại tự động zero-fill
        }.array()

        val headerNonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, headerNonce))
        val headerCiphertext = cipher.doFinal(headerPlain) // Sẽ dài 80 bytes

        context.contentResolver.openOutputStream(destUri)?.use { out ->
            // Ghi 96 bytes Fixed Header
            out.write(SGV2_MAGIC) // 4 bytes
            out.write(headerNonce) // 12 bytes
            out.write(headerCiphertext) // 80 bytes

            // Ghi các tier metadata
            out.write(thumbCipher)
            out.write(screenCipher)

            // Block-encrypt file Full trực tiếp để giữ EXIF
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                encryptFileContent(input, out, dek, sourceSize, onProgress)
            }
        }
    }

    /** Block encryption gốc cho Tier 3. totalSize/onProgress dùng để báo % tiến độ khi import. */
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
        var lastReportedPercent = -1

        while (input.read(buffer).also { bytesRead = it } != -1) {
            val blockIv = calculateBlockIv(fileNonce, blockIndex)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE_BITS, blockIv))
            output.write(cipher.doFinal(buffer, 0, bytesRead))
            blockIndex++
            totalBytesRead += bytesRead

            // Chỉ báo tiến độ khi biết tổng kích thước, và chỉ khi % nguyên thay đổi (tránh spam state)
            if (totalSize > 0 && onProgress != null) {
                val progress = (totalBytesRead.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                val percent = (progress * 100).toInt()
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onProgress(progress)
                }
            }
        }
        onProgress?.invoke(1f)
    }

    /**
     * Đọc O(1) Seek bất kỳ tier nào. Trả về luồng InputStream đã được giải mã sẵn.
     */
    fun getSgv2TierStream(context: Context, uri: Uri, tier: Tier, dek: ByteArray): InputStream? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val fis = FileInputStream(pfd.fileDescriptor)
        val channel = fis.channel

        // Đọc 96 bytes Header
        val headerBuffer = ByteBuffer.allocate(96)
        channel.read(headerBuffer)
        headerBuffer.flip()

        val magic = ByteArray(4)
        headerBuffer.get(magic)
        if (!magic.contentEquals(SGV2_MAGIC)) throw IllegalStateException("Not an SGV2 format")

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

        return when(tier) {
            Tier.THUMB -> {
                channel.position(offsetThumb)
                val blob = ByteArray(thumbLen.toInt())
                fis.read(blob)
                fis.close()
                pfd.close()
                ByteArrayInputStream(decryptBlob(blob, dek))
            }
            Tier.SCREEN -> {
                channel.position(offsetScreen)
                val blob = ByteArray(screenLen.toInt())
                fis.read(blob)
                fis.close()
                pfd.close()
                ByteArrayInputStream(decryptBlob(blob, dek))
            }
            Tier.FULL -> {
                channel.position(offsetFull)
                // KHÔNG close fis/pfd ở đây, BlockDecryptingInputStream sẽ chiếm quyền quản lý nó
                BlockDecryptingInputStream(fis, dek)
            }
        }
    }

    // --- Các hàm SAF helper giữ nguyên ---
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