package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.LocalDate

class ListEmployees(
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        asOf: LocalDate,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<Employee>> {
        val visibility =
            when {
                "people.read" in actor.permissions -> EmployeeVisibility.ALL
                "people.team.read" in actor.permissions -> EmployeeVisibility.TEAM
                "people.self.read" in actor.permissions -> EmployeeVisibility.SELF
                else -> return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            }
        if (limit !in 1..200 || query.length > 120 || (after?.length ?: 0) > 32)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            people.list(
                requireNotNull(actor.companyId),
                actor.accountId,
                visibility,
                asOf,
                query.trim(),
                after,
                limit,
            )
        }
    }
}
