package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.policies.canReadEmployee
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.LocalDate
import java.util.UUID

class GetEmployee(
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID, asOf: LocalDate): Result<Employee> =
        transactions.run(actor) {
            people.find(requireNotNull(actor.companyId), id, asOf).flatMap {
                if (it == null || !canReadEmployee(actor, it))
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                else Result.Success(it)
            }
        }
}
