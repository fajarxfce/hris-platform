package dev.fajar.hris.jobs.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.util.UUID

interface JobRepository {
    fun lockQueue(companyId: UUID): Result<Unit>

    fun pendingCount(companyId: UUID): Result<Int>

    fun create(request: JobRequest): Result<BackgroundJob>

    fun find(companyId: UUID, id: UUID, lock: Boolean = false): Result<BackgroundJob?>

    fun list(
        companyId: UUID,
        actorId: UUID?,
        size: Int,
        beforeAt: Instant?,
        beforeId: UUID?,
    ): Result<JobPage>

    fun requestCancellation(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        requestedAt: Instant,
    ): Result<BackgroundJob?>

    fun claim(
        owner: UUID,
        limit: Int,
        seconds: Int,
        kinds: Set<JobKind> = JobKind.entries.toSet(),
        maximumAttempts: Int = 8,
    ): Result<List<JobLease>>

    fun exhausted(kinds: Set<JobKind>, maximumAttempts: Int, limit: Int): Result<List<JobLease>>

    fun failExpired(lease: JobLease, failureCode: String): Result<Boolean>

    fun renew(lease: JobLease, seconds: Int): Result<Boolean>

    fun lockLease(lease: JobLease): Result<BackgroundJob?>

    fun checkpoint(lease: JobLease, progress: JobProgress): Result<Boolean>

    fun complete(lease: JobLease, status: JobStatus, failureCode: String? = null): Result<Boolean>

    fun defer(lease: JobLease, delaySeconds: Long, failureCode: String): Result<Boolean>
}
