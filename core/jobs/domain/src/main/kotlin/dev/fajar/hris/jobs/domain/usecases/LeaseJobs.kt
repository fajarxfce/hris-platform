package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.time.Clock
import java.util.UUID

class LeaseJobs(
    private val jobs: JobRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
    private val retry: JobRetryPolicy,
) {
    fun execute(
        owner: UUID,
        limit: Int,
        seconds: Int,
        kinds: Set<JobKind>,
    ): Result<List<JobLease>> {
        if (limit !in 1..4 || seconds !in 15..300 || kinds.isEmpty() || kinds.size > 32)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_job_lease_request"))
        val scope = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())
        val expired = transactions.run(scope) { jobs.exhausted(kinds, retry.maximumAttempts, 4) }
        if (expired is Result.Failed) return expired
        for (lease in (expired as Result.Success).value) {
            val request = lease.job.request
            val actor =
                Actor(
                    request.actorId,
                    request.companyId,
                    emptySet(),
                    request.authenticatedAt,
                    request.correlationId,
                )
            val recorded =
                transactions.run(actor) {
                    jobs.failExpired(lease, "job_attempts_exhausted").flatMap { changed ->
                        if (!changed) Result.Success(Unit)
                        else
                            journal.record(
                                actor,
                                ChangeRecord("job", request.id, "jobs.attempts_exhausted"),
                            )
                    }
                }
            if (recorded is Result.Failed) return recorded
        }
        return transactions.run(scope) {
            jobs.claim(owner, limit, seconds, kinds, retry.maximumAttempts)
        }
    }
}
