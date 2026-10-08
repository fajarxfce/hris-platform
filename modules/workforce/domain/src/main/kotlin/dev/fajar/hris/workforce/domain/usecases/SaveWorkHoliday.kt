package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.WorkHoliday
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.util.UUID

class SaveWorkHoliday(
    private val periods: WorkPeriodRepository,
    private val schedules: ScheduleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
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
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val lock = schedules.lock(requireNotNull(actor.companyId))
            if (lock is Result.Failed) return@run lock
            val company = requireNotNull(actor.companyId)
            val previous = schedules.findHoliday(company, holiday.id)
            if (previous is Result.Failed) return@run previous
            val months =
                listOfNotNull((previous as Result.Success).value?.workDate, holiday.workDate)
                    .map { java.time.YearMonth.from(it) }
                    .distinct()
                    .sorted()
            for (month in months) {
                val mutable =
                    periods.lockMonth(company, month, false).flatMap(::requireMutablePeriod)
                if (mutable is Result.Failed) return@run mutable
            }
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
