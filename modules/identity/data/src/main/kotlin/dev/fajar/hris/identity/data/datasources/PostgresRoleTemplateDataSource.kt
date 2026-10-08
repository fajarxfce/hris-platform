package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresRoleTemplateDataSource(private val sql: DSLContext) : RoleTemplateDataSource {
    override fun lock(companyId: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "roles:$companyId")
            .execute()
    }

    override fun find(companyId: UUID, id: UUID) =
        sql.selectFrom(COMPANY_ROLE_TEMPLATES)
            .where(
                COMPANY_ROLE_TEMPLATES.COMPANY_ID.eq(companyId)
                    .and(COMPANY_ROLE_TEMPLATES.ID.eq(id))
            )
            .fetchOne()

    override fun count(companyId: UUID) =
        sql.fetchCount(
            sql.selectFrom(COMPANY_ROLE_TEMPLATES)
                .where(COMPANY_ROLE_TEMPLATES.COMPANY_ID.eq(companyId))
        )

    override fun list(companyId: UUID, after: UUID?, limit: Int): List<CompanyRoleTemplatesRecord> =
        sql.selectFrom(COMPANY_ROLE_TEMPLATES)
            .where(COMPANY_ROLE_TEMPLATES.COMPANY_ID.eq(companyId))
            .and(after?.let { COMPANY_ROLE_TEMPLATES.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(COMPANY_ROLE_TEMPLATES.ID)
            .limit(limit)
            .fetch()

    override fun insert(row: CompanyRoleTemplatesRecord) {
        sql.insertInto(COMPANY_ROLE_TEMPLATES).set(row).execute()
    }

    override fun update(row: CompanyRoleTemplatesRecord, expectedVersion: Long): Long? =
        sql.update(COMPANY_ROLE_TEMPLATES)
            .set(COMPANY_ROLE_TEMPLATES.NAME, row.name)
            .set(COMPANY_ROLE_TEMPLATES.PERMISSIONS, row.permissions)
            .set(COMPANY_ROLE_TEMPLATES.ACTIVE, row.active)
            .set(COMPANY_ROLE_TEMPLATES.VERSION, expectedVersion + 1)
            .where(
                COMPANY_ROLE_TEMPLATES.COMPANY_ID.eq(row.companyId)
                    .and(COMPANY_ROLE_TEMPLATES.ID.eq(row.id))
                    .and(COMPANY_ROLE_TEMPLATES.VERSION.eq(expectedVersion))
            )
            .returning(COMPANY_ROLE_TEMPLATES.VERSION)
            .fetchOne()
            ?.version

    override fun insertRevision(row: RoleTemplateRevisionsRecord) {
        sql.insertInto(ROLE_TEMPLATE_REVISIONS).set(row).execute()
    }

    override fun insertApplication(row: MembershipRoleApplicationsRecord) {
        sql.insertInto(MEMBERSHIP_ROLE_APPLICATIONS).set(row).execute()
    }

    override fun application(companyId: UUID, accountId: UUID, membershipVersion: Long): String? =
        sql.select(MEMBERSHIP_ROLE_APPLICATIONS.SNAPSHOT)
            .from(MEMBERSHIP_ROLE_APPLICATIONS)
            .where(
                MEMBERSHIP_ROLE_APPLICATIONS.COMPANY_ID.eq(companyId)
                    .and(MEMBERSHIP_ROLE_APPLICATIONS.ACCOUNT_ID.eq(accountId))
                    .and(MEMBERSHIP_ROLE_APPLICATIONS.MEMBERSHIP_VERSION.eq(membershipVersion))
            )
            .fetchOne()
            ?.value1()
            ?.data()
}
