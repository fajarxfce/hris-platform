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

class ScheduleOnboarding(
    private val schedules: OnboardingScheduleRepository,
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
        command: ScheduleOnboardingCommand,
    ): Result<MutationReceipt> {
        val permission = requireOnboardingAccess(actor)
        if (permission is Result.Failed) return permission
        val valid = validateOnboardingSelection(command.selection)
        if (valid is Result.Failed) return valid
        if (
            command.employmentVersion < 0 ||
                command.templateVersion < 0 ||
                command.id == command.caseId ||
                !command.basisRevision.matches(Regex("[a-f0-9]{64}")) ||
                command.assignees.size > 64 ||
                command.assignees.keys.any { !it.matches(Regex("[a-z][a-z0-9_-]{0,47}")) } ||
                command.reason.isBlank() ||
                command.reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_onboarding_schedule"))
        val selected = command.copy(assignees = command.assignees.toMap())
        val company = requireNotNull(actor.companyId)
        val selection = selected.selection
        val key =
            OperationKey(
                "people.onboarding_schedule",
                operationId,
                listOf(
                    selected.id.toString(),
                    selected.caseId.toString(),
                    selected.employmentId.toString(),
                    selected.employmentVersion.toString(),
                    selected.templateVersion.toString(),
                    selected.basisRevision,
                    selection.templateId.toString(),
                    selection.targetDate.toString(),
                    selection.startsOn.toString(),
                    selected.reason,
                ) + selected.assignees.toSortedMap().flatMap { listOf(it.key, it.value.toString()) },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val peopleGuard = people.lockReportingLines(company)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val templateGuard = lifecycle.lockTemplates(company, shared = true)
            if (templateGuard is Result.Failed) return@run templateGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            for (id in (selected.assignees.values + actor.accountId).distinct().sorted()) {
                val accountGuard = identities.lockAccount(id, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val access = (loadedAccess as Result.Success).value
            val checked =
                validateOnboardingSession(actor, access, clock.instant(), security, recent = true)
            if (checked is Result.Failed) return@run checked
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val loadedCompany = companies.find(company)
            if (loadedCompany is Result.Failed) return@run loadedCompany
            val timezone =
                (loadedCompany as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val loadedEmployee = people.find(company, selected.employmentId, selection.targetDate)
            if (loadedEmployee is Result.Failed) return@run loadedEmployee
            val employee =
                (loadedEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.version != selected.employmentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            val loadedTemplate = lifecycle.template(company, selection.templateId)
            if (loadedTemplate is Result.Failed) return@run loadedTemplate
            val template =
                (loadedTemplate as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_template_not_found")
                    )
            if (template.version != selected.templateVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_template_version"))
            val at = clock.instant()
            val window = onboardingWindow(selection, timezone, at)
            if (
                onboardingBasisRevision(employee, template, selection, window) !=
                    selected.basisRevision
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "onboarding_schedule_changed")
                )
            val loadedAttempts = schedules.forEmployee(company, employee.id)
            if (loadedAttempts is Result.Failed) return@run loadedAttempts
            val attempts = (loadedAttempts as Result.Success).value
            val blocker =
                onboardingScheduleBlockers(employee, template, selection, window, at, attempts)
                    .firstOrNull()
            if (blocker != null) return@run Result.Failed(Failure(FailureKind.CONFLICT, blocker))
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
            val existingCase = lifecycle.case(company, selected.caseId)
            if (existingCase is Result.Failed) return@run existingCase
            if ((existingCase as Result.Success).value != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_exists"))
            if (!template.tasks.map { it.key }.containsAll(selected.assignees.keys))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_lifecycle_assignee")
                )
            for (id in selected.assignees.values.toSet()) {
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
                        Failure(FailureKind.VALIDATION, "lifecycle_assignee_unavailable")
                    )
            }
            val queueGuard = jobs.lockQueue(company)
            if (queueGuard is Result.Failed) return@run queueGuard
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val checkedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val assurance =
                validateOnboardingSession(actor, access, checkedAt, security, recent = true)
            if (assurance is Result.Failed) return@run assurance
            val currentWindow = onboardingWindow(selection, timezone, checkedAt)
            val lateBlocker =
                onboardingScheduleBlockers(
                        employee,
                        template,
                        selection,
                        currentWindow,
                        checkedAt,
                        attempts,
                    )
                    .firstOrNull()
            if (lateBlocker != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, lateBlocker))
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.PEOPLE_ONBOARD_START,
                    operationId,
                    mapOf("scheduleId" to selected.id.toString()),
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
                OnboardingSchedule(
                    selected.id,
                    selected.caseId,
                    employee.id,
                    employee.version,
                    employee.appliedRevision,
                    selection,
                    template,
                    selected.assignees,
                    timezone,
                    currentWindow.scheduledFor,
                    currentWindow.validUntil,
                    attempts.size + 1,
                    job.id,
                    actor.accountId,
                    checkedAt,
                    selected.reason,
                )
            schedules.create(actor, schedule).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "onboarding_schedule",
                                schedule.id,
                                "people.onboarding_scheduled",
                                mapOf(
                                    "employmentId" to employee.id.toString(),
                                    "caseId" to schedule.caseId.toString(),
                                    "jobId" to job.id.toString(),
                                    "startsOn" to selection.startsOn.toString(),
                                ),
                                selected.reason,
                            ),
                        )
                    }
                    .flatMap {
                        validateOnboardingSession(
                            actor,
                            access,
                            clock.instant(),
                            security,
                            recent = true,
                        )
                    }
                    .flatMap {
                        val finalTime = clock.instant()
                        val finalBlocker =
                            onboardingScheduleBlockers(
                                    employee,
                                    template,
                                    selection,
                                    onboardingWindow(selection, timezone, finalTime),
                                    finalTime,
                                    attempts,
                                )
                                .firstOrNull()
                        if (finalBlocker != null)
                            Result.Failed(Failure(FailureKind.CONFLICT, finalBlocker))
                        else Result.Success(receipt)
                    }
            }
        }
    }
}
