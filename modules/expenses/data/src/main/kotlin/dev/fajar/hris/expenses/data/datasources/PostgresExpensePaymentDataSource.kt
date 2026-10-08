package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.*
import dev.fajar.hris.schema.tables.ExpensePayableCandidates.EXPENSE_PAYABLE_CANDIDATES as C
import dev.fajar.hris.schema.tables.ExpensePaymentActions.EXPENSE_PAYMENT_ACTIONS as A
import dev.fajar.hris.schema.tables.ExpensePaymentBatches.EXPENSE_PAYMENT_BATCHES as B
import dev.fajar.hris.schema.tables.ExpensePaymentItems.EXPENSE_PAYMENT_ITEMS as I
import dev.fajar.hris.schema.tables.ExpensePaymentResults.EXPENSE_PAYMENT_RESULTS as R
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresExpensePaymentDataSource(private val sql: DSLContext) : ExpensePaymentDataSource {
    override fun progress(company: UUID, claim: UUID): List<ExpensePaymentProgressRow> =
        sql.select(B.ID, I.ID, I.STATUS, B.CREATED_AT, B.RELEASED_AT, I.OCCURRED_AT)
            .from(I)
            .join(B)
            .on(B.COMPANY_ID.eq(I.COMPANY_ID).and(B.ID.eq(I.BATCH_ID)))
            .where(I.COMPANY_ID.eq(company))
            .and(I.CLAIM_ID.eq(claim))
            .orderBy(B.CREATED_AT.desc(), I.ID.desc())
            .limit(20)
            .fetch {
                ExpensePaymentProgressRow(
                    requireNotNull(it[B.ID]),
                    requireNotNull(it[I.ID]),
                    requireNotNull(it[I.STATUS]),
                    requireNotNull(it[B.CREATED_AT]),
                    it[B.RELEASED_AT],
                    it[I.OCCURRED_AT],
                )
            }

    override fun lock(company: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "expenses:$company")
            .execute()
    }

    override fun capacity(company: UUID): ExpensePaymentCapacityRow =
        sql.select(DSL.count(), DSL.count().filterWhere(B.STATUS.`in`("PREPARED", "RELEASED")))
            .from(B)
            .where(B.COMPANY_ID.eq(company))
            .fetchSingle { ExpensePaymentCapacityRow(it.value1(), it.value2()) }

    override fun candidates(
        company: UUID,
        submissions: Set<UUID>,
    ): List<ExpensePayableCandidatesRecord> =
        sql.selectFrom(C)
            .where(C.COMPANY_ID.eq(company))
            .and(C.SUBMISSION_ID.`in`(submissions))
            .orderBy(C.CLAIM_ID)
            .limit(101)
            .fetch()

    override fun occupiedClaims(company: UUID, claims: Set<UUID>): Set<UUID> =
        sql.select(I.CLAIM_ID)
            .from(I)
            .where(I.COMPANY_ID.eq(company))
            .and(I.CLAIM_ID.`in`(claims))
            .and(I.STATUS.`in`("PREPARED", "PENDING", "SUCCEEDED"))
            .fetch(I.CLAIM_ID)
            .filterNotNull()
            .toSet()

    override fun attempts(company: UUID, claims: Set<UUID>): Map<UUID, Int> =
        sql.select(I.CLAIM_ID, DSL.count())
            .from(I)
            .where(I.COMPANY_ID.eq(company))
            .and(I.CLAIM_ID.`in`(claims))
            .groupBy(I.CLAIM_ID)
            .fetch()
            .associate { requireNotNull(it.value1()) to it.value2() }

    override fun payables(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<ExpensePayableCandidatesRecord> {
        val cursor = C.`as`("cursor")
        val available =
            DSL.notExists(
                DSL.selectOne()
                    .from(I)
                    .where(I.COMPANY_ID.eq(C.COMPANY_ID))
                    .and(I.CLAIM_ID.eq(C.CLAIM_ID))
                    .and(I.STATUS.`in`("PREPARED", "PENDING", "SUCCEEDED"))
            )
        val attempts =
            DSL.selectCount()
                .from(I)
                .where(I.COMPANY_ID.eq(C.COMPANY_ID))
                .and(I.CLAIM_ID.eq(C.CLAIM_ID))
                .asField<Int>()
        val before =
            if (after == null) DSL.noCondition()
            else
                DSL.row(C.APPROVED_AT, C.CLAIM_ID)
                    .lt(
                        DSL.select(cursor.APPROVED_AT, cursor.CLAIM_ID)
                            .from(cursor)
                            .where(cursor.COMPANY_ID.eq(company))
                            .and(cursor.CLAIM_ID.eq(after))
                            .and(cursor.APPROVED_AT.ge(from))
                            .and(cursor.APPROVED_AT.lt(until))
                    )
        return sql.selectFrom(C)
            .where(C.COMPANY_ID.eq(company))
            .and(C.APPROVED_AT.ge(from))
            .and(C.APPROVED_AT.lt(until))
            .and(available)
            .and(attempts.lt(20))
            .and(before)
            .orderBy(C.APPROVED_AT.desc(), C.CLAIM_ID.desc())
            .limit(limit)
            .fetch()
    }

    override fun find(company: UUID, id: UUID): ExpensePaymentBatchesRecord? =
        sql.selectFrom(B).where(B.COMPANY_ID.eq(company)).and(B.ID.eq(id)).fetchOne()

    override fun items(company: UUID, id: UUID): List<ExpensePaymentItemsRecord> =
        sql.selectFrom(I)
            .where(I.COMPANY_ID.eq(company))
            .and(I.BATCH_ID.eq(id))
            .orderBy(I.ID)
            .limit(100)
            .fetch()

    override fun list(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<ExpensePaymentSummaryRow> {
        val cursor = B.`as`("cursor")
        val before =
            if (after == null) DSL.noCondition()
            else
                DSL.row(B.CREATED_AT, B.ID)
                    .lt(
                        DSL.select(cursor.CREATED_AT, cursor.ID)
                            .from(cursor)
                            .where(cursor.COMPANY_ID.eq(company))
                            .and(cursor.ID.eq(after))
                            .and(cursor.CREATED_AT.ge(from))
                            .and(cursor.CREATED_AT.lt(until))
                            .and(status?.let { cursor.STATUS.eq(it) } ?: DSL.noCondition())
                    )
        val pending = DSL.count().filterWhere(I.STATUS.eq("PENDING"))
        val succeeded = DSL.count().filterWhere(I.STATUS.eq("SUCCEEDED"))
        val failed = DSL.count().filterWhere(I.STATUS.eq("FAILED"))
        return sql.select(B.asterisk(), pending, succeeded, failed)
            .from(B)
            .join(I)
            .on(I.COMPANY_ID.eq(B.COMPANY_ID).and(I.BATCH_ID.eq(B.ID)))
            .where(B.COMPANY_ID.eq(company))
            .and(B.CREATED_AT.ge(from))
            .and(B.CREATED_AT.lt(until))
            .and(status?.let { B.STATUS.eq(it) } ?: DSL.noCondition())
            .and(before)
            .groupBy(*B.fields())
            .orderBy(B.CREATED_AT.desc(), B.ID.desc())
            .limit(limit)
            .fetch {
                ExpensePaymentSummaryRow(
                    it.into(B),
                    requireNotNull(it[pending]),
                    requireNotNull(it[succeeded]),
                    requireNotNull(it[failed]),
                )
            }
    }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<ExpensePaymentActionsRecord> =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(company))
            .and(A.BATCH_ID.eq(id))
            .and(after?.let { A.VERSION.gt(it) } ?: DSL.noCondition())
            .orderBy(A.VERSION)
            .limit(limit)
            .fetch()

    override fun results(company: UUID, id: UUID): List<ExpensePaymentResultsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company))
            .and(R.BATCH_ID.eq(id))
            .orderBy(R.BATCH_VERSION, R.ITEM_ID)
            .limit(100)
            .fetch()

    override fun insert(row: ExpensePaymentBatchesRecord) {
        sql.insertInto(B).set(row).execute()
    }

    override fun insertItems(rows: List<ExpensePaymentItemsRecord>) {
        sql.batchInsert(rows).execute()
    }

    override fun action(row: ExpensePaymentActionsRecord) {
        sql.insertInto(A).set(row).execute()
    }

    override fun advance(
        company: UUID,
        id: UUID,
        version: Long,
        status: String,
        releasedBy: UUID?,
        releasedAt: OffsetDateTime?,
    ): Long? =
        sql.update(B)
            .set(B.VERSION, version + 1)
            .set(B.STATUS, status)
            .set(B.RELEASED_BY, releasedBy)
            .set(B.RELEASED_AT, releasedAt)
            .where(B.COMPANY_ID.eq(company))
            .and(B.ID.eq(id))
            .and(B.VERSION.eq(version))
            .returning(B.VERSION)
            .fetchOne()
            ?.version

    override fun transitionItems(company: UUID, id: UUID, batchVersion: Long, status: String): Int =
        sql.update(I)
            .set(I.STATUS, status)
            .set(I.VERSION, 1)
            .set(I.BATCH_VERSION, batchVersion)
            .where(I.COMPANY_ID.eq(company))
            .and(I.BATCH_ID.eq(id))
            .and(I.STATUS.eq("PREPARED"))
            .execute()

    override fun reconcileItem(
        company: UUID,
        batch: UUID,
        id: UUID,
        batchVersion: Long,
        status: String,
        reference: String?,
        occurredAt: OffsetDateTime,
    ): Int =
        sql.update(I)
            .set(I.STATUS, status)
            .set(I.VERSION, 2)
            .set(I.BATCH_VERSION, batchVersion)
            .set(I.TRANSACTION_REFERENCE, reference)
            .set(I.OCCURRED_AT, occurredAt)
            .where(I.COMPANY_ID.eq(company))
            .and(I.BATCH_ID.eq(batch))
            .and(I.ID.eq(id))
            .and(I.STATUS.eq("PENDING"))
            .and(I.VERSION.eq(1))
            .execute()

    override fun recordResults(rows: List<ExpensePaymentResultsRecord>) {
        sql.batchInsert(rows).execute()
    }
}
