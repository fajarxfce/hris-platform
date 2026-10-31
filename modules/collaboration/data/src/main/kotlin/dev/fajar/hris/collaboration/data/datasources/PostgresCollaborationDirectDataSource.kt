package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationDirectConversations.COLLABORATION_DIRECT_CONVERSATIONS as D
import dev.fajar.hris.schema.tables.records.CollaborationDirectConversationsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationDirectDataSource(private val sql: DSLContext) :
    CollaborationDirectDataSource {
    override fun lockPair(companyId: UUID, first: UUID, second: UUID) {
        sql.fetch(
            "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "collaboration.direct:$companyId:$first:$second",
        )
    }

    override fun findPair(
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): CollaborationDirectConversationsRecord? =
        sql.selectFrom(D)
            .where(
                D.COMPANY_ID.eq(companyId),
                D.FIRST_ACCOUNT_ID.eq(first),
                D.SECOND_ACCOUNT_ID.eq(second),
            )
            .fetchOne()

    override fun find(companyId: UUID, id: UUID): CollaborationDirectConversationsRecord? =
        sql.selectFrom(D).where(D.COMPANY_ID.eq(companyId), D.ID.eq(id)).fetchOne()

    override fun list(
        companyId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<CollaborationDirectConversationsRecord> =
        sql.selectFrom(D)
            .where(
                D.COMPANY_ID.eq(companyId),
                D.FIRST_ACCOUNT_ID.eq(accountId).or(D.SECOND_ACCOUNT_ID.eq(accountId)),
                after?.let { D.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(D.ID)
            .limit(limit)
            .fetch()

    override fun insert(record: CollaborationDirectConversationsRecord) {
        sql.insertInto(D).set(record).execute()
    }
}
