package com.example.coil

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.crypto.CryptoEngine
import okio.buffer
import okio.source

class EncryptedUriFetcher(
    private val uri: Uri,
    private val options: Options,
    private val dek: ByteArray
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val tierParam = uri.getQueryParameter("tier") ?: "FULL"
        val tier = when(tierParam) {
            "THUMB" -> CryptoEngine.Tier.THUMB
            "SCREEN" -> CryptoEngine.Tier.SCREEN
            else -> CryptoEngine.Tier.FULL
        }

        val decryptedStream = CryptoEngine.getSgv2TierStream(options.context, uri, tier, dek)
            ?: return null

        val bufferedSource = decryptedStream.source().buffer()

        return SourceResult(
            source = ImageSource(bufferedSource, options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val dek: ByteArray) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uriStr = data.toString()
            if (uriStr.contains(".enc")) {
                return EncryptedUriFetcher(data, options, dek)
            }
            return null
        }
    }
}