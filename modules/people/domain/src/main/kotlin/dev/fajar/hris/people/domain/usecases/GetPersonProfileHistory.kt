package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.PersonProfileRevision
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.util.UUID

class GetPersonProfileHistory(
    private val profiles: PersonProfileRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PersonProfileRevision>> {
        val access = actor.requirePermission("people.profile.read")
        if (access is Result.Failed) return access
        if (limit !in 1..200 || (after ?: 0) < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_pagination"))
        return transactions.run(actor) {
            profiles.findForEmployee(requireNotNull(actor.companyId), employeeId).flatMap { profile
                ->
                if (profile == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "person_profile_not_found"))
                else profiles.history(profile.profile.id, after, limit)
            }
        }
    }
}
