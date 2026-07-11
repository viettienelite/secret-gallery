package com.example.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.example.crypto.CryptoEngine
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedDataSource(
    private val context: Context,
    private val dek: ByteArray
) : BaseDataSource(/* isNetwork = */ false) {

    private var dataSpec: DataSpec? = null
    private var pfd: ParcelFileDescriptor? = null
    private var fileChannel: FileChannel? = null
    private var opened = false
    private var currentPosition: Long = 0
    private var bytesRemaining: Long = 0
    
    private val fileNonce = ByteArray(12)
    private var encryptedFileSize: Long = 0

    // Cache of the most recently decrypted block to optimize sequential sub-block reads from ExoPlayer
    private var cachedBlockIndex: Long = -1
    private var cachedBlockData: ByteArray? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        val uri = dataSpec.uri

        pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Failed to open file descriptor for: $uri")
        val fileInputStream = FileInputStream(pfd!!.fileDescriptor)
        fileChannel = fileInputStream.channel
        encryptedFileSize = fileChannel!!.size()

        if (encryptedFileSize < 12) {
            throw IOException("Malformed encrypted file: file too small")
        }

        // Read 12-byte header file nonce
        val headerBuffer = ByteBuffer.allocate(12)
        fileChannel!!.read(headerBuffer, 0)
        headerBuffer.flip()
        headerBuffer.get(fileNonce)

        // Reset decrypt cache
        cachedBlockIndex = -1
        cachedBlockData = null

        currentPosition = dataSpec.position
        val decryptedLength = CryptoEngine.getDecryptedLength(encryptedFileSize)

        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = dataSpec.length
        } else {
            bytesRemaining = decryptedLength - currentPosition
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        
        // Block-based index calculation
        val blockIndex = currentPosition / 4096
        val blockOffset = (currentPosition % 4096).toInt()
        val blockRemaining = 4096 - blockOffset
        val bytesFromThisBlock = minOf(toRead, blockRemaining)

        val decryptedBlock = getOrDecryptBlock(blockIndex)
        if (decryptedBlock == null || decryptedBlock.isEmpty()) {
            return C.RESULT_END_OF_INPUT
        }

        val actualToCopy = minOf(bytesFromThisBlock, decryptedBlock.size - blockOffset)
        if (actualToCopy <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        System.arraycopy(decryptedBlock, blockOffset, buffer, offset, actualToCopy)

        currentPosition += actualToCopy
        bytesRemaining -= actualToCopy
        bytesTransferred(actualToCopy)
        
        return actualToCopy
    }

    private fun getOrDecryptBlock(blockIndex: Long): ByteArray? {
        if (blockIndex == cachedBlockIndex && cachedBlockData != null) {
            return cachedBlockData
        }

        val encryptedBlockStart = 12 + blockIndex * 4112
        if (encryptedBlockStart >= encryptedFileSize) {
            return null
        }

        val encryptedBlockSize = minOf(4112, encryptedFileSize - encryptedBlockStart)
        if (encryptedBlockSize <= 16) {
            return null
        }

        val byteBuffer = ByteBuffer.allocate(encryptedBlockSize.toInt())
        var bytesRead = 0
        while (bytesRead < encryptedBlockSize) {
            val count = fileChannel!!.read(byteBuffer, encryptedBlockStart + bytesRead)
            if (count == -1) {
                break
            }
            bytesRead += count
        }

        if (bytesRead <= 16) {
            return null
        }

        return try {
            val blockIv = CryptoEngine.calculateBlockIv(fileNonce, blockIndex)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(dek, "AES")
            val gcmSpec = GCMParameterSpec(128, blockIv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            val decrypted = cipher.doFinal(byteBuffer.array(), 0, bytesRead)
            
            cachedBlockIndex = blockIndex
            cachedBlockData = decrypted
            decrypted
        } catch (e: Exception) {
            null
        }
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        try {
            pfd?.close()
        } catch (e: IOException) {
            // Ignore
        } finally {
            pfd = null
            fileChannel = null
            cachedBlockIndex = -1
            cachedBlockData = null
            dataSpec = null
        }
    }
}
