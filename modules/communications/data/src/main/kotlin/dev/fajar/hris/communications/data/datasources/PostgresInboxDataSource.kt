package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.*
import dev.fajar.hris.schema.Tables.ANNOUNCEMENT_PUBLICATIONS as P
import dev.fajar.hris.schema.Tables.ANNOUNCEMENT_REVISIONS as R
import dev.fajar.hris.schema.Tables.INBOX_ITEMS as I
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresInboxDataSource(private val sql: DSLContext) : InboxDataSource {
    override fun find(companyId: UUID, accountId: UUID, id: UUID, lock: Boolean): InboxItemRow? {
        val query =
            sql.select(
                    I.ID,
                    I.ANNOUNCEMENT_ID,
                    I.PUBLICATION_ID,
                    I.VERSION,
                    R.TITLE,
                    R.BODY,
                    I.ACKNOWLEDGEMENT_REQUIRED,
                    I.DELIVERED_AT,
                    I.READ_AT,
                    I.ACKNOWLEDGED_AT,
                )
                .from(I)
                .join(P)
                .on(P.COMPANY_ID.eq(I.COMPANY_ID), P.ID.eq(I.PUBLICATION_ID))
                .join(R)
                .on(
                    R.COMPANY_ID.eq(P.COMPANY_ID),
                    R.ID.eq(P.ANNOUNCEMENT_ID),
                    R.VERSION.eq(P.CONTENT_VERSION),
                )
                .where(
                    I.COMPANY_ID.eq(companyId),
                    I.OWNER_ACCOUNT_ID.eq(accountId),
                    I.ID.eq(id),
                    I.WITHDRAWN.isFalse,
                )
        val row = if (lock) query.forUpdate().of(I).fetchOne() else query.fetchOne()
        return row?.let {
            InboxItemRow(
                it[I.ID]!!,
                it[I.ANNOUNCEMENT_ID]!!,
                it[I.PUBLICATION_ID]!!,
                it[I.VERSION]!!,
                it[R.TITLE]!!,
                it[R.BODY]!!,
                it[I.ACKNOWLEDGEMENT_REQUIRED]!!,
                it[I.DELIVERED_AT]!!,
                it[I.READ_AT],
                it[I.ACKNOWLEDGED_AT],
            )
        }
    }

    override fun list(
        companyId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<InboxSummaryRow> =
        sql.select(
                I.ID,
                I.ANNOUNCEMENT_ID,
                I.VERSION,
                R.TITLE,
                I.ACKNOWLEDGEMENT_REQUIRED,
                I.DELIVERED_AT,
                I.READ_AT,
                I.ACKNOWLEDGED_AT,
            )
            .from(I)
            .join(P)
            .on(P.COMPANY_ID.eq(I.COMPANY_ID), P.ID.eq(I.PUBLICATION_ID))
            .join(R)
            .on(
                R.COMPANY_ID.eq(P.COMPANY_ID),
                R.ID.eq(P.ANNOUNCEMENT_ID),
                R.VERSION.eq(P.CONTENT_VERSION),
            )
            .where(
                I.COMPANY_ID.eq(companyId),
                I.OWNER_ACCOUNT_ID.eq(accountId),
                I.WITHDRAWN.isFalse,
                after?.let { I.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(I.ID)
            .limit(limit)
            .fetch {
                InboxSummaryRow(
                    it[I.ID]!!,
                    it[I.ANNOUNCEMENT_ID]!!,
                    it[I.VERSION]!!,
                    it[R.TITLE]!!,
                    it[I.ACKNOWLEDGEMENT_REQUIRED]!!,
                    it[I.DELIVERED_AT]!!,
                    it[I.READ_AT],
                    it[I.ACKNOWLEDGED_AT],
                )
            }

    override fun updateReadState(
        companyId: UUID,
        accountId: UUID,
        id: UUID,
        expectedVersion: Long,
        readAt: Instant?,
        acknowledgedAt: Instant?,
    ): Boolean =
        sql.update(I)
            .set(I.READ_AT, readAt?.atOffset(ZoneOffset.UTC))
            .set(I.ACKNOWLEDGED_AT, acknowledgedAt?.atOffset(ZoneOffset.UTC))
            .set(I.VERSION, expectedVersion + 1)
            .where(
                I.COMPANY_ID.eq(companyId),
                I.OWNER_ACCOUNT_ID.eq(accountId),
                I.ID.eq(id),
                I.VERSION.eq(expectedVersion),
                I.WITHDRAWN.isFalse,
            )
            .execute() == 1

    override fun withdraw(companyId: UUID, announcementId: UUID): Int =
        sql.update(I)
            .set(I.WITHDRAWN, true)
            .set(I.VERSION, I.VERSION.plus(1))
            .where(
                I.COMPANY_ID.eq(companyId),
                I.ANNOUNCEMENT_ID.eq(announcementId),
                I.WITHDRAWN.isFalse,
            )
            .execute()
}
