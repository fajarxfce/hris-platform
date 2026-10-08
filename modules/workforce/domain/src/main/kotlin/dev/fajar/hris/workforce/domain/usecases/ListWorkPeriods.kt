package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.WorkPeriod
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class ListWorkPeriods(
    private val periods: WorkPeriodRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, from: YearMonth, until: YearMonth): Result<List<WorkPeriod>> {
        val access = actor.requirePermission("workforce.read")
        if (access is Result.Failed) return access
        if (
            from.year !in 2000..2100 ||
                until.year !in 2000..2100 ||
                ChronoUnit.MONTHS.between(from, until) !in 0..23
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_range"))
        return transactions.run(actor) {
            periods.list(requireNotNull(actor.companyId), from, until)
        }
    }
}
