package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit

/** Closes a stopped lease without creating a case or granting the initiating account access. */
class AbortOnboardingSchedule(
    private val schedules: OnboardingScheduleRepository,
    private val people: PeopleRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.PEOPLE_ONBOARD_START)
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
            val job = (locked as Result.Success).value ?: return@run Result.Success(Unit)
            if (job.request != request)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
            if (job.completedItems != 0)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "onboarding_progress_invalid")
                )
            val loaded = schedules.forJob(request.companyId, request.id)
            if (loaded is Result.Failed) return@run loaded
            val schedule = (loaded as Result.Success).value
            val code =
                if (job.cancellationRequested) "job_cancelled"
                else
                    failure.code.takeIf { it.matches(Regex("[a-z][a-z0-9_.]{0,79}")) }
                        ?: "onboarding_execution_failed"
            val status = if (job.cancellationRequested) JobStatus.CANCELLED else JobStatus.FAILED
            if (schedule != null) {
                val peopleGuard = people.lockReportingLines(request.companyId)
                if (peopleGuard is Result.Failed) return@run peopleGuard
                val stopped =
                    schedules.finish(
                        request.companyId,
                        schedule.id,
                        schedule.version,
                        if (status == JobStatus.CANCELLED) OnboardingScheduleStatus.CANCELLED
                        else OnboardingScheduleStatus.FAILED,
                        clock.instant().truncatedTo(ChronoUnit.MICROS),
                        code,
                    )
                if (stopped is Result.Failed) return@run stopped
            }
            jobs.complete(lease, status, code).flatMap { completed ->
                if (!completed) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                else
                    journal.record(
                        actor,
                        ChangeRecord(
                            "job",
                            request.id,
                            "people.onboarding_stopped",
                            mapOf("failureCode" to code),
                        ),
                    )
            }
        }
    }
}
