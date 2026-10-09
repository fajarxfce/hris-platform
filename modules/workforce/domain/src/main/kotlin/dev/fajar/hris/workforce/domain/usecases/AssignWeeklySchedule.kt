package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class AssignWeeklySchedule(
    private val periods: WorkPeriodRepository,
    private val schedules: ScheduleRepository,
    private val people: PeopleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employeeId: UUID,
        effectiveFrom: LocalDate,
        days: Map<DayOfWeek, ShiftReference>,
        version: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("workforce.manage")
        if (access is Result.Failed) return access
        if (
            (version ?: 0) < 0 ||
                days.values.any { it.version < 0 } ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_schedule_change"))
        val key =
            OperationKey(
                "workforce.schedule_assign",
                operationId,
                listOf(
                    employeeId.toString(),
                    effectiveFrom.toString(),
                    version?.toString(),
                    reason,
                    days.size.toString(),
                ) +
                    days.entries
                        .sortedBy { it.key.value }
                        .flatMap {
                            listOf(it.key.name, it.value.id.toString(), it.value.version.toString())
                        },
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = schedules.lock(company)
            if (lock is Result.Failed) return@run lock
            val lockedPeriods =
                if ((replay as Result.Success).value == null)
                    periods.lockRange(company, java.time.YearMonth.from(effectiveFrom))
                else Result.Success(emptyList())
            if (lockedPeriods is Result.Failed) return@run lockedPeriods
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            val permission = live.requirePermission("workforce.manage")
            if (permission is Result.Failed) return@run permission
            replay.value?.let {
                return@run Result.Success(it)
            }
            if (
                (lockedPeriods as Result.Success).value.size > 1212 ||
                    lockedPeriods.value.any(::workPeriodLocked)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "work_period_locked"))
            val employee = people.find(company, employeeId, effectiveFrom)
            if (employee is Result.Failed) return@run employee
            if ((employee as Result.Success).value?.terms?.isWorkingOn(effectiveFrom) != true)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "employee_unavailable"))
            val snapshots = mutableMapOf<ShiftReference, ShiftSnapshot>()
            for (reference in days.values.distinct()) {
                val found = schedules.findShift(company, reference.id)
                if (found is Result.Failed) return@run found
                val shift =
                    (found as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "shift_unavailable")
                        )
                if (!shift.active)
                    return@run Result.Failed(Failure(FailureKind.VALIDATION, "shift_unavailable"))
                if (shift.version != reference.version)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "shift_changed"))
                snapshots[reference] = ShiftSnapshot(shift.id, shift.version, shift.details)
            }
            schedules
                .assign(
                    actor,
                    employeeId,
                    effectiveFrom,
                    days.mapValues { snapshots.getValue(it.value) },
                    version,
                    reason,
                )
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "employment_schedule",
                                    employeeId,
                                    "workforce.schedule_assigned",
                                    mapOf("effectiveFrom" to effectiveFrom.toString()),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
