package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS
import dev.fajar.hris.schema.tables.CollaborationDirectConversations.COLLABORATION_DIRECT_CONVERSATIONS as C
import dev.fajar.hris.schema.tables.CollaborationDirectSummaryRecords.COLLABORATION_DIRECT_SUMMARY_RECORDS as R
import dev.fajar.hris.schema.tables.CollaborationSummaryAccountQueue.COLLABORATION_SUMMARY_ACCOUNT_QUEUE as A
import dev.fajar.hris.schema.tables.CollaborationSummaryAccountTargets.COLLABORATION_SUMMARY_ACCOUNT_TARGETS
import dev.fajar.hris.schema.tables.CollaborationSummaryRefreshQueue.COLLABORATION_SUMMARY_REFRESH_QUEUE as Q
import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationSummaryProjectionDataSource(private val sql: DSLContext) :
    CollaborationSummaryProjectionDataSource {
    override fun lockAccounts(
        now: Instant,
        limit: Int,
    ): List<CollaborationSummaryAccountQueueRecord> =
        // due_at may be PostgreSQL -infinity. Keep that scheduling sentinel in SQL;
        // the application needs only the locked identity and continuation.
        sql.select(A.ACCOUNT_ID, A.AFTER_COMPANY_ID, A.AFTER_CONVERSATION_ID)
            .from(A)
            .where(A.DUE_AT.le(now.atOffset(ZoneOffset.UTC)))
            .orderBy(A.DUE_AT, A.ACCOUNT_ID)
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .fetchInto(A)

    override fun targets(
        account: UUID,
        afterCompany: UUID?,
        afterConversation: UUID?,
        limit: Int,
    ): List<CollaborationSummaryAccountTargetsRecord> =
        COLLABORATION_SUMMARY_ACCOUNT_TARGETS.call(account, afterCompany, afterConversation, limit)
            .let { table ->
                sql.selectFrom(table).orderBy(table.COMPANY_ID, table.CONVERSATION_ID).fetch()
            }

    override fun enqueue(targets: List<CollaborationSummaryRefreshQueueRecord>): Boolean =
        sql.batch(
                targets.map { row ->
                    sql.insertInto(Q)
                        .set(row)
                        .onConflict(Q.COMPANY_ID, Q.CONVERSATION_ID)
                        .doUpdate()
                        .set(Q.DUE_AT, DSL.least(Q.DUE_AT, DSL.`val`(row.dueAt)))
                }
            )
            .execute()
            .all { it == 1 }

    override fun advanceAccount(
        account: UUID,
        afterCompany: UUID?,
        afterConversation: UUID?,
        next: Instant?,
    ): Boolean =
        sql.update(A)
            .set(A.AFTER_COMPANY_ID, afterCompany)
            .set(A.AFTER_CONVERSATION_ID, afterConversation)
            .set(A.DUE_AT, next?.atOffset(ZoneOffset.UTC))
            .where(A.ACCOUNT_ID.eq(account))
            .execute() == 1

    override fun dueCompanies(now: Instant, limit: Int): List<UUID> =
        sql.select(Q.COMPANY_ID)
            .from(Q)
            .where(Q.DUE_AT.le(now.atOffset(ZoneOffset.UTC)))
            .groupBy(Q.COMPANY_ID)
            .orderBy(DSL.min(Q.DUE_AT), Q.COMPANY_ID)
            .limit(limit)
            .fetch(Q.COMPANY_ID)
            .map { requireNotNull(it) }

    override fun lockDue(company: UUID, now: Instant, limit: Int): List<UUID> =
        sql.select(Q.CONVERSATION_ID)
            .from(Q)
            .where(Q.COMPANY_ID.eq(company), Q.DUE_AT.le(now.atOffset(ZoneOffset.UTC)))
            .orderBy(Q.CONVERSATION_ID)
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .fetch(Q.CONVERSATION_ID)
            .map { requireNotNull(it) }

    override fun candidates(
        company: UUID,
        ids: List<UUID>,
    ): List<CollaborationSummaryCandidateRow> {
        val first = ACCOUNTS.`as`("summary_first")
        val second = ACCOUNTS.`as`("summary_second")
        return sql.select(
                C.COMPANY_ID,
                C.ID,
                C.FIRST_ACCOUNT_ID,
                C.SECOND_ACCOUNT_ID,
                first.DISPLAY_NAME,
                second.DISPLAY_NAME,
            )
            .from(C)
            .join(first)
            .on(first.ID.eq(C.FIRST_ACCOUNT_ID))
            .join(second)
            .on(second.ID.eq(C.SECOND_ACCOUNT_ID))
            .where(C.COMPANY_ID.eq(company), C.ID.`in`(ids))
            .orderBy(C.ID)
            .fetch { row ->
                CollaborationSummaryCandidateRow(
                    requireNotNull(row[C.COMPANY_ID]),
                    requireNotNull(row[C.ID]),
                    requireNotNull(row[C.FIRST_ACCOUNT_ID]),
                    requireNotNull(row[C.SECOND_ACCOUNT_ID]),
                    requireNotNull(row[first.DISPLAY_NAME]),
                    requireNotNull(row[second.DISPLAY_NAME]),
                )
            }
    }

    override fun records(
        company: UUID,
        ids: List<UUID>,
    ): List<CollaborationDirectSummaryRecordsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company), R.CONVERSATION_ID.`in`(ids))
            .orderBy(R.CONVERSATION_ID, R.OWNER_ACCOUNT_ID)
            .fetch()

    override fun save(records: List<CollaborationDirectSummaryRecordsRecord>): Boolean =
        sql.batch(
                records.map { row ->
                    if (row.version == 0L) sql.insertInto(R).set(row)
                    else
                        sql.update(R)
                            .set(R.PEER_DISPLAY_NAME, row.peerDisplayName)
                            .set(R.VERSION, row.version)
                            .where(
                                R.COMPANY_ID.eq(row.companyId),
                                R.CONVERSATION_ID.eq(row.conversationId),
                                R.OWNER_ACCOUNT_ID.eq(row.ownerAccountId),
                                R.PEER_ACCOUNT_ID.eq(row.peerAccountId),
                                R.VERSION.eq(requireNotNull(row.version) - 1),
                            )
                }
            )
            .execute()
            .all { it == 1 }

    override fun complete(company: UUID, ids: List<UUID>): Boolean =
        sql.update(Q)
            .set(Q.DUE_AT, null as OffsetDateTime?)
            .where(Q.COMPANY_ID.eq(company), Q.CONVERSATION_ID.`in`(ids))
            .execute() == ids.size
}
