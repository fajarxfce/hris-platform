package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.util.UUID

class RequestJobCancellation(
    private val jobs: JobRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
) {
    fun execute(actor: Actor, id: UUID, expectedVersion: Long): Result<BackgroundJob> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val found = jobs.find(company, id, true)
            if (found is Result.Failed) return@run found
            val job = (found as Result.Success).value
            if (
                job == null ||
                    (job.request.actorId != actor.accountId && "jobs.manage" !in actor.permissions)
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "job_not_found"))
            if (job.cancellationRequested) return@run Result.Success(job)
            if (job.status !in setOf(JobStatus.QUEUED, JobStatus.RUNNING))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_already_finished"))
            if (job.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            jobs.requestCancellation(company, id, expectedVersion).flatMap { changed ->
                if (changed == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else
                    journal
                        .record(actor, ChangeRecord("job", id, "jobs.cancellation_requested"))
                        .map { changed }
            }
        }
    }
}
