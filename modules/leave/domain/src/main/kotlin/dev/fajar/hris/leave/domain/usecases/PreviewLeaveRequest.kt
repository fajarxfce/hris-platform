package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.policies.resolveCalendar
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.*
import java.util.UUID

class PreviewLeaveRequest(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val schedules: ScheduleRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val cutoffs: PayrollCutoffRepository,
    private val security: IdentitySecurityPolicy,
    private val carry: LeaveCarryRepository,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        typeId: UUID,
        days: List<RequestedLeaveDay>,
        scope: LeavePreparationScope = LeavePreparationScope.SELF,
    ): Result<LeaveRequestPreview> {
        val permission =
            if (scope == LeavePreparationScope.SELF) "leave.self.manage" else "leave.manage"
        val admitted = actor.requirePermission(permission)
        if (admitted is Result.Failed) return admitted
        val selectedDays = days.take(LEAVE_DATE_LIMIT + 1)
        val valid = validateLeaveDateSelection(selectedDays)
        if (valid is Result.Failed) return valid
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val from = selectedDays.minOf { it.workDate }
        val until = selectedDays.maxOf { it.workDate }
        return transactions.run(actor) {
            val ledgerGuard = ledger.lock(company, employeeId, shared = true)
            if (ledgerGuard is Result.Failed) return@run ledgerGuard
            val scheduleGuard = schedules.lock(company, shared = true)
            if (scheduleGuard is Result.Failed) return@run scheduleGuard
            val cutoffGuard = cutoffs.lock(company)
            if (cutoffGuard is Result.Failed) return@run cutoffGuard
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
            val permissionAfterWait =
                (checked as Result.Success).value.requirePermission(permission)
            if (permissionAfterWait is Result.Failed) return@run permissionAfterWait

            val now = clock.instant()
            val settings =
                when (val result = companies.find(company)) {
                    is Result.Failed -> return@run result
                    is Result.Success ->
                        result.value
                            ?: return@run Result.Failed(
                                Failure(FailureKind.NOT_FOUND, "company_not_found")
                            )
                }
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val employee =
                when (val result = people.find(company, employeeId, until)) {
                    is Result.Failed -> return@run result
                    is Result.Success -> result.value
                }
            if (employee == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            if (scope == LeavePreparationScope.SELF) {
                val current =
                    when (val result = people.find(company, employeeId, today)) {
                        is Result.Failed -> return@run result
                        is Result.Success -> result.value
                    }
                if (
                    current?.person?.accountId != actor.accountId ||
                        !current.terms.isWorkingOn(today)
                )
                    return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            }
            val frozen =
                cutoffs.frozenMonths(
                    company,
                    employeeId,
                    selectedDays.map { YearMonth.from(it.workDate) }.toSet(),
                )
            if (frozen is Result.Failed) return@run frozen
            if ((frozen as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_frozen"))
            val calendar =
                when (
                    val result =
                        schedules
                            .calendar(company, employeeId, from, until)
                            .flatMap(::resolveCalendar)
                ) {
                    is Result.Failed -> return@run result
                    is Result.Success -> result.value
                }
            val selected = selectedDays.map { it.workDate }.toSet()
            val first =
                when (val result = leavePolicyDate(selectedDays, calendar)) {
                    is Result.Failed -> return@run result
                    is Result.Success -> result.value
                }
            val type =
                when (val result = policies.effective(company, typeId, first)) {
                    is Result.Failed -> return@run result
                    is Result.Success -> result.value
                }
            if (type == null || !type.active)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
            val history = people.effectiveRevisions(company, employeeId, from, until)
            if (history is Result.Failed) return@run history
            val planned =
                planLeaveDays(
                    selectedDays,
                    calendar,
                    (history as Result.Success).value,
                    type.policy,
                )
            if (planned is Result.Failed) return@run planned
            val charged = (planned as Result.Success).value
            val occupied = requests.occupancy(company, employeeId, from, until)
            if (occupied is Result.Failed) return@run occupied
            val overlap = validateLeaveOverlap(charged, (occupied as Result.Success).value)
            if (overlap is Result.Failed) return@run overlap
            val balances = mutableListOf<LeavePreviewBalance>()
            for (movement in leaveLedgerMovements(charged, LeaveBalanceEffect.RESERVE)) {
                val balance = ledger.balance(company, employeeId, typeId, movement.year)
                if (balance is Result.Failed) return@run balance
                val value = (balance as Result.Success).value
                val enough = validateLeaveMovement(value, movement)
                if (enough is Result.Failed) return@run enough
                balances += LeavePreviewBalance(value, movement.reservedDelta)
            }
            val acquiredCarry =
                carry.buckets(company, employeeId, typeId, balances.map { it.balance.year }.toSet())
            if (acquiredCarry is Result.Failed) return@run acquiredCarry
            val carryBuckets = (acquiredCarry as Result.Success).value
            val funding =
                planLeaveCarryReservation(charged, balances.map { it.balance }, carryBuckets, now)
            if (funding is Result.Failed) return@run funding
            val carryAllocations = (funding as Result.Success).value
            val included = charged.map { it.workDate }.toSet()
            val result =
                LeaveRequestPreview(
                    employeeId,
                    now,
                    settings.timezone,
                    LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                    charged,
                    (selected - included).sorted(),
                    balances.toList(),
                    leavePreviewRevision(
                        company,
                        employeeId,
                        settings.timezone,
                        LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                        selectedDays,
                        charged,
                        balances,
                        carryBuckets,
                        carryAllocations,
                    ),
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { result }
        }
    }
}
