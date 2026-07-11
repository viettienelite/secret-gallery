package com.example.crypto

import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BlockDecryptingInputStream(
    private val encryptedStream: InputStream,
    private val dek: ByteArray
) : InputStream() {

    private val fileNonce = ByteArray(12)
    private var isNonceRead = false
    
    private var blockIndex: Long = 0
    private var decryptedBuffer: ByteArray? = null
    private var bufferOffset = 0
    private var bufferLength = 0
    private var isEof = false

    private fun readNonce() {
        var read = 0
        while (read < 12) {
            val count = encryptedStream.read(fileNonce, read, 12 - read)
            if (count == -1) {
                throw java.io.IOException("Malformed file: missing 12-byte nonce header")
            }
            read += count
        }
        isNonceRead = true
    }

    private fun fillBuffer(): Boolean {
        if (isEof) return false
        if (!isNonceRead) {
            readNonce()
        }

        // Read next encrypted block (up to 4112 bytes)
        val encryptedBlock = ByteArray(4112)
        var totalRead = 0
        while (totalRead < 4112) {
            val count = encryptedStream.read(encryptedBlock, totalRead, 4112 - totalRead)
            if (count == -1) {
                break
            }
            totalRead += count
        }

        if (totalRead == 0) {
            isEof = true
            return false
        }

        if (totalRead <= 16) {
            throw java.io.IOException("Malformed encrypted block: too short ($totalRead bytes)")
        }

        // Decrypt block using calculated block-specific IV
        val blockIv = CryptoEngine.calculateBlockIv(fileNonce, blockIndex)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(dek, "AES")
        val gcmSpec = GCMParameterSpec(128, blockIv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        decryptedBuffer = cipher.doFinal(encryptedBlock, 0, totalRead)
        bufferOffset = 0
        bufferLength = decryptedBuffer?.size ?: 0
        blockIndex++
        
        return bufferLength > 0
    }

    override fun read(): Int {
        if (bufferOffset >= bufferLength) {
            if (!fillBuffer()) {
                return -1
            }
        }
        val value = decryptedBuffer!![bufferOffset].toInt() and 0xFF
        bufferOffset++
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (b.isEmpty()) return 0
        if (bufferOffset >= bufferLength) {
            if (!fillBuffer()) {
                return -1
            }
        }
        val available = bufferLength - bufferOffset
        val toCopy = minOf(len, available)
        System.arraycopy(decryptedBuffer!!, bufferOffset, b, off, toCopy)
        bufferOffset += toCopy
        return toCopy
    }

    override fun close() {
        encryptedStream.close()
    }
}
