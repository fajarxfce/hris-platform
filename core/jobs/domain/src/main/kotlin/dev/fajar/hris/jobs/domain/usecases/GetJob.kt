package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.util.UUID

class GetJob(private val jobs: JobRepository, private val transactions: TransactionRunner) {
    fun execute(actor: Actor, id: UUID): Result<BackgroundJob> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            jobs.find(company, id).flatMap { job ->
                if (
                    job == null ||
                        (job.request.actorId != actor.accountId &&
                            "jobs.read" !in actor.permissions)
                )
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "job_not_found"))
                else Result.Success(job)
            }
        }
    }
}
