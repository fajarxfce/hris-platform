package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.policies.scheduledWorkDay
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.LocalDate
import java.util.UUID

class SetRosterDay(
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
        date: LocalDate,
        reference: ShiftReference?,
        version: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("workforce.manage")
        if (access is Result.Failed) return access
        if (
            (version ?: 0) < 0 ||
                (reference?.version ?: 0) < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_roster_change"))
        val key =
            OperationKey(
                "workforce.roster_save",
                operationId,
                listOf(
                    employeeId.toString(),
                    date.toString(),
                    reference?.id?.toString(),
                    reference?.version?.toString(),
                    version?.toString(),
                    reason,
                ),
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = schedules.lock(company)
            if (lock is Result.Failed) return@run lock
            val period =
                if ((replay as Result.Success).value == null)
                    periods.lockMonth(company, java.time.YearMonth.from(date), false)
                else Result.Success(null)
            if (period is Result.Failed) return@run period
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
            val mutable = requireMutablePeriod(requireNotNull((period as Result.Success).value))
            if (mutable is Result.Failed) return@run mutable
            val employee = people.find(company, employeeId, date)
            if (employee is Result.Failed) return@run employee
            if ((employee as Result.Success).value?.terms?.isWorkingOn(date) != true)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "employee_unavailable"))
            var snapshot: ShiftSnapshot? = null
            if (reference != null) {
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
                snapshot = ShiftSnapshot(shift.id, shift.version, shift.details)
                val interval = scheduledWorkDay(date, snapshot, CalendarOrigin.ROSTER, version ?: 0)
                if (interval is Result.Failed) return@run interval
            }
            schedules.roster(actor, employeeId, date, snapshot, version, reason).flatMap { receipt
                ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "roster_day",
                                employeeId,
                                "workforce.roster_saved",
                                mapOf("workDate" to date.toString()),
                                reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
