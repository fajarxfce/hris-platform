package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.util.UUID

interface PersonProfileRepository {
    fun findForEmployee(companyId: UUID, employeeId: UUID): Result<ManagedPersonProfile?>

    fun history(personId: UUID, after: Long?, limit: Int): Result<Page<PersonProfileRevision>>

    fun save(
        actor: Actor,
        profile: PersonProfile,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt>
}
