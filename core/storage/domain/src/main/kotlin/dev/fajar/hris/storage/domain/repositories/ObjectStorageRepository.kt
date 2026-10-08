package dev.fajar.hris.storage.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.storage.domain.entities.StoredObject

interface ObjectStorageRepository {
    /**
     * Writes a private object. Callers allocate a unique key per attempt to preserve immutability.
     */
    fun put(key: String, bytes: ByteArray): Result<StoredObject>

    fun metadata(key: String): Result<StoredObject?>

    /** A bounded exact range of the previously observed immutable object. */
    fun read(key: String, offset: Long, length: Int, etag: String): Result<ByteArray>

    fun delete(key: String): Result<Unit>
}
