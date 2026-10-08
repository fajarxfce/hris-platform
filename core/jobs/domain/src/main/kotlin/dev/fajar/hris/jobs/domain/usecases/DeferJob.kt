package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository

class DeferJob(
    private val jobs: JobRepository,
    private val transactions: TransactionRunner,
    private val policy: JobRetryPolicy,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Boolean> {
        if (failure.kind != FailureKind.UNAVAILABLE || lease.job.attempts >= policy.maximumAttempts)
            return Result.Success(false)
        val request = lease.job.request
        val actor =
            Actor(
                request.actorId,
                request.companyId,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
            )
        return transactions.run(actor) {
            jobs.defer(lease, policy.delaySeconds(lease.job.attempts), failure.code)
        }
    }
}
