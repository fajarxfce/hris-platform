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

class ScheduleOffboarding(
    private val schedules: OffboardingScheduleRepository,
    private val lifecycle: LifecycleRepository,
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
        command: ScheduleOffboardingCommand,
    ): Result<MutationReceipt> {
        val permission = requireOffboardingAccess(actor)
        if (permission is Result.Failed) return permission
        if (
            command.expectedVersion < 0 ||
                command.employmentVersion < 0 ||
                !command.basisRevision.matches(Regex("[a-f0-9]{64}")) ||
                command.reason.isBlank() ||
                command.reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_offboarding_schedule"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.offboarding_schedule",
                operationId,
                listOf(
                    command.id.toString(),
                    command.caseId.toString(),
                    command.expectedVersion.toString(),
                    command.employmentVersion.toString(),
                    command.basisRevision,
                    command.reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val peopleGuard = people.lockReportingLines(company)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val caseGuard = lifecycle.lockCase(company, command.caseId)
            if (caseGuard is Result.Failed) return@run caseGuard
            val found = lifecycle.case(company, command.caseId)
            if (found is Result.Failed) return@run found
            val case = (found as Result.Success).value
            val binding =
                if (case == null) Result.Success(null)
                else people.accountForEmployee(company, case.employmentId)
            if (binding is Result.Failed) return@run binding
            val targetAccount = (binding as Result.Success).value
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            for (id in listOfNotNull(actor.accountId, targetAccount).distinct().sorted()) {
                val guard = identities.lockAccount(id, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val access = (loadedAccess as Result.Success).value
            val checked =
                validateOffboardingSession(actor, access, clock.instant(), security, recent = true)
            if (checked is Result.Failed) return@run checked
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (case == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found"))
            if (case.version != command.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val loadedCompany = companies.find(company)
            if (loadedCompany is Result.Failed) return@run loadedCompany
            val timezone =
                (loadedCompany as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val window = offboardingWindow(case.targetDate, timezone, clock.instant())
            val loadedEmployee = people.find(company, case.employmentId, window.today)
            if (loadedEmployee is Result.Failed) return@run loadedEmployee
            val employee =
                (loadedEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.version != command.employmentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            if (employee.person.accountId == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "cannot_complete_own_offboarding")
                )
            if (offboardingBasisRevision(case, employee, window) != command.basisRevision)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "offboarding_schedule_changed")
                )
            val basisBlocker =
                offboardingBasisBlockers(case, employee, actor.accountId).firstOrNull()
            if (basisBlocker != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, basisBlocker))
            val loadedAttempts = schedules.forCase(company, case.id)
            if (loadedAttempts is Result.Failed) return@run loadedAttempts
            val attempts = (loadedAttempts as Result.Success).value
            val blocker =
                offboardingScheduleBlockers(case, window, clock.instant(), attempts).firstOrNull()
            if (blocker != null) return@run Result.Failed(Failure(FailureKind.CONFLICT, blocker))
            val otherCases = lifecycle.hasOpenCases(company, employee.id, case.id)
            if (otherCases is Result.Failed) return@run otherCases
            if ((otherCases as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "other_lifecycle_cases_pending")
                )
            val futureChanges = people.hasRevisionsAfter(company, employee.id, window.today)
            if (futureChanges is Result.Failed) return@run futureChanges
            if ((futureChanges as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "scheduled_employment_changes_pending")
                )
            val executeOn = case.targetDate.plusDays(1)
            val reports = people.hasReportingDependentsAtOrAfter(company, employee.id, executeOn)
            if (reports is Result.Failed) return@run reports
            if ((reports as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "reporting_reassignment_required")
                )
            if (targetAccount != null) {
                val remaining =
                    people.hasOpenEmploymentAtOrAfter(
                        company,
                        employee.person.id,
                        employee.id,
                        executeOn,
                    )
                if (remaining is Result.Failed) return@run remaining
                val membership = members.find(company, targetAccount)
                if (membership is Result.Failed) return@run membership
                val member = (membership as Result.Success).value
                if (
                    !(remaining as Result.Success).value &&
                        member?.membershipActive == true &&
                        "identity.manage" in member.permissions
                ) {
                    val other =
                        members.hasOtherActiveMember(company, targetAccount, "identity.manage")
                    if (other is Result.Failed) return@run other
                    if (!(other as Result.Success).value)
                        return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "last_company_administrator")
                        )
                }
            }
            val queueGuard = jobs.lockQueue(company)
            if (queueGuard is Result.Failed) return@run queueGuard
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val checkedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val assurance =
                validateOffboardingSession(actor, access, checkedAt, security, recent = true)
            if (assurance is Result.Failed) return@run assurance
            val currentWindow = offboardingWindow(case.targetDate, timezone, checkedAt)
            val lateBlocker =
                offboardingScheduleBlockers(case, currentWindow, checkedAt, attempts).firstOrNull()
            if (lateBlocker != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, lateBlocker))
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.PEOPLE_OFFBOARD,
                    operationId,
                    mapOf("scheduleId" to command.id.toString()),
                    actor.authenticatedAt,
                    requireNotNull(access).account.securityVersion,
                    actor.correlationId,
                    checkedAt,
                    1,
                    scheduledFor = currentWindow.scheduledFor,
                )
            val queued = jobs.create(job)
            if (queued is Result.Failed) return@run queued
            val schedule =
                OffboardingSchedule(
                    command.id,
                    case.id,
                    employee.id,
                    case.version,
                    employee.version,
                    employee.appliedRevision,
                    employee.person.accountId,
                    case.targetDate,
                    timezone,
                    currentWindow.scheduledFor,
                    currentWindow.validUntil,
                    attempts.size + 1,
                    job.id,
                    actor.accountId,
                    checkedAt,
                    command.reason,
                )
            schedules.create(actor, schedule).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "offboarding_schedule",
                                schedule.id,
                                "people.offboarding_scheduled",
                                mapOf(
                                    "caseId" to case.id.toString(),
                                    "jobId" to job.id.toString(),
                                    "lastWorkingDate" to case.targetDate.toString(),
                                ),
                                command.reason,
                            ),
                        )
                    }
                    .flatMap {
                        validateOffboardingSession(
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
}
