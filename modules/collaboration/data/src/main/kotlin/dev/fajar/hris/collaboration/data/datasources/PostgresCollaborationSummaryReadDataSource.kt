package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationDirectConversations.COLLABORATION_DIRECT_CONVERSATIONS as C
import dev.fajar.hris.schema.tables.CollaborationDirectSummaryRecords.COLLABORATION_DIRECT_SUMMARY_RECORDS as R
import dev.fajar.hris.schema.tables.CollaborationSummaryAccountQueue.COLLABORATION_SUMMARY_ACCOUNT_QUEUE as A
import dev.fajar.hris.schema.tables.CollaborationSummaryRefreshQueue.COLLABORATION_SUMMARY_REFRESH_QUEUE as Q
import dev.fajar.hris.schema.tables.MobileSyncChanges.MOBILE_SYNC_CHANGES as J
import dev.fajar.hris.schema.tables.MobileSyncHeads.MOBILE_SYNC_HEADS as H
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSummaryReadDataSource(private val sql: DSLContext) :
    CollaborationSummaryReadDataSource {
    override fun head(company: UUID): MobileSyncHeadsRecord =
        sql.selectFrom(H).where(H.COMPANY_ID.eq(company)).forShare().fetchSingle()

    override fun snapshot(company: UUID, account: UUID, after: UUID?, limit: Int) =
        read(company, account, after?.let { R.CONVERSATION_ID.gt(it) } ?: DSL.noCondition(), limit)

    override fun facts(company: UUID, account: UUID, ids: List<UUID>) =
        read(company, account, R.CONVERSATION_ID.`in`(ids), ids.size)

    private fun read(
        company: UUID,
        account: UUID,
        selected: Condition,
        limit: Int,
    ): List<CollaborationSummaryFactRow> =
        sql.select(*R.fields(), *C.fields())
            .from(R)
            .join(C)
            .on(C.COMPANY_ID.eq(R.COMPANY_ID), C.ID.eq(R.CONVERSATION_ID))
            .where(R.COMPANY_ID.eq(company), R.OWNER_ACCOUNT_ID.eq(account), selected)
            .orderBy(R.CONVERSATION_ID)
            .limit(limit)
            .fetch { CollaborationSummaryFactRow(it.into(R), it.into(C)) }

    override fun changes(
        company: UUID,
        account: UUID,
        after: Long,
        upper: Long,
        limit: Int,
    ): List<MobileSyncChangesRecord> =
        sql.select(J.SEQUENCE, J.RESOURCE_ID, J.RESOURCE_VERSION)
            .from(J)
            .where(audience(company, account), J.SEQUENCE.gt(after), J.SEQUENCE.le(upper))
            .orderBy(J.SEQUENCE)
            .limit(limit)
            .fetchInto(J)

    override fun pending(company: UUID, account: UUID): Boolean =
        sql.fetchValue(
            DSL.select(
                DSL.exists(
                        DSL.selectOne().from(J).where(audience(company, account), J.SEQUENCE.isNull)
                    )
                    .or(
                        DSL.exists(
                            DSL.selectOne()
                                .from(Q)
                                .join(C)
                                .on(C.COMPANY_ID.eq(Q.COMPANY_ID), C.ID.eq(Q.CONVERSATION_ID))
                                .where(
                                    Q.COMPANY_ID.eq(company),
                                    Q.DUE_AT.isNotNull,
                                    participant(account),
                                )
                        )
                    )
                    .or(
                        DSL.exists(
                            DSL.selectOne()
                                .from(A)
                                .join(C)
                                .on(A.ACCOUNT_ID.`in`(C.FIRST_ACCOUNT_ID, C.SECOND_ACCOUNT_ID))
                                .where(
                                    C.COMPANY_ID.eq(company),
                                    A.DUE_AT.isNotNull,
                                    participant(account),
                                )
                        )
                    )
            )
        ) == true

    private fun audience(company: UUID, account: UUID) =
        J.COMPANY_ID.eq(company)
            .and(J.OWNER_ACCOUNT_ID.eq(account))
            .and(J.COLLECTION.eq("COLLABORATION_DIRECT"))

    private fun participant(account: UUID) =
        C.FIRST_ACCOUNT_ID.eq(account).or(C.SECOND_ACCOUNT_ID.eq(account))
}
