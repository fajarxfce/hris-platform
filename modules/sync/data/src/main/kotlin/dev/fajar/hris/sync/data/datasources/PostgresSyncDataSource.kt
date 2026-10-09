package dev.fajar.hris.sync.data.datasources

import dev.fajar.hris.schema.tables.ExpenseClaims.EXPENSE_CLAIMS as E
import dev.fajar.hris.schema.tables.LeaveAccounts.LEAVE_ACCOUNTS as B
import dev.fajar.hris.schema.tables.LeaveRequests.LEAVE_REQUESTS as L
import dev.fajar.hris.schema.tables.MobileSyncChanges.MOBILE_SYNC_CHANGES as C
import dev.fajar.hris.schema.tables.MobileSyncHeads.MOBILE_SYNC_HEADS as H
import dev.fajar.hris.schema.tables.OvertimeRequests.OVERTIME_REQUESTS as O
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.sync.data.models.*
import dev.fajar.hris.sync.data.queries.syncAudience
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresSyncDataSource(private val sql: DSLContext) : SyncDataSource {
    override fun head(companyId: UUID): MobileSyncHeadsRecord =
        sql.selectFrom(H).where(H.COMPANY_ID.eq(companyId)).forShare().fetchSingle()

    override fun snapshot(
        selection: SyncSelection,
        afterCollection: String?,
        afterId: UUID?,
        limit: Int,
    ): List<SyncResourceRow> {
        val expenses =
            sql.select(
                    DSL.inline("EXPENSE_CLAIMS").`as`("collection"),
                    E.ID.`as`("resource_id"),
                    E.VERSION.`as`("resource_version"),
                )
                .from(E)
                .where(
                    E.COMPANY_ID.eq(selection.companyId),
                    E.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("EXPENSE_CLAIMS").`in`(selection.collections),
                )
        val leaves =
            sql.select(
                    DSL.inline("LEAVE_REQUESTS").`as`("collection"),
                    L.ID.`as`("resource_id"),
                    L.VERSION.`as`("resource_version"),
                )
                .from(L)
                .where(
                    L.COMPANY_ID.eq(selection.companyId),
                    L.EMPLOYMENT_ID.`in`(selection.employmentIds)
                        .or(L.OWNER_ACCOUNT_ID.eq(selection.accountId)),
                    DSL.inline("LEAVE_REQUESTS").`in`(selection.collections),
                )
        val overtime =
            sql.select(
                    DSL.inline("OVERTIME_REQUESTS").`as`("collection"),
                    O.ID.`as`("resource_id"),
                    O.VERSION.`as`("resource_version"),
                )
                .from(O)
                .where(
                    O.COMPANY_ID.eq(selection.companyId),
                    O.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    DSL.inline("OVERTIME_REQUESTS").`in`(selection.collections),
                )
        val balances =
            sql.select(
                    DSL.inline("LEAVE_BALANCES").`as`("collection"),
                    B.ID.`as`("resource_id"),
                    B.VERSION.`as`("resource_version"),
                )
                .from(B)
                .where(
                    B.COMPANY_ID.eq(selection.companyId),
                    B.EMPLOYMENT_ID.`in`(selection.employmentIds),
                    B.VERSION.gt(0),
                    DSL.inline("LEAVE_BALANCES").`in`(selection.collections),
                )
        val records =
            expenses
                .unionAll(leaves)
                .unionAll(overtime)
                .unionAll(balances)
                .asTable("sync_resources")
        val collection = requireNotNull(records.field("collection", String::class.java))
        val id = requireNotNull(records.field("resource_id", UUID::class.java))
        val version = requireNotNull(records.field("resource_version", Long::class.java))
        return sql.select(collection, id, version)
            .from(records)
            .where(
                if (afterCollection == null || afterId == null) DSL.noCondition()
                else DSL.row(collection, id).gt(DSL.row(afterCollection, afterId))
            )
            .orderBy(collection, id)
            .limit(limit)
            .fetch {
                SyncResourceRow(
                    requireNotNull(it[collection]),
                    requireNotNull(it[id]),
                    requireNotNull(it[version]),
                )
            }
    }

    override fun changes(
        selection: SyncSelection,
        after: Long,
        upper: Long,
        limit: Int,
    ): List<MobileSyncChangesRecord> =
        sql.selectFrom(C)
            .where(syncAudience(selection), C.SEQUENCE.gt(after), C.SEQUENCE.le(upper))
            .orderBy(C.SEQUENCE)
            .limit(limit)
            .fetch()

    override fun pending(selection: SyncSelection): Boolean =
        sql.fetchExists(sql.selectOne().from(C).where(syncAudience(selection), C.SEQUENCE.isNull))

    override fun publish(limit: Int): Int =
        requireNotNull(
            sql.fetchOne("select publish_mobile_sync_changes(?)", limit)?.get(0, Int::class.java)
        )

    override fun prune(limit: Int): Int =
        requireNotNull(
            sql.fetchOne("select prune_mobile_sync_changes(?)", limit)?.get(0, Int::class.java)
        )
}
