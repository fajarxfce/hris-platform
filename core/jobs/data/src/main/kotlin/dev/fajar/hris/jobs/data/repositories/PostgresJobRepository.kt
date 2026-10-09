package dev.fajar.hris.jobs.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.data.datasources.JobDataSource
import dev.fajar.hris.jobs.data.mappers.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.time.Instant
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class PostgresJobRepository(private val source: JobDataSource, private val json: ObjectMapper) :
    JobRepository {
    override fun lockQueue(companyId: UUID): Result<Unit> = safeDatabaseCall {
        source.lockQueue(companyId)
    }

    override fun pendingCount(companyId: UUID): Result<Int> = safeDatabaseCall {
        source.pendingCount(companyId)
    }

    override fun create(request: JobRequest): Result<BackgroundJob> = safeDatabaseCall {
        source.insert(request.toRecord(json)).toJob(json)
    }

    override fun find(companyId: UUID, id: UUID, lock: Boolean): Result<BackgroundJob?> =
        safeDatabaseCall {
            source.find(companyId, id, lock)?.toJob(json)
        }

    override fun list(
        companyId: UUID,
        actorId: UUID?,
        size: Int,
        beforeAt: Instant?,
        beforeId: UUID?,
    ): Result<JobPage> = safeDatabaseCall {
        val rows = source.list(companyId, actorId, size + 1, beforeAt, beforeId)
        val items = rows.take(size).map { it.toJob(json) }
        val last = if (rows.size > size) items.lastOrNull() else null
        JobPage(items, last?.request?.createdAt, last?.request?.id)
    }

    override fun requestCancellation(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        requestedAt: Instant,
    ): Result<BackgroundJob?> = safeDatabaseCall {
        source.requestCancellation(companyId, id, expectedVersion, requestedAt)?.toJob(json)
    }

    override fun claim(
        owner: UUID,
        limit: Int,
        seconds: Int,
        kinds: Set<JobKind>,
        maximumAttempts: Int,
    ): Result<List<JobLease>> = safeDatabaseCall {
        source.claim(owner, limit, seconds, kinds.map { it.name }.toSet(), maximumAttempts).map {
            JobLease(it.toJob(json), it.leaseOwner, it.leaseToken, it.leaseUntil.toInstant())
        }
    }

    override fun exhausted(
        kinds: Set<JobKind>,
        maximumAttempts: Int,
        limit: Int,
    ): Result<List<JobLease>> = safeDatabaseCall {
        source.exhausted(kinds.map { it.name }.toSet(), maximumAttempts, limit).map {
            JobLease(it.toJob(json), it.leaseOwner, it.leaseToken, it.leaseUntil.toInstant())
        }
    }

    override fun failExpired(lease: JobLease, failureCode: String): Result<Boolean> =
        safeDatabaseCall {
            source.failExpired(
                lease.job.request.id,
                lease.job.request.companyId,
                lease.token,
                lease.job.version,
                failureCode,
            )
        }

    override fun renew(lease: JobLease, seconds: Int): Result<Boolean> = safeDatabaseCall {
        source.renew(lease.job.request.id, lease.owner, lease.token, seconds)
    }

    override fun lockLease(lease: JobLease): Result<BackgroundJob?> = safeDatabaseCall {
        source
            .lockLease(lease.job.request.id, lease.job.request.companyId, lease.owner, lease.token)
            ?.toJob(json)
    }

    override fun checkpoint(lease: JobLease, progress: JobProgress): Result<Boolean> =
        safeDatabaseCall {
            source.checkpoint(
                lease.job.request.id,
                lease.job.request.companyId,
                lease.owner,
                lease.token,
                progress.completedItems,
                JSONB.valueOf(json.writeValueAsString(progress.checkpoint)),
            )
        }

    override fun complete(
        lease: JobLease,
        status: JobStatus,
        failureCode: String?,
    ): Result<Boolean> = safeDatabaseCall {
        require(status in setOf(JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.CANCELLED))
        source.complete(
            lease.job.request.id,
            lease.job.request.companyId,
            lease.owner,
            lease.token,
            status.name,
            failureCode,
        )
    }

    override fun defer(lease: JobLease, delaySeconds: Long, failureCode: String): Result<Boolean> =
        safeDatabaseCall {
            require(delaySeconds in 1..300)
            source.defer(
                lease.job.request.id,
                lease.job.request.companyId,
                lease.owner,
                lease.token,
                delaySeconds,
                failureCode,
            )
        }
}
