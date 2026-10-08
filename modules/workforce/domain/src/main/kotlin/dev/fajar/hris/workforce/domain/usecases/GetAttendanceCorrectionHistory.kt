package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.AttendanceCorrection
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.AttendanceCorrectionRepository
import java.time.*
import java.util.UUID

class GetAttendanceCorrectionHistory(
    private val corrections: AttendanceCorrectionRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        date: LocalDate,
        after: Long?,
        limit: Int,
    ): Result<Page<AttendanceCorrection>> {
        if (limit !in 1..200 || (after ?: 0) < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            people.find(company, employeeId, date).flatMap { employee ->
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
                    else corrections.history(company, employeeId, date, after, limit)
                }
            }
        }
    }
}
