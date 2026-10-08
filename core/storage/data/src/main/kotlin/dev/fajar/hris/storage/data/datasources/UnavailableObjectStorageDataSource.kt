package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.storage.data.errors.ObjectStorageUnavailable
import dev.fajar.hris.storage.data.models.ObjectMetadataData

class UnavailableObjectStorageDataSource : ObjectStorageDataSource {
    override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData =
        throw ObjectStorageUnavailable()

    override fun metadata(key: String): ObjectMetadataData = throw ObjectStorageUnavailable()

    override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray =
        throw ObjectStorageUnavailable()

    override fun delete(key: String): Unit = throw ObjectStorageUnavailable()

    override fun close() {}
}
