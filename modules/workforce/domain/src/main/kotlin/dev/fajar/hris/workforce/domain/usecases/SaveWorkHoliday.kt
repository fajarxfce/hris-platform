package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.workforce.domain.entities.WorkHoliday
import dev.fajar.hris.workforce.domain.entities.WorkPeriod
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.util.UUID

class SaveWorkHoliday(
    private val periods: WorkPeriodRepository,
    private val schedules: ScheduleRepository,
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
        holiday: WorkHoliday,
        version: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("workforce.manage")
        if (access is Result.Failed) return access
        if (
            holiday.name.isBlank() ||
                holiday.name.length > 200 ||
                (version ?: 0) < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_holiday"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "workforce.holiday_save",
                operationId,
                listOf(
                    holiday.id.toString(),
                    holiday.workDate.toString(),
                    holiday.name.trim(),
                    holiday.active.toString(),
                    version?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = schedules.lock(company)
            if (lock is Result.Failed) return@run lock
            val lockedPeriods = mutableListOf<WorkPeriod>()
            if ((replay as Result.Success).value == null) {
                val previous = schedules.findHoliday(company, holiday.id)
                if (previous is Result.Failed) return@run previous
                val months =
                    listOfNotNull((previous as Result.Success).value?.workDate, holiday.workDate)
                        .map { java.time.YearMonth.from(it) }
                        .distinct()
                        .sorted()
                for (month in months) {
                    val locked = periods.lockMonth(company, month, false)
                    if (locked is Result.Failed) return@run locked
                    lockedPeriods += (locked as Result.Success).value
                }
            }
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
            if (lockedPeriods.any(::workPeriodLocked))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "work_period_locked"))
            schedules
                .saveHoliday(actor, holiday.copy(name = holiday.name.trim()), version, reason)
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "work_holiday",
                                    holiday.id,
                                    "workforce.holiday_saved",
                                    mapOf("workDate" to holiday.workDate.toString()),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
