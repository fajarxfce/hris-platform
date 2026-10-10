package dev.fajar.hris.organization.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.*
import java.util.UUID

interface OrganizationRepository {
    fun lockStructure(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun find(companyId: UUID, id: UUID): Result<OrganizationUnit?>

    fun ancestors(companyId: UUID, parentId: UUID): Result<List<OrganizationUnit>>

    fun list(companyId: UUID, search: OrganizationUnitSearch): Result<Page<OrganizationUnit>>

    fun save(companyId: UUID, change: UnitChange): Result<MutationReceipt>
}
