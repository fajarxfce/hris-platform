package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.ManagedAccount
import java.util.UUID

interface AccountAdministrationRepository {
    fun lockAdministration(): Result<Unit>

    fun lockAccount(id: UUID): Result<ManagedAccount?>

    fun list(query: String, after: UUID?, limit: Int): Result<Page<ManagedAccount>>

    fun save(
        id: UUID,
        expectedVersion: Long,
        active: Boolean,
        permissions: Set<String>,
    ): Result<MutationReceipt>
}
