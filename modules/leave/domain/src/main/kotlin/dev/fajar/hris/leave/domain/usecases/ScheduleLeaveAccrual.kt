package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

class ScheduleLeaveAccrual(
    private val schedules: LeaveAccrualScheduleRepository,
    private val batches: LeaveBatchRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        command: ScheduleLeaveAccrualCommand,
    ): Result<MutationReceipt> {
        val initial = actor.requirePermission(LeaveBatchKind.ACCRUAL.permission)
        if (initial is Result.Failed) return initial
        val valid = validateLeaveAccrualScheduleSelection(command.firstPeriod, command.periods)
        if (valid is Result.Failed) return valid
        val input = command.copy(employeeIds = command.employeeIds.toSet())
        if (
            input.expectedPolicyVersion < 0 ||
                input.employeeIds.size !in 1..5000 ||
                input.reason.isBlank() ||
                input.reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_accrual_schedule"))
        val company = requireNotNull(actor.companyId)
        val typeId = input.typeId
        val firstPeriod = input.firstPeriod
        val periods = input.periods
        val key =
            OperationKey(
                "leave.accrual_schedule",
                operationId,
                listOf(
                    input.id.toString(),
                    typeId.toString(),
                    firstPeriod.toString(),
                    periods.toString(),
                    input.expectedPolicyVersion.toString(),
                    input.reason,
                ) + input.employeeIds.map { it.toString() }.sorted(),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val batchGuard = batches.lock(company)
            if (batchGuard is Result.Failed) return@run batchGuard
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val permission =
                (checked as Result.Success)
                    .value
                    .requirePermission(LeaveBatchKind.ACCRUAL.permission)
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val settingsResult = companies.find(company)
            if (settingsResult is Result.Failed) return@run settingsResult
            val settings =
                (settingsResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val firstResult = policies.effective(company, typeId, firstPeriod.atDay(1))
            if (firstResult is Result.Failed) return@run firstResult
            val first =
                (firstResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                    )
            val frequency =
                first.policy.accrual?.frequency
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_accrual_not_configured")
                    )
            val monthsResult = leaveAccrualScheduleMonths(firstPeriod, periods, frequency)
            if (monthsResult is Result.Failed) return@run monthsResult
            val definitions = mutableListOf<LeaveAccrualPeriodReview>()
            for (month in (monthsResult as Result.Success).value) {
                val found =
                    if (month == firstPeriod) Result.Success(first)
                    else policies.effective(company, typeId, month.atDay(1))
                if (found is Result.Failed) return@run found
                val type =
                    (found as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                        )
                val validPolicy = validateScheduledLeavePolicy(type, frequency)
                if (validPolicy is Result.Failed) return@run validPolicy
                val active = batches.active(company, typeId, LeaveBatchKind.ACCRUAL, month)
                if (active is Result.Failed) return@run active
                if ((active as Result.Success).value)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_active"))
                definitions +=
                    LeaveAccrualPeriodReview(
                        month,
                        leaveAccrualDueAt(month, frequency, settings.timezone),
                        type,
                    )
            }

            if (definitions.any { it.type.version != input.expectedPolicyVersion })
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_policy_version"))
            val existing = people.existingEmployeeIds(company, input.employeeIds)
            if (existing is Result.Failed) return@run existing
            if ((existing as Result.Success).value != input.employeeIds)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "leave_batch_employee_unavailable")
                )
            val queue = jobs.lockQueue(company)
            if (queue is Result.Failed) return@run queue
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value > 100 - definitions.size)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val assured = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (assured is Result.Failed) return@run assured
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val targets =
                input.employeeIds
                    .sortedBy { it.toString() }
                    .mapIndexed { index, employee -> LeaveBatchTarget(index + 1, employee) }
            val entries = mutableListOf<LeaveAccrualScheduleEntry>()
            for ((index, definition) in definitions.withIndex()) {
                if (Thread.currentThread().isInterrupted)
                    throw InterruptedException("Leave schedule interrupted")
                val batchId = UUID.randomUUID()
                val request =
                    JobRequest(
                        UUID.randomUUID(),
                        company,
                        actor.accountId,
                        JobKind.LEAVE_ACCRUAL,
                        batchId,
                        mapOf("batchId" to batchId.toString()),
                        actor.authenticatedAt,
                        actor.credentialVersion ?: 0,
                        actor.correlationId,
                        now,
                        targets.size + 1,
                        scheduledFor = maxOf(now, definition.dueAt),
                    )
                val type = definition.type
                val batch =
                    LeaveBatch(
                        batchId,
                        LeaveBatchKind.ACCRUAL,
                        typeId,
                        definition.period,
                        LeavePolicySnapshot(typeId, type.code, type.appliedRevision, type.policy),
                        type.version,
                        settings.timezone,
                        actor.accountId,
                        now,
                        input.reason,
                        targets.size,
                        request.id,
                        LeaveBatchStatus.RUNNING,
                        0,
                    )
                val job = jobs.create(request)
                if (job is Result.Failed) return@run job
                val saved =
                    batches.create(
                        company,
                        batch,
                        targets,
                        LeaveBatchAttempt(request.id, 1, 0, now, input.reason),
                    )
                if (saved is Result.Failed) return@run saved
                entries +=
                    LeaveAccrualScheduleEntry(
                        index + 1,
                        batchId,
                        request.id,
                        requireNotNull(request.scheduledFor),
                    )
            }
            val schedule =
                LeaveAccrualSchedule(
                    input.id,
                    typeId,
                    first.code,
                    first.policy.name,
                    first.version,
                    frequency,
                    firstPeriod,
                    definitions.size,
                    targets.size,
                    settings.timezone,
                    actor.accountId,
                    now,
                    input.reason,
                )
            val receipt = MutationReceipt(input.id, 0)
            schedules
                .create(company, schedule, entries)
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "leave_accrual_schedule",
                            input.id,
                            "leave.accrual_scheduled",
                            mapOf(
                                "typeId" to typeId.toString(),
                                "firstPeriod" to firstPeriod.toString(),
                                "periods" to periods.toString(),
                                "employees" to targets.size.toString(),
                            ),
                            input.reason,
                        ),
                    )
                }
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    validateCompanySessionActor(actor, access, clock.instant(), security).map {
                        receipt
                    }
                }
        }
    }
}
