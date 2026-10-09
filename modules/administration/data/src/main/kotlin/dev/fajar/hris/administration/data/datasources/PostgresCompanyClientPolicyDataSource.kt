package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.schema.tables.CompanyClientPolicyHeads.COMPANY_CLIENT_POLICY_HEADS as H
import dev.fajar.hris.schema.tables.CompanyClientPolicyRevisions.COMPANY_CLIENT_POLICY_REVISIONS as R
import dev.fajar.hris.schema.tables.records.CompanyClientPolicyRevisionsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCompanyClientPolicyDataSource(private val sql: DSLContext) :
    CompanyClientPolicyDataSource {
    override fun lock(companyId: UUID, shared: Boolean) {
        sql.query(
                if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
                else "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "hris:client-policy:$companyId",
            )
            .execute()
    }

    override fun find(companyId: UUID, version: Long?): CompanyClientPolicyRevisionsRecord? =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(
                if (version == null)
                    R.VERSION.eq(sql.select(H.VERSION).from(H).where(H.COMPANY_ID.eq(companyId)))
                else R.VERSION.eq(version)
            )
            .fetchOne()

    override fun findEffective(
        companyId: UUID,
        at: OffsetDateTime,
    ): CompanyClientPolicyRevisionsRecord? =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId), R.ACTIVATE_AT.le(at))
            .orderBy(R.VERSION.desc())
            .limit(1)
            .fetchOne()

    override fun nextActivation(
        companyId: UUID,
        afterVersion: Long,
        after: OffsetDateTime,
    ): OffsetDateTime? =
        sql.select(DSL.min(R.ACTIVATE_AT))
            .from(R)
            .where(R.COMPANY_ID.eq(companyId), R.VERSION.gt(afterVersion), R.ACTIVATE_AT.gt(after))
            .fetchOne(0, OffsetDateTime::class.java)

    override fun createHead(companyId: UUID) {
        sql.insertInto(H).set(H.COMPANY_ID, companyId).execute()
    }

    override fun advanceHead(companyId: UUID, expectedVersion: Long): Boolean =
        sql.update(H)
            .set(H.VERSION, expectedVersion + 1)
            .where(H.COMPANY_ID.eq(companyId), H.VERSION.eq(expectedVersion))
            .execute() == 1

    override fun insertRevision(row: CompanyClientPolicyRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
