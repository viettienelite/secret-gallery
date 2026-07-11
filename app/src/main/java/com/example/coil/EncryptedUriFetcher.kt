package com.example.coil

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.crypto.BlockDecryptingInputStream
import okio.buffer
import okio.source

class EncryptedUriFetcher(
    private val uri: Uri,
    private val options: Options,
    private val dek: ByteArray
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val rawInputStream = options.context.contentResolver.openInputStream(uri)
            ?: return null
        
        val decryptedStream = BlockDecryptingInputStream(rawInputStream, dek)
        val bufferedSource = decryptedStream.source().buffer()

        return SourceResult(
            source = ImageSource(bufferedSource, options.context),
            mimeType = null, // Let Coil inspect the stream signature (PNG, JPEG, Ultra HDR, etc.) dynamically
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val dek: ByteArray) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val path = data.path ?: ""
            val uriStr = data.toString()
            if (path.endsWith(".enc") || path.contains("_thumb.enc") || uriStr.contains(".enc")) {
                return EncryptedUriFetcher(data, options, dek)
            }
            return null
        }
    }
}
