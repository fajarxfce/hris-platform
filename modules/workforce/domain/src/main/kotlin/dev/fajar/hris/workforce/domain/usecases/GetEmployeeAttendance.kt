package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class GetEmployeeAttendance(
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<AttendanceDay>> {
        if (java.time.temporal.ChronoUnit.DAYS.between(from, until) !in 0..30)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_attendance_range"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            people.find(company, employeeId, until).flatMap { employee ->
                if (employee == null)
                    return@flatMap Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
                people.findAtInstant(company, employeeId, clock.instant()).flatMap { current ->
                    if (
                        !canReadWorkforce(actor, employee, current) &&
                            !canReviewAttendance(actor, current, employee.person.accountId)
                    )
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                    else
                        attendance.entries(company, employeeId, from, until).flatMap { entries ->
                            corrections.latest(company, employeeId, from, until).map { changes ->
                                val byDate = entries.groupBy { it.capture.workDate }
                                val correctedDates = changes.associateBy { it.workDate }
                                (0..java.time.temporal.ChronoUnit.DAYS.between(from, until)).map {
                                    day ->
                                    val date = from.plusDays(day)
                                    summarizeAttendance(
                                        date,
                                        byDate[date].orEmpty(),
                                        correctedDates[date],
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}
