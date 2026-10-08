package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.ManagedPersonProfile
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.util.UUID

class GetPersonProfile(
    private val profiles: PersonProfileRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, employeeId: UUID): Result<ManagedPersonProfile> =
        transactions.run(actor) {
            profiles.findForEmployee(requireNotNull(actor.companyId), employeeId).flatMap { profile
                ->
                if (
                    profile == null ||
                        !("people.profile.read" in actor.permissions ||
                            ("people.self.read" in actor.permissions &&
                                profile.profile.accountId == actor.accountId))
                )
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "person_profile_not_found"))
                else Result.Success(profile)
            }
        }
}
