package com.example.crypto

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

object CryptoEngine {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val BLOCK_SIZE = 4096
    private const val TAG_SIZE_BITS = 128
    private const val TAG_SIZE_BYTES = 16
    private const val ENCRYPTED_BLOCK_SIZE = BLOCK_SIZE + TAG_SIZE_BYTES // 4112 bytes

    /**
     * Derives a 256-bit Key Encryption Key (KEK) from the master password and salt using PBKDF2.
     */
    fun deriveKek(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Initializes a new Vault inside the directory by generating a random Salt and DEK,
     * encrypting the DEK with KEK derived from the password, and saving it to vault.conf.
     */
    fun initVault(context: Context, directoryUri: Uri, password: String): ByteArray {
        val random = SecureRandom()

        // Generate 16-byte random salt
        val salt = ByteArray(16)
        random.nextBytes(salt)

        // Generate 32-byte (256-bit) random DEK (Data Encryption Key)
        val dek = ByteArray(32)
        random.nextBytes(dek)

        // Derive KEK from password and salt
        val kek = deriveKek(password, salt)

        // Generate 12-byte random IV for DEK encryption
        val dekIv = ByteArray(12)
        random.nextBytes(dekIv)

        // Encrypt DEK with KEK
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(TAG_SIZE_BITS, dekIv))
        val encryptedDek = cipher.doFinal(dek)

        // Save salt, encrypted DEK, and DEK IV to vault.conf
        val json = JSONObject().apply {
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("encryptedDek", Base64.encodeToString(encryptedDek, Base64.NO_WRAP))
            put("dekIv", Base64.encodeToString(dekIv, Base64.NO_WRAP))
        }

        // Write to vault.conf file in the selected SAF directory
        val vaultConfUri = createChildFile(context, directoryUri, "vault.conf")
            ?: throw java.io.IOException("Cannot create vault.conf")

        context.contentResolver.openOutputStream(vaultConfUri)?.use { out ->
            out.write(json.toString().toByteArray(Charsets.UTF_8))
        } ?: throw java.io.IOException("Cannot open output stream for vault.conf")

        return dek
    }

    /**
     * Unlocks the Vault by reading vault.conf, deriving the KEK, and decrypting the DEK.
     * Returns the raw 32-byte DEK. Throws an exception if decryption fails (incorrect password).
     */
    fun unlockVault(context: Context, directoryUri: Uri, password: String): ByteArray {
        val vaultConfUri = findChildUri(context, directoryUri, "vault.conf")
            ?: throw java.io.FileNotFoundException("vault.conf not found in the selected folder.")

        val jsonStr = context.contentResolver.openInputStream(vaultConfUri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw java.io.IOException("Cannot read vault.conf")

        val json = JSONObject(jsonStr)
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val encryptedDek = Base64.decode(json.getString("encryptedDek"), Base64.NO_WRAP)
        val dekIv = Base64.decode(json.getString("dekIv"), Base64.NO_WRAP)

        // Derive KEK
        val kek = deriveKek(password, salt)

        // Decrypt DEK
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(TAG_SIZE_BITS, dekIv))

        return cipher.doFinal(encryptedDek)
    }

    /**
     * Checks if a vault.conf exists in the directory.
     */
    fun hasVaultConfig(context: Context, directoryUri: Uri): Boolean {
        return findChildUri(context, directoryUri, "vault.conf") != null
    }

    /**
     * Stateless self-contained filename encryption:
     * Tên gốc (String) -> AES-256-GCM (với ngẫu nhiên 12-byte IV) -> ByteArray -> Base64URL -> Tên file vật lý.
     * Output format: [Base64URL_String].enc
     */
    fun encryptFileName(name: String, dek: ByteArray): String {
        val random = SecureRandom()
        val iv = ByteArray(12)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(dek, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE_BITS, iv))

        val plaintextBytes = name.toByteArray(Charsets.UTF_8)
        val ciphertextBytes = cipher.doFinal(plaintextBytes)

        // Prepend IV to ciphertext so it's self-contained and stateless
        val resultBytes = ByteArray(iv.size + ciphertextBytes.size)
        System.arraycopy(iv, 0, resultBytes, 0, iv.size)
        System.arraycopy(ciphertextBytes, 0, resultBytes, iv.size, ciphertextBytes.size)

        val base64Url = Base64.encodeToString(resultBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$base64Url.enc"
    }

    /**
     * Decrypts the physical filename to retrieve the original plaintext filename.
     */
    fun decryptFileName(encryptedName: String, dek: ByteArray): String? {
        return try {
            val basePart = if (encryptedName.endsWith(".enc")) {
                encryptedName.substring(0, encryptedName.length - 4)
            } else {
                encryptedName
            }

            val decodedBytes = Base64.decode(basePart, Base64.URL_SAFE or Base64.NO_WRAP)
            if (decodedBytes.size <= 12) return null

            val iv = ByteArray(12)
            System.arraycopy(decodedBytes, 0, iv, 0, 12)

            val ciphertextLength = decodedBytes.size - 12
            val ciphertext = ByteArray(ciphertextLength)
            System.arraycopy(decodedBytes, 12, ciphertext, 0, ciphertextLength)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(dek, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE_BITS, iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculates the unique 12-byte IV for a specific block by XORing the File Nonce with the Block Index.
     */
    fun calculateBlockIv(fileNonce: ByteArray, blockIndex: Long): ByteArray {
        val iv = fileNonce.clone()
        for (i in 0 until 8) {
            val byteValue = ((blockIndex ushr (i * 8)) and 0xFF).toByte()
            iv[4 + i] = (iv[4 + i].toInt() xor byteValue.toInt()).toByte()
        }
        return iv
    }

    /**
     * Computes the exact decrypted file size from an encrypted file size under our block scheme.
     */
    fun getDecryptedLength(encryptedFileSize: Long): Long {
        if (encryptedFileSize <= 12) return 0
        val dataSize = encryptedFileSize - 12
        val fullBlocks = dataSize / ENCRYPTED_BLOCK_SIZE
        val remaining = dataSize % ENCRYPTED_BLOCK_SIZE
        val remainingPlaintext = if (remaining > TAG_SIZE_BYTES) remaining - TAG_SIZE_BYTES else 0
        return fullBlocks * BLOCK_SIZE + remainingPlaintext
    }

    /**
     * Block-based file encryption.
     * Takes an input stream of clean data, encrypts block by block (4KB),
     * and writes to output stream (12-byte header + 4112-byte blocks).
     */
    fun encryptFileContent(input: InputStream, output: OutputStream, dek: ByteArray) {
        val random = SecureRandom()
        val fileNonce = ByteArray(12)
        random.nextBytes(fileNonce)

        // Write 12-byte file nonce header
        output.write(fileNonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(dek, "AES")

        val buffer = ByteArray(BLOCK_SIZE)
        var blockIndex: Long = 0
        var bytesRead: Int

        while (input.read(buffer).also { bytesRead = it } != -1) {
            val blockIv = calculateBlockIv(fileNonce, blockIndex)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE_BITS, blockIv))

            val encryptedBlock = cipher.doFinal(buffer, 0, bytesRead)
            output.write(encryptedBlock)
            blockIndex++
        }
    }

    // --- Storage Access Framework Helper Functions ---

    /**
     * Safely retrieves the document ID from a Uri, checking if it is a tree Uri.
     */
    private fun getSafeDocumentId(uri: Uri): String {
        return if (android.provider.DocumentsContract.isTreeUri(uri)) {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        } else {
            android.provider.DocumentsContract.getDocumentId(uri)
        }
    }

    /**
     * Helper to search for a file in a DocumentTree directory by exact name.
     */
    fun findChildUri(context: Context, rootUri: Uri, displayName: String): Uri? {
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            rootUri,
            getSafeDocumentId(rootUri)
        )
        val resolver = context.contentResolver
        var cursor: android.database.Cursor? = null
        try {
            cursor = resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (name == displayName) {
                        val docId = cursor.getString(0)
                        return android.provider.DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Helper to create a child file in a DocumentTree directory.
     */
    fun createChildFile(context: Context, parentUri: Uri, displayName: String, mimeType: String = "application/octet-stream"): Uri? {
        val parentId = getSafeDocumentId(parentUri)
        val targetUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(parentUri, parentId)
        return try {
            android.provider.DocumentsContract.createDocument(
                context.contentResolver,
                targetUri,
                mimeType,
                displayName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a document file.
     */
    fun deleteFile(context: Context, docUri: Uri): Boolean {
        return try {
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, docUri)
        } catch (e: Exception) {
            false
        }
    }
}
