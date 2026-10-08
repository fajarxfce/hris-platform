package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.EmployeeCalendar
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class GetEmployeeCalendar(
    private val schedules: ScheduleRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<EmployeeCalendar> {
        if (ChronoUnit.DAYS.between(from, until) !in 0..61)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_calendar_range"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            people.find(company, employeeId, until).flatMap { employee ->
                if (employee == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                else
                    people.findAtInstant(company, employeeId, clock.instant()).flatMap { current ->
                        if (!canReadWorkforce(actor, employee, current))
                            Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                        else
                            schedules
                                .calendar(company, employeeId, from, until)
                                .flatMap(::resolveCalendar)
                    }
            }
        }
    }
}
