package dev.fajar.hris.organization.data.datasources

import dev.fajar.hris.schema.tables.OrganizationUnits.ORGANIZATION_UNITS as U
import dev.fajar.hris.schema.tables.records.OrganizationUnitsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresOrganizationDataSource(private val sql: DSLContext) : OrganizationDataSource {
    override fun lock(companyId: UUID, shared: Boolean) {
        sql.query(
                if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
                else "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "organization:$companyId",
            )
            .execute()
    }

    override fun find(companyId: UUID, id: UUID): OrganizationUnitsRecord? =
        sql.selectFrom(U).where(U.COMPANY_ID.eq(companyId)).and(U.ID.eq(id)).fetchOne()

    override fun ancestors(companyId: UUID, parentId: UUID): List<OrganizationUnitsRecord> =
        sql.fetch(
                """
        with recursive chain as (
            select u.*, 0 as depth from organization_units u where company_id=? and id=?
            union all select p.*, c.depth+1 from organization_units p join chain c on p.id=c.parent_id and p.company_id=c.company_id where c.depth<32
        ) select * from chain order by depth
    """,
                companyId,
                parentId,
            )
            .into(U)

    override fun list(
        companyId: UUID,
        kind: String?,
        after: String?,
        limit: Int,
    ): List<OrganizationUnitsRecord> =
        sql.selectFrom(U)
            .where(U.COMPANY_ID.eq(companyId))
            .and(kind?.let { U.KIND.eq(it) } ?: DSL.noCondition())
            .and(
                after?.let { DSL.concat(U.KIND, DSL.inline(":"), U.CODE).gt(it) }
                    ?: DSL.noCondition()
            )
            .orderBy(U.KIND, U.CODE)
            .limit(limit)
            .fetch()

    override fun insert(row: OrganizationUnitsRecord): OrganizationUnitsRecord =
        sql.insertInto(U).set(row).returning().fetchSingle()

    override fun update(
        row: OrganizationUnitsRecord,
        expectedVersion: Long,
    ): OrganizationUnitsRecord? =
        sql.update(U)
            .set(U.CODE, row.code)
            .set(U.NAME, row.name)
            .set(U.PARENT_ID, row.parentId)
            .set(U.TIMEZONE, row.timezone)
            .set(U.ACTIVE, row.active)
            .set(U.VERSION, expectedVersion + 1)
            .where(U.COMPANY_ID.eq(row.companyId))
            .and(U.ID.eq(row.id))
            .and(U.VERSION.eq(expectedVersion))
            .returning()
            .fetchOne()
}
