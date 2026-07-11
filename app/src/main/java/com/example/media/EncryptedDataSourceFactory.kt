package com.example.media

import android.content.Context
import androidx.media3.datasource.DataSource

class EncryptedDataSourceFactory(
    private val context: Context,
    private val dek: ByteArray
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return EncryptedDataSource(context, dek)
    }
}
