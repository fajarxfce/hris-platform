package dev.fajar.hris.jobs.data.datasources

import dev.fajar.hris.schema.tables.records.BackgroundJobsRecord
import java.time.Instant
import java.util.UUID
import org.jooq.JSONB

interface JobDataSource {
    fun lockQueue(companyId: UUID)

    fun pendingCount(companyId: UUID): Int

    fun insert(record: BackgroundJobsRecord): BackgroundJobsRecord

    fun find(companyId: UUID, id: UUID, lock: Boolean): BackgroundJobsRecord?

    fun list(
        companyId: UUID,
        actorId: UUID?,
        size: Int,
        beforeAt: Instant?,
        beforeId: UUID?,
    ): List<BackgroundJobsRecord>

    fun requestCancellation(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        requestedAt: Instant,
    ): BackgroundJobsRecord?

    fun claim(
        owner: UUID,
        limit: Int,
        seconds: Int,
        kinds: Set<String>,
        maximumAttempts: Int,
    ): List<BackgroundJobsRecord>

    fun exhausted(kinds: Set<String>, maximumAttempts: Int, limit: Int): List<BackgroundJobsRecord>

    fun failExpired(
        id: UUID,
        companyId: UUID,
        token: UUID,
        version: Long,
        failureCode: String,
    ): Boolean

    fun renew(id: UUID, owner: UUID, token: UUID, seconds: Int): Boolean

    fun lockLease(id: UUID, companyId: UUID, owner: UUID, token: UUID): BackgroundJobsRecord?

    fun checkpoint(
        id: UUID,
        companyId: UUID,
        owner: UUID,
        token: UUID,
        completed: Int,
        checkpoint: JSONB,
    ): Boolean

    fun complete(
        id: UUID,
        companyId: UUID,
        owner: UUID,
        token: UUID,
        status: String,
        failureCode: String?,
    ): Boolean

    fun defer(
        id: UUID,
        companyId: UUID,
        owner: UUID,
        token: UUID,
        seconds: Long,
        failureCode: String,
    ): Boolean
}
