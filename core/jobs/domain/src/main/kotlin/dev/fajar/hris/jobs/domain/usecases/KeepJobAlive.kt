package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository

class KeepJobAlive(private val jobs: JobRepository, private val transactions: TransactionRunner) {
    fun execute(lease: JobLease, seconds: Int): Result<LeaseHealth> {
        if (seconds !in 15..300)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_job_lease_request"))
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
            jobs.lockLease(lease).flatMap { job ->
                when {
                    job == null ->
                        jobs.find(request.companyId, request.id).map { current ->
                            if (
                                current?.status in
                                    setOf(
                                        JobStatus.SUCCEEDED,
                                        JobStatus.CANCELLED,
                                        JobStatus.FAILED,
                                    )
                            )
                                LeaseHealth.FINISHED
                            else LeaseHealth.LOST
                        }
                    job.cancellationRequested -> Result.Success(LeaseHealth.CANCELLATION_REQUESTED)
                    else ->
                        jobs.renew(lease, seconds).map {
                            if (it) LeaseHealth.ACTIVE else LeaseHealth.LOST
                        }
                }
            }
        }
    }
}
