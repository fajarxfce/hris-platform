package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository

class AbortAnnouncementPublication(
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.ANNOUNCEMENT_PUBLISH)
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
            val found = jobs.lockLease(lease)
            if (found is Result.Failed) return@run found
            val job = (found as Result.Success).value ?: return@run Result.Success(Unit)
            val code = if (job.cancellationRequested) "job_cancelled" else failure.code
            jobs
                .complete(
                    lease,
                    if (job.cancellationRequested) JobStatus.CANCELLED else JobStatus.FAILED,
                    code,
                )
                .flatMap { complete ->
                    if (!complete) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "job",
                                request.id,
                                "communications.publication_stopped",
                                mapOf("failureCode" to code),
                            ),
                        )
                }
        }
    }
}
