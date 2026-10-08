package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.EmploymentRevision
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID

class GetEmploymentHistory(
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<EmploymentRevision>> =
        actor.requirePermission("people.read").flatMap {
            if (limit !in 1..200 || (after ?: 0) < 0)
                Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            else
                transactions.run(actor) {
                    people.history(requireNotNull(actor.companyId), id, after, limit)
                }
        }
}
