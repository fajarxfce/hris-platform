package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface RoleTemplateDataSource {
    fun lock(companyId: UUID)

    fun find(companyId: UUID, id: UUID): CompanyRoleTemplatesRecord?

    fun count(companyId: UUID): Int

    fun list(companyId: UUID, after: UUID?, limit: Int): List<CompanyRoleTemplatesRecord>

    fun insert(row: CompanyRoleTemplatesRecord)

    fun update(row: CompanyRoleTemplatesRecord, expectedVersion: Long): Long?

    fun insertRevision(row: RoleTemplateRevisionsRecord)

    fun insertApplication(row: MembershipRoleApplicationsRecord)

    fun application(companyId: UUID, accountId: UUID, membershipVersion: Long): String?
}
