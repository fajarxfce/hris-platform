package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Executes one admitted authorization in the lease's transaction; no interactive proof is reused.
 */
class ExecuteOffboardingSchedule(
    private val schedules: OffboardingScheduleRepository,
    private val lifecycle: LifecycleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val roles: RoleTemplateRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val scope = requireOffboardingJobActor(actor, lease)
        if (scope is Result.Failed) return scope
        val request = lease.job.request
        val company = request.companyId
        return transactions.run(actor) {
            val leased = jobs.lockLease(lease)
            if (leased is Result.Failed) return@run leased
            val job =
                (leased as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.request != request)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )
            if (job.completedItems != 0)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "offboarding_progress_invalid")
                )
            val loadedSchedule = schedules.forJob(company, request.id)
            if (loadedSchedule is Result.Failed) return@run loadedSchedule
            val observed =
                (loadedSchedule as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "offboarding_schedule_obsolete")
                    )
            val peopleGuard = people.lockReportingLines(company)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val caseGuard = lifecycle.lockCase(company, observed.caseId)
            if (caseGuard is Result.Failed) return@run caseGuard
            val reread = schedules.forJob(company, request.id)
            if (reread is Result.Failed) return@run reread
            val schedule =
                (reread as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "offboarding_schedule_obsolete")
                    )
            if (
                schedule.id.toString() != request.values["scheduleId"] ||
                    schedule.jobId != request.id ||
                    schedule.createdBy != request.actorId ||
                    schedule.scheduledFor != request.scheduledFor ||
                    schedule.createdAt != request.createdAt ||
                    schedule.status != OffboardingScheduleStatus.SCHEDULED
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "offboarding_schedule_obsolete")
                )
            val binding = people.accountForEmployee(company, schedule.employmentId)
            if (binding is Result.Failed) return@run binding
            val targetAccount = (binding as Result.Success).value
            val companyGuard = companies.lock(company)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company)
            if (memberGuard is Result.Failed) return@run memberGuard
            for (id in listOfNotNull(actor.accountId, targetAccount).distinct().sorted()) {
                val guard = identities.lockAccount(id, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val access = (loadedAccess as Result.Success).value
            val authority =
                validateCompanyCommandActor(actor, access).flatMap { requireOffboardingAccess(it) }
            if (authority is Result.Failed) return@run authority
            val loadedCompany = companies.find(company)
            if (loadedCompany is Result.Failed) return@run loadedCompany
            val timezone =
                (loadedCompany as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val today = now.atZone(ZoneId.of(timezone)).toLocalDate()
            val loadedCase = lifecycle.case(company, schedule.caseId)
            if (loadedCase is Result.Failed) return@run loadedCase
            val case =
                (loadedCase as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found")
                    )
            val loadedEmployee = people.find(company, schedule.employmentId, today)
            if (loadedEmployee is Result.Failed) return@run loadedEmployee
            val employee =
                (loadedEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val valid = validateOffboardingExecution(schedule, case, employee, timezone, now)
            if (valid is Result.Failed) return@run valid
            val otherCases = lifecycle.hasOpenCases(company, employee.id, case.id)
            if (otherCases is Result.Failed) return@run otherCases
            if ((otherCases as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "other_lifecycle_cases_pending")
                )
            val scheduledChanges = people.hasRevisionsAfter(company, employee.id, today)
            if (scheduledChanges is Result.Failed) return@run scheduledChanges
            if ((scheduledChanges as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "scheduled_employment_changes_pending")
                )
            val reports = people.hasReportingDependentsAtOrAfter(company, employee.id, today)
            if (reports is Result.Failed) return@run reports
            if ((reports as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "reporting_reassignment_required")
                )
            val departure =
                if (targetAccount == null) null
                else {
                    val remaining =
                        people.hasOpenEmploymentAtOrAfter(
                            company,
                            employee.person.id,
                            employee.id,
                            today,
                        )
                    if (remaining is Result.Failed) return@run remaining
                    val loadedMember = members.find(company, targetAccount)
                    if (loadedMember is Result.Failed) return@run loadedMember
                    val member = (loadedMember as Result.Success).value
                    if (
                        (remaining as Result.Success).value ||
                            member == null ||
                            !member.membershipActive
                    )
                        null
                    else {
                        if ("identity.manage" in member.permissions) {
                            val other =
                                members.hasOtherActiveMember(
                                    company,
                                    targetAccount,
                                    "identity.manage",
                                )
                            if (other is Result.Failed) return@run other
                            if (!(other as Result.Success).value)
                                return@run Result.Failed(
                                    Failure(FailureKind.CONFLICT, "last_company_administrator")
                                )
                        }
                        val application = roles.application(company, targetAccount, member.version)
                        if (application is Result.Failed) return@run application
                        MembershipDeparture(
                            member,
                            (application as Result.Success).value
                                ?: MembershipGrant(member.permissions, emptyList()),
                        )
                    }
                }
            val ended =
                employee.terms.copy(
                    effectiveFrom = today,
                    endDate = case.targetDate,
                    status = EmploymentStatus.ENDED,
                    managerId = null,
                )
            val revised =
                people.revise(actor, employee.id, employee.version, ended, schedule.reason)
            if (revised is Result.Failed) return@run revised
            if (departure != null) {
                val member = departure.member
                val revoked =
                    members.save(company, member.id, member.version, false, member.permissions)
                if (revoked is Result.Failed) return@run revoked
                val history =
                    roles
                        .recordApplication(
                            actor,
                            member.id,
                            (revoked as Result.Success).value.version,
                            departure.grant,
                        )
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "company_membership",
                                    member.id,
                                    "people.departure_access_revoked",
                                    mapOf(
                                        "employmentId" to employee.id.toString(),
                                        "scheduleId" to schedule.id.toString(),
                                    ),
                                    schedule.reason,
                                ),
                            )
                        }
                if (history is Result.Failed) return@run history
            }
            val finishedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val event =
                LifecycleEvent(
                    case.version + 1,
                    null,
                    LifecycleAction.COMPLETED,
                    null,
                    actor.accountId,
                    schedule.reason,
                    finishedAt,
                )
            val completedCase =
                lifecycle.finish(actor, case.id, case.version, LifecycleStatus.COMPLETED, event)
            if (completedCase is Result.Failed) return@run completedCase
            val completedSchedule =
                schedules.finish(
                    company,
                    schedule.id,
                    schedule.version,
                    OffboardingScheduleStatus.COMPLETED,
                    finishedAt,
                    null,
                )
            if (completedSchedule is Result.Failed) return@run completedSchedule
            val progress =
                jobs.checkpoint(
                    lease,
                    JobProgress(
                        1,
                        mapOf(
                            "scheduleId" to schedule.id.toString(),
                            "caseVersion" to (case.version + 1).toString(),
                            "employmentVersion" to (employee.version + 1).toString(),
                        ),
                    ),
                )
            if (progress is Result.Failed) return@run progress
            if (!(progress as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            val completedJob = jobs.complete(lease, JobStatus.SUCCEEDED)
            if (completedJob is Result.Failed) return@run completedJob
            if (!(completedJob as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            val recorded =
                journal.record(
                    actor,
                    ChangeRecord(
                        "employment",
                        employee.id,
                        "people.offboarding_completed",
                        mapOf(
                            "caseId" to case.id.toString(),
                            "scheduleId" to schedule.id.toString(),
                            "lastWorkingDate" to case.targetDate.toString(),
                        ),
                        schedule.reason,
                    ),
                )
            if (recorded is Result.Failed) return@run recorded
            val finalTime = clock.instant()
            if (finalTime.atZone(ZoneId.of(timezone)).toLocalDate() != today)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "offboarding_execution_date_changed")
                )
            val continued =
                validateOffboardingExecution(schedule, case, employee, timezone, finalTime)
            if (continued is Result.Failed) return@run continued
            validateCompanyCommandActor(actor, access)
                .flatMap { requireOffboardingAccess(it) }
                .map { JobStep(1, true) }
        }
    }
}
