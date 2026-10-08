package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.storage.data.models.*

interface ObjectStorageDataSource : AutoCloseable {
    fun list(prefix: String, afterKey: String?, limit: Int): ObjectInventoryPageData

    fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData

    fun metadata(key: String): ObjectMetadataData

    fun read(key: String, offset: Long, length: Int, etag: String): ByteArray

    fun delete(key: String)
}
