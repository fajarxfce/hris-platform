package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.policies.canReadEmployee
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class GetEmployee(
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID, asOf: LocalDate): Result<Employee> =
        transactions.run(actor) {
            val company = requireNotNull(actor.companyId)
            people.find(company, id, asOf).flatMap { employee ->
                if (employee == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                else
                    people.findAtInstant(company, id, clock.instant()).flatMap { current ->
                        if (canReadEmployee(actor, employee, current)) Result.Success(employee)
                        else Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                    }
            }
        }
}
