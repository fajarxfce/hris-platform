package dev.fajar.hris.storage.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.storage.domain.entities.*
import java.util.UUID

interface ObjectStorageRepository {
    /** One bounded, ordered company-prefix page. Consumers own scan policy and authorization. */
    fun list(companyId: UUID, afterKey: String?, limit: Int): Result<ObjectInventoryPage>

    /**
     * Writes a private object. Callers allocate a unique key per attempt to preserve immutability.
     */
    fun put(key: String, bytes: ByteArray): Result<StoredObject>

    fun metadata(key: String): Result<StoredObject?>

    /** A bounded exact range of the previously observed immutable object. */
    fun read(key: String, offset: Long, length: Int, etag: String): Result<ByteArray>

    fun delete(key: String): Result<Unit>
}
