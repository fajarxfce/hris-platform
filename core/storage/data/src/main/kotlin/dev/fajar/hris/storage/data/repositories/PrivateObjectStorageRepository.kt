package dev.fajar.hris.storage.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.data.datasources.ObjectStorageDataSource
import dev.fajar.hris.storage.data.errors.safeObjectStorageCall
import dev.fajar.hris.storage.data.validation.*
import dev.fajar.hris.storage.domain.entities.StoredObject
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.security.MessageDigest
import java.util.HexFormat

class PrivateObjectStorageRepository(private val source: ObjectStorageDataSource) :
    ObjectStorageRepository {
    override fun put(key: String, bytes: ByteArray): Result<StoredObject> = safeObjectStorageCall {
        requireObjectKey(key)
        require(bytes.size in 1..MAXIMUM_OBJECT_BYTES)
        val content = bytes.copyOf()
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))
        val result = source.put(key, content, hash)
        StoredObject(key, result.size, result.etag, result.sha256)
    }

    override fun metadata(key: String): Result<StoredObject?> {
        val result = safeObjectStorageCall {
            requireObjectKey(key)
            source.metadata(key).let { StoredObject(key, it.size, it.etag, it.sha256) }
        }
        return if (result is Result.Failed && result.failure.kind == FailureKind.NOT_FOUND)
            Result.Success(null)
        else result
    }

    override fun read(key: String, offset: Long, length: Int, etag: String): Result<ByteArray> =
        safeObjectStorageCall {
            requireObjectKey(key)
            require(
                offset >= 0 &&
                    offset <= Long.MAX_VALUE - MAXIMUM_OBJECT_BYTES &&
                    length in 1..MAXIMUM_OBJECT_BYTES
            )
            require(etag.isNotBlank() && etag.length <= 200 && etag.none { it.isISOControl() })
            source.read(key, offset, length, etag)
        }

    override fun delete(key: String): Result<Unit> = safeObjectStorageCall {
        requireObjectKey(key)
        source.delete(key)
    }
}
