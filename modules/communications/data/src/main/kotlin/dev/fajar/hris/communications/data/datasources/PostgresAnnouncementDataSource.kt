package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.AnnouncementSummaryRow
import dev.fajar.hris.schema.tables.AnnouncementHeads.ANNOUNCEMENT_HEADS as H
import dev.fajar.hris.schema.tables.AnnouncementRevisions.ANNOUNCEMENT_REVISIONS as R
import dev.fajar.hris.schema.tables.records.AnnouncementRevisionsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAnnouncementDataSource(private val sql: DSLContext) : AnnouncementDataSource {
    override fun lock(companyId: UUID, shared: Boolean) {
        sql.query(
                if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
                else "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "hris:announcements:$companyId",
            )
            .execute()
    }

    override fun forJob(companyId: UUID, jobId: UUID): AnnouncementRevisionsRecord? =
        sql.select(R.fields().toList())
            .from(R)
            .join(H)
            .on(H.COMPANY_ID.eq(R.COMPANY_ID), H.ID.eq(R.ID), H.VERSION.eq(R.VERSION))
            .where(R.COMPANY_ID.eq(companyId), R.PUBLICATION_JOB_ID.eq(jobId))
            .fetchOneInto(R)

    override fun count(companyId: UUID): Int =
        requireNotNull(
            sql.selectCount()
                .from(H)
                .where(H.COMPANY_ID.eq(companyId))
                .fetchSingle(0, Int::class.java)
        )

    override fun find(companyId: UUID, id: UUID, revision: Long?): AnnouncementRevisionsRecord? =
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

    override fun list(companyId: UUID, after: UUID?, limit: Int): List<AnnouncementSummaryRow> =
        sql.select(
                R.ID,
                R.VERSION,
                R.TITLE,
                R.AUDIENCE_KIND,
                R.TARGET_COUNT,
                R.ACKNOWLEDGEMENT_REQUIRED,
                R.STATUS,
                R.RECORDED_AT,
                R.PUBLICATION_JOB_ID,
                R.SCHEDULED_FOR,
                R.PUBLISHED_AT,
                R.RECIPIENT_COUNT,
                R.PUBLICATION_ATTEMPTS,
            )
            .from(R)
            .join(H)
            .on(H.COMPANY_ID.eq(R.COMPANY_ID), H.ID.eq(R.ID), H.VERSION.eq(R.VERSION))
            .where(R.COMPANY_ID.eq(companyId), after?.let { R.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(R.ID)
            .limit(limit)
            .fetch {
                AnnouncementSummaryRow(
                    it[R.ID]!!,
                    it[R.VERSION]!!,
                    it[R.TITLE]!!,
                    it[R.AUDIENCE_KIND]!!,
                    it[R.TARGET_COUNT]!!,
                    it[R.ACKNOWLEDGEMENT_REQUIRED]!!,
                    it[R.STATUS]!!,
                    it[R.RECORDED_AT]!!,
                    it[R.PUBLICATION_JOB_ID],
                    it[R.SCHEDULED_FOR],
                    it[R.PUBLISHED_AT],
                    it[R.RECIPIENT_COUNT]!!,
                    it[R.PUBLICATION_ATTEMPTS]!!,
                )
            }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<AnnouncementSummaryRow> =
        sql.select(
                R.ID,
                R.VERSION,
                R.TITLE,
                R.AUDIENCE_KIND,
                R.TARGET_COUNT,
                R.ACKNOWLEDGEMENT_REQUIRED,
                R.STATUS,
                R.RECORDED_AT,
                R.PUBLICATION_JOB_ID,
                R.SCHEDULED_FOR,
                R.PUBLISHED_AT,
                R.RECIPIENT_COUNT,
                R.PUBLICATION_ATTEMPTS,
            )
            .from(R)
            .where(
                R.COMPANY_ID.eq(companyId),
                R.ID.eq(id),
                after?.let { R.VERSION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(R.VERSION)
            .limit(limit)
            .fetch {
                AnnouncementSummaryRow(
                    it[R.ID]!!,
                    it[R.VERSION]!!,
                    it[R.TITLE]!!,
                    it[R.AUDIENCE_KIND]!!,
                    it[R.TARGET_COUNT]!!,
                    it[R.ACKNOWLEDGEMENT_REQUIRED]!!,
                    it[R.STATUS]!!,
                    it[R.RECORDED_AT]!!,
                    it[R.PUBLICATION_JOB_ID],
                    it[R.SCHEDULED_FOR],
                    it[R.PUBLISHED_AT],
                    it[R.RECIPIENT_COUNT]!!,
                    it[R.PUBLICATION_ATTEMPTS]!!,
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

    override fun insertRevision(row: AnnouncementRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
