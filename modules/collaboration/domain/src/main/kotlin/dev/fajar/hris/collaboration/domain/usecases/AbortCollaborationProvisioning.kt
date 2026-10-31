package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository

/** Stops only the owned job. Unpublished provider candidates expire; no E2EE device is deleted. */
class AbortCollaborationProvisioning(
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (
            request.kind !in
                setOf(JobKind.COLLABORATION_PROVISION, JobKind.COLLABORATION_DIRECT_PROVISION)
        )
            return Result.Failed(
                Failure(FailureKind.UNEXPECTED, "invalid_collaboration_provisioning")
            )
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
                if (job == null || job.request != request) return@flatMap lost()
                val status =
                    if (job.cancellationRequested || failure.code == "job_cancellation_requested")
                        JobStatus.CANCELLED
                    else JobStatus.FAILED
                jobs.complete(lease, status, failure.code).flatMap { complete ->
                    if (!complete) lost()
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "collaboration_provisioning",
                                request.id,
                                "collaboration.provisioning_stopped",
                                mapOf("status" to status.name, "reason" to failure.code),
                            ),
                        )
                }
            }
        }
    }

    private fun lost() = Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
}
