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

    private val lock = Any()
    private var dataSpec: DataSpec? = null
    private var pfd: ParcelFileDescriptor? = null
    private var fileChannel: FileChannel? = null
    private var opened = false
    private var currentPosition: Long = 0
    private var bytesRemaining: Long = 0

    private val fileNonce = ByteArray(12)
    private var fullTierEncryptedSize: Long = 0
    private var offsetFull: Long = 0

    // Hỗ trợ ánh xạ phân đoạn video nhúng bên trong Live Photo (?live=1)
    private var virtualBaseOffset: Long = 0

    private var cachedBlockIndex: Long = -1
    private var cachedBlockData: ByteArray? = null

    override fun open(dataSpec: DataSpec): Long = synchronized(lock) {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        val uri = dataSpec.uri
        val isLivePhotoVideo = uri.getQueryParameter("live") == "1"

        try {
            val openedPfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Failed to open file descriptor for $uri")
            pfd = openedPfd
            val fileInputStream = FileInputStream(openedPfd.fileDescriptor)
            val channel = fileInputStream.channel
            fileChannel = channel
            val totalFileSize = channel.size()

            if (totalFileSize < 96) throw IOException("File too small to be SGV2 ($totalFileSize bytes)")

            // Parse SGV2 Header to find Tier 3 (Full) Offset & Live Photo slice
            val headerBuffer = ByteBuffer.allocate(96)
            channel.read(headerBuffer, 0L)
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
            headerPlain.int // flags
            headerPlain.int // width
            headerPlain.int // height
            headerPlain.long // storedOriginalSize
            val liveVideoOffset = headerPlain.long
            val liveVideoSize = headerPlain.long

            offsetFull = 96L + thumbLen + screenLen
            fullTierEncryptedSize = totalFileSize - offsetFull

            if (fullTierEncryptedSize <= 12L) {
                throw IOException("Empty or corrupt Tier 3 data in SGV2")
            }

            // Đọc Nonce của Tier 3 tại offsetFull
            val nonceBuffer = ByteBuffer.allocate(12)
            channel.read(nonceBuffer, offsetFull)
            nonceBuffer.flip()
            nonceBuffer.get(fileNonce)

            cachedBlockIndex = -1
            cachedBlockData = null

            val totalDecryptedLength = CryptoEngine.getDecryptedLength(fullTierEncryptedSize)

            val streamLength: Long = if (isLivePhotoVideo && liveVideoOffset > 0L && liveVideoSize > 0L && liveVideoOffset < totalDecryptedLength) {
                virtualBaseOffset = liveVideoOffset
                minOf(liveVideoSize, totalDecryptedLength - liveVideoOffset)
            } else {
                virtualBaseOffset = 0L
                totalDecryptedLength
            }

            currentPosition = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                (streamLength - currentPosition).coerceAtLeast(0L)
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            closeInternal()
            throw if (e is IOException) e else IOException("Failed to open SGV2 data source: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = synchronized(lock) {
        if (length == 0) return 0
        if (bytesRemaining == 0L || !opened) return C.RESULT_END_OF_INPUT

        val channel = fileChannel ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val absolutePlainPosition = virtualBaseOffset + currentPosition
        val blockIndex = absolutePlainPosition / 4096
        val blockOffset = (absolutePlainPosition % 4096).toInt()
        val blockRemaining = 4096 - blockOffset
        val bytesFromThisBlock = minOf(toRead, blockRemaining)

        val decryptedBlock = getOrDecryptBlock(channel, blockIndex)
        if (decryptedBlock == null || decryptedBlock.isEmpty()) return C.RESULT_END_OF_INPUT

        val actualToCopy = minOf(bytesFromThisBlock, decryptedBlock.size - blockOffset)
        if (actualToCopy <= 0) return C.RESULT_END_OF_INPUT

        System.arraycopy(decryptedBlock, blockOffset, buffer, offset, actualToCopy)

        currentPosition += actualToCopy
        bytesRemaining -= actualToCopy
        bytesTransferred(actualToCopy)

        return actualToCopy
    }

    private fun getOrDecryptBlock(channel: FileChannel, blockIndex: Long): ByteArray? {
        if (blockIndex == cachedBlockIndex && cachedBlockData != null) return cachedBlockData

        val encryptedBlockStart = offsetFull + 12 + blockIndex * 4112
        if (encryptedBlockStart >= offsetFull + fullTierEncryptedSize) return null

        val encryptedBlockSize = minOf(4112, offsetFull + fullTierEncryptedSize - encryptedBlockStart)
        if (encryptedBlockSize <= 16) return null

        val byteBuffer = ByteBuffer.allocate(encryptedBlockSize.toInt())
        var bytesRead = 0
        while (bytesRead < encryptedBlockSize) {
            val count = try {
                channel.read(byteBuffer, encryptedBlockStart + bytesRead)
            } catch (_: Exception) {
                -1
            }
            if (count <= 0) break
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
        } catch (_: Exception) {
            null
        }
    }

    override fun getUri(): Uri? = dataSpec?.uri

    private fun closeInternal() {
        if (opened) {
            opened = false
            try { transferEnded() } catch (_: Exception) {}
        }
        try { fileChannel?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        fileChannel = null
        pfd = null
        cachedBlockIndex = -1
        cachedBlockData = null
        dataSpec = null
        virtualBaseOffset = 0L
        currentPosition = 0L
        bytesRemaining = 0L
    }

    override fun close(): Unit = synchronized(lock) {
        closeInternal()
    }
}