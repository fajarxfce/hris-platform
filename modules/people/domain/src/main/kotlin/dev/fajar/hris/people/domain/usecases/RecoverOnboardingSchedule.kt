package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class RecoverOnboardingSchedule(
    private val schedules: OnboardingScheduleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employmentId: UUID,
        id: UUID,
        expectedVersion: Long,
        expectedJobVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val permission = requireOnboardingAccess(actor)
        if (permission is Result.Failed) return permission
        if (
            expectedVersion < 0 ||
                expectedJobVersion < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_onboarding_schedule"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.onboarding_recover",
                operationId,
                listOf(
                    employmentId.toString(),
                    id.toString(),
                    expectedVersion.toString(),
                    expectedJobVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val observed = schedules.find(company, id)
            if (observed is Result.Failed) return@run observed
            val jobId = (observed as Result.Success).value?.schedule?.jobId
            val loadedJob =
                if (jobId == null) Result.Success(null) else jobs.find(company, jobId, lock = true)
            if (loadedJob is Result.Failed) return@run loadedJob
            val job = (loadedJob as Result.Success).value
            val peopleGuard = people.lockReportingLines(company)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val access = (loadedAccess as Result.Success).value
            val checked =
                validateOnboardingSession(actor, access, clock.instant(), security, recent = true)
            if (checked is Result.Failed) return@run checked
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = schedules.find(company, id)
            if (found is Result.Failed) return@run found
            val schedule =
                (found as Result.Success).value?.schedule?.takeIf {
                    it.employmentId == employmentId
                }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "onboarding_schedule_not_found")
                    )
            if (
                job == null ||
                    job.request.id != schedule.jobId ||
                    job.request.kind != JobKind.PEOPLE_ONBOARD_START ||
                    job.request.values["scheduleId"] != id.toString()
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "onboarding_schedule_obsolete")
                )
            if (schedule.version != expectedVersion || job.version != expectedJobVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (schedule.status != OnboardingScheduleStatus.SCHEDULED)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "onboarding_schedule_finished")
                )
            if (
                job.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED) ||
                    job.completedItems != 0
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "onboarding_job_not_stopped")
                )
            val failureCode =
                job.failureCode
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "onboarding_job_result_invalid")
                    )
            val changed =
                schedules.finish(
                    company,
                    id,
                    expectedVersion,
                    if (job.status == JobStatus.CANCELLED) OnboardingScheduleStatus.CANCELLED
                    else OnboardingScheduleStatus.FAILED,
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                    failureCode,
                )
            if (changed is Result.Failed) return@run changed
            val receipt = (changed as Result.Success).value
            val record =
                ChangeRecord(
                    "onboarding_schedule",
                    id,
                    "people.onboarding_schedule_recovered",
                    mapOf("jobId" to job.request.id.toString(), "failureCode" to failureCode),
                    reason,
                )

            operations
                .record(actor, key, receipt)
                .flatMap { journal.record(actor, record) }
                .flatMap {
                    validateOnboardingSession(
                        actor,
                        access,
                        clock.instant(),
                        security,
                        recent = true,
                    )
                }
                .map { receipt }
        }
    }
}
