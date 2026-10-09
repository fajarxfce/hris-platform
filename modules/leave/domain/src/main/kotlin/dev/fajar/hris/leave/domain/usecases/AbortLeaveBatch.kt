package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*

class AbortLeaveBatch(
    private val batches: LeaveBatchRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind !in setOf(JobKind.LEAVE_ACCRUAL, JobKind.LEAVE_YEAR_CLOSE))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val actor =
            Actor(
                request.actorId,
                request.companyId,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
            )
        return transactions.run(actor) {
            val current = jobs.lockLease(lease)
            if (current is Result.Failed) return@run current
            val job = (current as Result.Success).value ?: return@run Result.Success(Unit)
            val found = batches.forJob(request.companyId, request.id, lock = true)
            if (found is Result.Failed) return@run found
            val batch = (found as Result.Success).value
            if (batch != null && batch.status == LeaveBatchStatus.RUNNING) {
                val stopped = batches.transition(request.companyId, batch, LeaveBatchStatus.STOPPED)
                if (stopped is Result.Failed) return@run stopped
            }
            val code = if (job.cancellationRequested) "job_cancelled" else failure.code
            jobs
                .complete(
                    lease,
                    if (job.cancellationRequested) JobStatus.CANCELLED else JobStatus.FAILED,
                    code,
                )
                .flatMap { changed ->
                    if (!changed) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "leave_batch",
                                batch?.id ?: request.id,
                                "leave.batch_stopped",
                                mapOf("jobId" to request.id.toString(), "failureCode" to code),
                            ),
                        )
                }
        }
    }
}
