package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit

/** Creates the reviewed case atomically under the retained job lease and current authority. */
class ExecuteOnboardingSchedule(
    private val schedules: OnboardingScheduleRepository,
    private val lifecycle: LifecycleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val scope = requireOnboardingJobActor(actor, lease)
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
                    Failure(FailureKind.CONFLICT, "onboarding_progress_invalid")
                )
            val peopleGuard = people.lockReportingLines(company)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val templateGuard = lifecycle.lockTemplates(company, shared = true)
            if (templateGuard is Result.Failed) return@run templateGuard
            val loadedSchedule = schedules.forJob(company, request.id)
            if (loadedSchedule is Result.Failed) return@run loadedSchedule
            val schedule =
                (loadedSchedule as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "onboarding_schedule_obsolete")
                    )
            if (
                schedule.id.toString() != request.values["scheduleId"] ||
                    schedule.jobId != request.id ||
                    schedule.createdBy != request.actorId ||
                    schedule.scheduledFor != request.scheduledFor ||
                    schedule.createdAt != request.createdAt ||
                    schedule.status != OnboardingScheduleStatus.SCHEDULED
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "onboarding_schedule_obsolete")
                )
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            for (id in (schedule.assignees.values + actor.accountId).distinct().sorted()) {
                val accountGuard = identities.lockAccount(id, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val access = (loadedAccess as Result.Success).value
            val checked =
                validateCompanyCommandActor(actor, access).flatMap { requireOnboardingAccess(it) }
            if (checked is Result.Failed) return@run checked
            val loadedCompany = companies.find(company)
            if (loadedCompany is Result.Failed) return@run loadedCompany
            val timezone =
                (loadedCompany as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val loadedEmployee =
                people.find(company, schedule.employmentId, schedule.selection.targetDate)
            if (loadedEmployee is Result.Failed) return@run loadedEmployee
            val employee =
                (loadedEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val loadedTemplate = lifecycle.template(company, schedule.template.id)
            if (loadedTemplate is Result.Failed) return@run loadedTemplate
            val template =
                (loadedTemplate as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "onboarding_template_unavailable")
                    )
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val valid = validateOnboardingExecution(schedule, employee, template, timezone, now)
            if (valid is Result.Failed) return@run valid
            val previousClosure = lifecycle.completedOffboardingDate(company, employee.id)
            if (previousClosure is Result.Failed) return@run previousClosure
            if ((previousClosure as Result.Success).value != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "employment_offboarded"))
            val completedOnboarding =
                lifecycle.hasCompletedCase(company, employee.id, LifecycleKind.ONBOARDING)
            if (completedOnboarding is Result.Failed) return@run completedOnboarding
            if ((completedOnboarding as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "onboarding_already_completed")
                )
            val otherCases = lifecycle.hasOpenCases(company, employee.id)
            if (otherCases is Result.Failed) return@run otherCases
            if ((otherCases as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "other_lifecycle_cases_pending")
                )
            val existingCase = lifecycle.case(company, schedule.caseId)
            if (existingCase is Result.Failed) return@run existingCase
            if ((existingCase as Result.Success).value != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_exists"))
            for (id in schedule.assignees.values.toSet()) {
                val loaded = members.find(company, id)
                if (loaded is Result.Failed) return@run loaded
                val member = (loaded as Result.Success).value
                if (
                    member == null ||
                        !member.accountActive ||
                        !member.membershipActive ||
                        member.permissions.none {
                            it == "people.lifecycle.perform" || it == "people.lifecycle.manage"
                        }
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "lifecycle_assignee_unavailable")
                    )
            }
            val case = scheduledOnboardingCase(schedule, now)
            val created = lifecycle.create(actor, case, schedule.reason)
            if (created is Result.Failed) return@run created
            val finished =
                schedules.finish(
                    company,
                    schedule.id,
                    schedule.version,
                    OnboardingScheduleStatus.STARTED,
                    now,
                )
            if (finished is Result.Failed) return@run finished
            val progress =
                jobs.checkpoint(
                    lease,
                    JobProgress(
                        1,
                        mapOf(
                            "scheduleId" to schedule.id.toString(),
                            "caseId" to case.id.toString(),
                            "caseVersion" to "0",
                        ),
                    ),
                )
            if (progress is Result.Failed) return@run progress
            if (!(progress as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            val completed = jobs.complete(lease, JobStatus.SUCCEEDED)
            if (completed is Result.Failed) return@run completed
            if (!(completed as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            val recorded =
                journal.record(
                    actor,
                    ChangeRecord(
                        "lifecycle_case",
                        case.id,
                        "people.lifecycle_started",
                        mapOf(
                            "employmentId" to employee.id.toString(),
                            "kind" to case.kind.name,
                            "scheduleId" to schedule.id.toString(),
                        ),
                        schedule.reason,
                    ),
                )
            if (recorded is Result.Failed) return@run recorded
            validateOnboardingExecution(schedule, employee, template, timezone, clock.instant())
                .flatMap { validateCompanyCommandActor(actor, access) }
                .flatMap { requireOnboardingAccess(it) }
                .map { JobStep(1, true) }
        }
    }
}
