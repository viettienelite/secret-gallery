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
    private var fullTierEncryptedSize: Long = 0
    private var offsetFull: Long = 0

    private var cachedBlockIndex: Long = -1
    private var cachedBlockData: ByteArray? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        val uri = dataSpec.uri

        pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Failed to open file descriptor")
        val fileInputStream = FileInputStream(pfd!!.fileDescriptor)
        fileChannel = fileInputStream.channel
        val totalFileSize = fileChannel!!.size()

        if (totalFileSize < 96) throw IOException("File too small to be SGV2")

        // Parse SGV2 Header to find Tier 3 (Full) Offset
        val headerBuffer = ByteBuffer.allocate(96)
        fileChannel!!.read(headerBuffer, 0)
        headerBuffer.flip()

        val magic = ByteArray(4)
        headerBuffer.get(magic)
        if (!magic.contentEquals(CryptoEngine.SGV2_MAGIC)) throw IOException("Not an SGV2 format")

        val headerNonce = ByteArray(12)
        headerBuffer.get(headerNonce)
        val headerCipher = ByteArray(80)
        headerBuffer.get(headerCipher)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(128, headerNonce))
        val headerPlain = ByteBuffer.wrap(cipher.doFinal(headerCipher))
        val thumbLen = headerPlain.long
        val screenLen = headerPlain.long
        offsetFull = 96L + thumbLen + screenLen

        fullTierEncryptedSize = totalFileSize - offsetFull

        // Đọc Nonce của Tier 3 tại offsetFull
        val nonceBuffer = ByteBuffer.allocate(12)
        fileChannel!!.read(nonceBuffer, offsetFull)
        nonceBuffer.flip()
        nonceBuffer.get(fileNonce)

        cachedBlockIndex = -1
        cachedBlockData = null

        currentPosition = dataSpec.position
        val decryptedLength = CryptoEngine.getDecryptedLength(fullTierEncryptedSize)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            decryptedLength - currentPosition
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val blockIndex = currentPosition / 4096
        val blockOffset = (currentPosition % 4096).toInt()
        val blockRemaining = 4096 - blockOffset
        val bytesFromThisBlock = minOf(toRead, blockRemaining)

        val decryptedBlock = getOrDecryptBlock(blockIndex)
        if (decryptedBlock == null || decryptedBlock.isEmpty()) return C.RESULT_END_OF_INPUT

        val actualToCopy = minOf(bytesFromThisBlock, decryptedBlock.size - blockOffset)
        if (actualToCopy <= 0) return C.RESULT_END_OF_INPUT

        System.arraycopy(decryptedBlock, blockOffset, buffer, offset, actualToCopy)

        currentPosition += actualToCopy
        bytesRemaining -= actualToCopy
        bytesTransferred(actualToCopy)

        return actualToCopy
    }

    private fun getOrDecryptBlock(blockIndex: Long): ByteArray? {
        if (blockIndex == cachedBlockIndex && cachedBlockData != null) return cachedBlockData

        // Start tính từ offsetFull, bỏ qua 12 byte Nonce của Full tier
        val encryptedBlockStart = offsetFull + 12 + blockIndex * 4112
        if (encryptedBlockStart >= offsetFull + fullTierEncryptedSize) return null

        val encryptedBlockSize = minOf(4112, offsetFull + fullTierEncryptedSize - encryptedBlockStart)
        if (encryptedBlockSize <= 16) return null

        val byteBuffer = ByteBuffer.allocate(encryptedBlockSize.toInt())
        var bytesRead = 0
        while (bytesRead < encryptedBlockSize) {
            val count = fileChannel!!.read(byteBuffer, encryptedBlockStart + bytesRead)
            if (count == -1) break
            bytesRead += count
        }

        if (bytesRead <= 16) return null

        return try {
            val blockIv = CryptoEngine.calculateBlockIv(fileNonce, blockIndex)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(128, blockIv))
            val decrypted = cipher.doFinal(byteBuffer.array(), 0, bytesRead)
            cachedBlockIndex = blockIndex
            cachedBlockData = decrypted
            decrypted
        } catch (e: Exception) { null }
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        try { pfd?.close() } catch (e: IOException) {}
        finally {
            pfd = null
            fileChannel = null
            cachedBlockIndex = -1
            cachedBlockData = null
            dataSpec = null
        }
    }
}