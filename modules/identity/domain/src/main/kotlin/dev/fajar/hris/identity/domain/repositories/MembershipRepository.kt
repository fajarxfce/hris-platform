package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.identity.domain.entities.MemberReference
import java.util.UUID

interface MembershipRepository {
    fun hasOtherActiveMember(
        companyId: UUID,
        exceptAccountId: UUID,
        permission: String,
    ): Result<Boolean>

    fun lock(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun find(companyId: UUID, accountId: UUID): Result<MemberAccount?>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<MemberAccount>>

    fun activeReferences(
        companyId: UUID,
        permissions: Set<String>,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<MemberReference>>

    fun candidates(
        companyId: UUID,
        accountIds: Set<UUID>,
        permissions: Set<String>,
        limit: Int,
    ): Result<List<MemberAccount>>

    fun save(
        companyId: UUID,
        accountId: UUID,
        expectedVersion: Long?,
        active: Boolean,
        permissions: Set<String>,
    ): Result<MutationReceipt>
}
