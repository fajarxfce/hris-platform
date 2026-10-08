package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.util.UUID

interface RoleTemplateRepository {
    fun lock(companyId: UUID): Result<Unit>

    fun find(companyId: UUID, id: UUID): Result<CompanyRoleTemplate?>

    fun count(companyId: UUID): Result<Int>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<CompanyRoleTemplate>>

    fun save(
        actor: Actor,
        template: CompanyRoleTemplate,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>

    fun recordApplication(
        actor: Actor,
        accountId: UUID,
        membershipVersion: Long,
        grant: MembershipGrant,
    ): Result<Unit>

    fun application(
        companyId: UUID,
        accountId: UUID,
        membershipVersion: Long,
    ): Result<MembershipGrant?>
}
