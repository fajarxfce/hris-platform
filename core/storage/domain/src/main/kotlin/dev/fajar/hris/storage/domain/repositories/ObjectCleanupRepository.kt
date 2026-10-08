package dev.fajar.hris.storage.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.domain.entities.*
import java.time.Instant
import java.util.UUID

interface ObjectCleanupRepository {
    fun schedule(request: ObjectCleanupRequest): Result<Unit>

    fun allocatedBytes(companyId: UUID): Result<Long>

    /** Removes only unleased objects; callers publish accepted content in the same transaction. */
    fun retain(companyId: UUID, ids: Set<UUID>): Result<Int>

    fun advanceDeletion(companyId: UUID, resourceId: UUID, deleteAfter: Instant): Result<Unit>

    fun claim(
        owner: UUID,
        limit: Int,
        seconds: Int,
        maximumAttempts: Int,
    ): Result<List<ObjectCleanupLease>>

    fun exhausted(maximumAttempts: Int, limit: Int): Result<List<ObjectCleanupLease>>

    fun failExpired(lease: ObjectCleanupLease): Result<Boolean>

    fun complete(lease: ObjectCleanupLease): Result<Boolean>

    fun fail(lease: ObjectCleanupLease, code: String, retryAt: Instant?): Result<Boolean>

    fun find(companyId: UUID, id: UUID): Result<ObjectCleanupEntry?>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<ObjectCleanupEntry>>

    fun retry(companyId: UUID, id: UUID, version: Long, at: Instant): Result<MutationReceipt?>
}
