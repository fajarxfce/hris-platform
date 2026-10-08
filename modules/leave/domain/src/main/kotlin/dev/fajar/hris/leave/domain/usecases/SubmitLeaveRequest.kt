package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.ScheduledDay
import dev.fajar.hris.workforce.domain.policies.resolveCalendar
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.*
import java.util.UUID

class SubmitLeaveRequest(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val schedules: ScheduleRepository,
    private val approvals: ApprovalRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        employeeId: UUID,
        typeId: UUID,
        days: List<RequestedLeaveDay>,
        reason: String,
    ): Result<MutationReceipt> {
        if ("leave.manage" !in actor.permissions && "leave.self.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        val valid = validateRequestedLeaveDays(days, reason)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "leave.request_submit",
                operationId,
                listOf(id.toString(), employeeId.toString(), typeId.toString(), reason) +
                    days
                        .sortedBy { it.workDate }
                        .flatMap { listOf(it.workDate.toString(), it.portion.name) },
            )
        val company = requireNotNull(actor.companyId)
        val from = days.minOf { it.workDate }
        val until = days.maxOf { it.workDate }
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val now = clock.instant()
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val employeeResult = people.find(company, employeeId, until)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val currentResult = people.find(company, employeeId, today)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (
                "leave.manage" !in actor.permissions &&
                    (employee.person.accountId != actor.accountId ||
                        current?.terms?.isWorkingOn(today) != true)
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val lock = ledger.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val calendarResult =
                schedules.calendar(company, employeeId, from, until).flatMap(::resolveCalendar)
            if (calendarResult is Result.Failed) return@run calendarResult
            val calendar = (calendarResult as Result.Success).value
            val requestedDates = days.map { it.workDate }.toSet()
            if (
                calendar.days.any { it.workDate in requestedDates && it is ScheduledDay.Unassigned }
            )
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_schedule_missing"))
            val first =
                calendar.days
                    .filterIsInstance<ScheduledDay.Work>()
                    .firstOrNull { it.workDate in requestedDates }
                    ?.workDate
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "no_leave_working_days")
                    )
            val typeResult = policies.effective(company, typeId, first)
            if (typeResult is Result.Failed) return@run typeResult
            val type = (typeResult as Result.Success).value
            if (type == null || !type.active)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
            val history = people.effectiveRevisions(company, employeeId, from, until)
            if (history is Result.Failed) return@run history
            val planned =
                planLeaveDays(days, calendar, (history as Result.Success).value, type.policy)
            if (planned is Result.Failed) return@run planned
            val charged = (planned as Result.Success).value
            val occupied = requests.occupancy(company, employeeId, from, until)
            if (occupied is Result.Failed) return@run occupied
            val overlap = validateLeaveOverlap(charged, (occupied as Result.Success).value)
            if (overlap is Result.Failed) return@run overlap
            val movements = leaveLedgerMovements(charged, LeaveBalanceEffect.RESERVE)
            for (movement in movements) {
                val balance = ledger.balance(company, employeeId, typeId, movement.year)
                if (balance is Result.Failed) return@run balance
                val enough = validateLeaveMovement((balance as Result.Success).value, movement)
                if (enough is Result.Failed) return@run enough
            }
            val context =
                ApprovalContext(
                    UUID.randomUUID(),
                    id,
                    ApprovalKind.LEAVE,
                    actor.accountId,
                    employee.person.accountId,
                    if (current?.terms?.isWorkingOn(today) == true) current.managerAccountId
                    else null,
                    today,
                    type.code,
                    java.math.BigDecimal.ZERO,
                    now,
                )
            val templateResult =
                approvals.templates(company, ApprovalKind.LEAVE, today).flatMap {
                    selectApprovalTemplate(it, context)
                }
            if (templateResult is Result.Failed) return@run templateResult
            val template = (templateResult as Result.Success).value
            val candidateResult =
                members.candidates(
                    company,
                    approvalCandidateIds(template, context),
                    approvalCandidatePermissions(template),
                    201,
                )
            if (candidateResult is Result.Failed) return@run candidateResult
            val snapshotResult =
                snapshotApproval(template, context, (candidateResult as Result.Success).value)
            if (snapshotResult is Result.Failed) return@run snapshotResult
            val snapshot = (snapshotResult as Result.Success).value
            val request =
                LeaveRequest(
                    id,
                    employeeId,
                    employee.employeeNumber,
                    employee.person.legalName,
                    employee.person.accountId,
                    actor.accountId,
                    now,
                    LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                    charged,
                    reason,
                    LeaveStatus.PENDING,
                    snapshot.id,
                    null,
                    0,
                )
            val entries =
                movements.map {
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        employeeId,
                        typeId,
                        it.year,
                        it.kind,
                        id,
                        id,
                        it.availableDelta,
                        it.reservedDelta,
                        it.consumedDelta,
                        actor.accountId,
                        now,
                        reason,
                    )
                }
            approvals
                .create(company, snapshot)
                .flatMap { requests.create(company, request) }
                .flatMap { receipt ->
                    ledger
                        .append(company, entries)
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "leave_request",
                                    id,
                                    "leave.request_submitted",
                                    mapOf(
                                        "employmentId" to employeeId.toString(),
                                        "approvalId" to snapshot.id.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
