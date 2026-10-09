package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.AudienceGroupSummaryRow
import dev.fajar.hris.schema.tables.AudienceGroupHeads.AUDIENCE_GROUP_HEADS as H
import dev.fajar.hris.schema.tables.AudienceGroupRevisions.AUDIENCE_GROUP_REVISIONS as R
import dev.fajar.hris.schema.tables.records.AudienceGroupRevisionsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAudienceGroupDataSource(private val sql: DSLContext) : AudienceGroupDataSource {
    override fun lock(companyId: UUID, shared: Boolean) {
        sql.query(
                if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
                else "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "hris:audience-groups:$companyId",
            )
            .execute()
    }

    override fun count(companyId: UUID): Int =
        requireNotNull(
            sql.selectCount()
                .from(H)
                .where(H.COMPANY_ID.eq(companyId))
                .fetchSingle(0, Int::class.java)
        )

    override fun find(companyId: UUID, id: UUID, revision: Long?): AudienceGroupRevisionsRecord? =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId), R.ID.eq(id))
            .and(
                if (revision == null)
                    R.VERSION.eq(
                        sql.select(H.VERSION).from(H).where(H.COMPANY_ID.eq(companyId), H.ID.eq(id))
                    )
                else R.VERSION.eq(revision)
            )
            .fetchOne()

    override fun list(companyId: UUID, after: UUID?, limit: Int): List<AudienceGroupSummaryRow> =
        sql.select(R.ID, R.VERSION, R.NAME, R.ACTIVE, R.MEMBER_COUNT, R.RECORDED_AT)
            .from(R)
            .join(H)
            .on(H.COMPANY_ID.eq(R.COMPANY_ID), H.ID.eq(R.ID), H.VERSION.eq(R.VERSION))
            .where(R.COMPANY_ID.eq(companyId), after?.let { R.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(R.ID)
            .limit(limit)
            .fetch {
                AudienceGroupSummaryRow(
                    it[R.ID]!!,
                    it[R.VERSION]!!,
                    it[R.NAME]!!,
                    it[R.ACTIVE]!!,
                    it[R.MEMBER_COUNT]!!,
                    it[R.RECORDED_AT]!!,
                )
            }

    override fun references(companyId: UUID, ids: Set<UUID>): List<AudienceGroupSummaryRow> =
        sql.select(R.ID, R.VERSION, R.NAME, R.ACTIVE, R.MEMBER_COUNT, R.RECORDED_AT)
            .from(R)
            .join(H)
            .on(H.COMPANY_ID.eq(R.COMPANY_ID), H.ID.eq(R.ID), H.VERSION.eq(R.VERSION))
            .where(R.COMPANY_ID.eq(companyId), R.ID.`in`(ids))
            .orderBy(R.ID)
            .limit(32)
            .fetch {
                AudienceGroupSummaryRow(
                    it[R.ID]!!,
                    it[R.VERSION]!!,
                    it[R.NAME]!!,
                    it[R.ACTIVE]!!,
                    it[R.MEMBER_COUNT]!!,
                    it[R.RECORDED_AT]!!,
                )
            }

    override fun createHead(companyId: UUID, id: UUID) {
        sql.insertInto(H).set(H.COMPANY_ID, companyId).set(H.ID, id).execute()
    }

    override fun advanceHead(companyId: UUID, id: UUID, expectedVersion: Long): Boolean =
        sql.update(H)
            .set(H.VERSION, expectedVersion + 1)
            .where(H.COMPANY_ID.eq(companyId), H.ID.eq(id), H.VERSION.eq(expectedVersion))
            .execute() == 1

    override fun insertRevision(row: AudienceGroupRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
