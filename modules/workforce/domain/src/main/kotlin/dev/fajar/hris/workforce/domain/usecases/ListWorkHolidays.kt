package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.WorkHoliday
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ListWorkHolidays(
    private val schedules: ScheduleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, from: LocalDate, until: LocalDate): Result<List<WorkHoliday>> =
        actor.requirePermission("company.read").flatMap {
            if (ChronoUnit.DAYS.between(from, until) !in 0..365)
                Result.Failed(Failure(FailureKind.VALIDATION, "invalid_calendar_range"))
            else
                transactions.run(actor) {
                    schedules.holidays(requireNotNull(actor.companyId), from, until)
                }
        }
}
