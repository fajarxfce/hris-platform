package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.time.Instant
import java.util.UUID

class ListJobs(private val jobs: JobRepository, private val transactions: TransactionRunner) {
    fun execute(actor: Actor, size: Int, beforeAt: Instant?, beforeId: UUID?): Result<JobPage> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (size !in 1..200 || (beforeAt == null) != (beforeId == null))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_pagination"))
        return transactions.run(actor) {
            jobs.list(
                company,
                if ("jobs.read" in actor.permissions) null else actor.accountId,
                size,
                beforeAt,
                beforeId,
            )
        }
    }
}
