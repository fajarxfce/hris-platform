package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository

class AbortLeaveCarryExpiry(
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.LEAVE_CARRY_EXPIRY)
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
            val locked = jobs.lockLease(lease)
            if (locked is Result.Failed) return@run locked
            val current = (locked as Result.Success).value ?: return@run Result.Success(Unit)
            if (current.request != request)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
            jobs.complete(lease, JobStatus.FAILED, failure.code).flatMap { changed ->
                if (!changed) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                else
                    journal.record(
                        actor,
                        ChangeRecord(
                            "job",
                            request.id,
                            "leave.carry_expiry_stopped",
                            mapOf("failureCode" to failure.code),
                        ),
                    )
            }
        }
    }
}
