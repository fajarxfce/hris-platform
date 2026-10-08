package dev.fajar.hris.storage.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.data.datasources.ObjectCleanupDataSource
import dev.fajar.hris.storage.data.mappers.*
import dev.fajar.hris.storage.domain.entities.*
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PostgresObjectCleanupRepository(private val source: ObjectCleanupDataSource) :
    ObjectCleanupRepository {
    override fun schedule(request: ObjectCleanupRequest): Result<Unit> = safeDatabaseCall {
        source.insert(request.toRecord())
    }

    override fun allocatedBytes(companyId: UUID): Result<Long> = safeDatabaseCall {
        source.bytes(companyId)
    }

    override fun retain(companyId: UUID, ids: Set<UUID>): Result<Int> = safeDatabaseCall {
        source.retain(companyId, ids)
    }

    override fun advanceDeletion(
        companyId: UUID,
        resourceId: UUID,
        deleteAfter: Instant,
    ): Result<Unit> = safeDatabaseCall {
        source.advance(companyId, resourceId, OffsetDateTime.ofInstant(deleteAfter, ZoneOffset.UTC))
    }

    override fun claim(
        owner: UUID,
        limit: Int,
        seconds: Int,
        maximumAttempts: Int,
    ): Result<List<ObjectCleanupLease>> = safeDatabaseCall {
        source.claim(owner, limit, seconds, maximumAttempts).map { it.toLease() }
    }

    override fun exhausted(maximumAttempts: Int, limit: Int): Result<List<ObjectCleanupLease>> =
        safeDatabaseCall {
            source.exhausted(maximumAttempts, limit).map { it.toLease() }
        }

    override fun failExpired(lease: ObjectCleanupLease): Result<Boolean> = safeDatabaseCall {
        source.failExpired(lease.entry.request.id, lease.token) == 1
    }

    override fun complete(lease: ObjectCleanupLease): Result<Boolean> = safeDatabaseCall {
        source.complete(lease.entry.request.id, lease.token) == 1
    }

    override fun fail(lease: ObjectCleanupLease, code: String, retryAt: Instant?): Result<Boolean> =
        safeDatabaseCall {
            source.fail(
                lease.entry.request.id,
                lease.token,
                code,
                if (retryAt == null) "FAILED" else "PENDING",
                OffsetDateTime.ofInstant(retryAt ?: lease.entry.request.deleteAfter, ZoneOffset.UTC),
            ) == 1
        }

    override fun find(companyId: UUID, id: UUID): Result<ObjectCleanupEntry?> = safeDatabaseCall {
        source.find(companyId, id)?.toEntry()
    }

    override fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<ObjectCleanupEntry>> =
        safeDatabaseCall {
            val rows = source.list(companyId, after, limit + 1)
            Page(
                rows.take(limit).map { it.toEntry() },
                if (rows.size > limit) rows[limit - 1].id.toString() else null,
            )
        }

    override fun retry(
        companyId: UUID,
        id: UUID,
        version: Long,
        at: Instant,
    ): Result<MutationReceipt?> = safeDatabaseCall {
        source.retry(companyId, id, version, OffsetDateTime.ofInstant(at, ZoneOffset.UTC))?.let {
            MutationReceipt(id, it)
        }
    }
}
