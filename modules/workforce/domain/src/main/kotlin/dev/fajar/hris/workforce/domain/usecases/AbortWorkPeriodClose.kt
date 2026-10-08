package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*

/**
 * Cleanup is authorized by the fenced lease, including after the initiating account loses access.
 */
class AbortWorkPeriodClose(
    private val periods: WorkPeriodRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.WORKFORCE_CLOSE)
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
            val status = if (job.cancellationRequested) JobStatus.CANCELLED else JobStatus.FAILED
            val code = if (job.cancellationRequested) "job_cancelled" else failure.code
            val found = periods.forJob(request.companyId, request.id, true)
            if (found is Result.Failed) return@run found
            val period = (found as Result.Success).value
            if (period != null && period.status == WorkPeriodStatus.PROCESSING) {
                val changed = periods.requireReview(request.companyId, period, code)
                if (changed is Result.Failed) return@run changed
            }
            jobs.complete(lease, status, code).flatMap { changed ->
                if (!changed) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                else
                    journal.record(
                        actor,
                        ChangeRecord(
                            "job",
                            request.id,
                            "workforce.period_close_stopped",
                            mapOf("status" to status.name, "failureCode" to code),
                        ),
                    )
            }
        }
    }
}
