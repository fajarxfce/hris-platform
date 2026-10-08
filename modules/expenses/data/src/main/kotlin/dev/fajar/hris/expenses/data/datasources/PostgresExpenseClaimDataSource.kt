package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.*
import dev.fajar.hris.schema.tables.ExpenseClaimChanges.EXPENSE_CLAIM_CHANGES as H
import dev.fajar.hris.schema.tables.ExpenseClaims.EXPENSE_CLAIMS as C
import dev.fajar.hris.schema.tables.ExpenseDraftLines.EXPENSE_DRAFT_LINES as L
import dev.fajar.hris.schema.tables.ExpenseDraftReceipts.EXPENSE_DRAFT_RECEIPTS as R
import dev.fajar.hris.schema.tables.ExpenseDrafts.EXPENSE_DRAFTS as D
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresExpenseClaimDataSource(private val sql: DSLContext) : ExpenseClaimDataSource {
    override fun lock(company: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "expenses:$company")
            .execute()
    }

    override fun capacity(company: UUID, account: UUID): ExpenseCapacityRow =
        sql.select(DSL.count(), DSL.count().filterWhere(C.CREATED_BY.eq(account)))
            .from(C)
            .where(C.COMPANY_ID.eq(company))
            .and(C.STATUS.`in`("DRAFT", "PENDING", "RETURNED"))
            .fetchSingle { ExpenseCapacityRow(it.value1(), it.value2()) }

    override fun find(company: UUID, id: UUID): ExpenseClaimsRecord? =
        sql.selectFrom(C).where(C.COMPANY_ID.eq(company)).and(C.ID.eq(id)).fetchOne()

    override fun draft(company: UUID, id: UUID, revision: Int): ExpenseDraftsRecord? =
        sql.selectFrom(D)
            .where(D.COMPANY_ID.eq(company))
            .and(D.CLAIM_ID.eq(id))
            .and(D.REVISION.eq(revision))
            .fetchOne()

    override fun list(
        company: UUID,
        employment: UUID?,
        status: String?,
        from: OffsetDateTime,
        until: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<ExpenseClaimSummaryRow> {
        val cursor = C.`as`("cursor")
        val before =
            if (after == null) DSL.noCondition()
            else
                DSL.row(C.CREATED_AT, C.ID)
                    .lt(
                        DSL.select(cursor.CREATED_AT, cursor.ID)
                            .from(cursor)
                            .where(cursor.COMPANY_ID.eq(company))
                            .and(cursor.ID.eq(after))
                            .and(
                                employment?.let { cursor.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition()
                            )
                            .and(status?.let { cursor.STATUS.eq(it) } ?: DSL.noCondition())
                            .and(cursor.CREATED_AT.ge(from))
                            .and(cursor.CREATED_AT.lt(until))
                    )
        return sql.select(
                C.ID,
                C.EMPLOYMENT_ID,
                D.EMPLOYEE_NUMBER,
                D.EMPLOYEE_NAME,
                D.TITLE,
                D.TOTAL_AMOUNT,
                C.CREATED_AT,
                C.STATUS,
                C.VERSION,
            )
            .from(C)
            .join(D)
            .on(
                D.COMPANY_ID.eq(C.COMPANY_ID)
                    .and(D.CLAIM_ID.eq(C.ID))
                    .and(D.REVISION.eq(C.DRAFT_REVISION))
            )
            .where(C.COMPANY_ID.eq(company))
            .and(employment?.let { C.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition())
            .and(status?.let { C.STATUS.eq(it) } ?: DSL.noCondition())
            .and(C.CREATED_AT.ge(from))
            .and(C.CREATED_AT.lt(until))
            .and(before)
            .orderBy(C.CREATED_AT.desc(), C.ID.desc())
            .limit(limit)
            .fetch {
                ExpenseClaimSummaryRow(
                    requireNotNull(it[C.ID]),
                    requireNotNull(it[C.EMPLOYMENT_ID]),
                    requireNotNull(it[D.EMPLOYEE_NUMBER]),
                    requireNotNull(it[D.EMPLOYEE_NAME]),
                    requireNotNull(it[D.TITLE]),
                    requireNotNull(it[D.TOTAL_AMOUNT]),
                    requireNotNull(it[C.CREATED_AT]),
                    requireNotNull(it[C.STATUS]),
                    requireNotNull(it[C.VERSION]),
                )
            }
    }

    override fun drafts(
        company: UUID,
        id: UUID,
        after: Int?,
        limit: Int,
    ): List<ExpenseDraftsRecord> =
        sql.selectFrom(D)
            .where(D.COMPANY_ID.eq(company))
            .and(D.CLAIM_ID.eq(id))
            .and(after?.let { D.REVISION.lt(it) } ?: DSL.noCondition())
            .orderBy(D.REVISION.desc())
            .limit(limit)
            .fetch()

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<ExpenseClaimChangesRecord> =
        sql.selectFrom(H)
            .where(H.COMPANY_ID.eq(company))
            .and(H.CLAIM_ID.eq(id))
            .and(after?.let { H.VERSION.lt(it) } ?: DSL.noCondition())
            .orderBy(H.VERSION.desc())
            .limit(limit)
            .fetch()

    override fun lines(
        company: UUID,
        id: UUID,
        revisions: Set<Int>,
    ): List<ExpenseDraftLinesRecord> =
        sql.selectFrom(L)
            .where(L.COMPANY_ID.eq(company))
            .and(L.CLAIM_ID.eq(id))
            .and(L.DRAFT_REVISION.`in`(revisions))
            .orderBy(L.DRAFT_REVISION, L.ORDINAL)
            .fetch()

    override fun receipts(
        company: UUID,
        id: UUID,
        revisions: Set<Int>,
    ): List<ExpenseDraftReceiptsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company))
            .and(R.CLAIM_ID.eq(id))
            .and(R.DRAFT_REVISION.`in`(revisions))
            .orderBy(R.DRAFT_REVISION, R.LINE_ID, R.ORDINAL)
            .fetch()

    override fun insert(row: ExpenseClaimsRecord) {
        sql.insertInto(C).set(row).execute()
    }

    override fun advance(company: UUID, id: UUID, version: Long, revision: Int): Long? =
        sql.update(C)
            .set(C.VERSION, version + 1)
            .set(C.DRAFT_REVISION, revision)
            .set(C.STATUS, "DRAFT")
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(C.VERSION.eq(version))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun cancel(company: UUID, id: UUID, version: Long): Long? =
        sql.update(C)
            .set(C.VERSION, version + 1)
            .set(C.STATUS, "CANCELLED")
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(C.VERSION.eq(version))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun submit(
        company: UUID,
        id: UUID,
        version: Long,
        submission: UUID,
        number: Int,
    ): Long? =
        sql.update(C)
            .set(C.VERSION, version + 1)
            .set(C.STATUS, "PENDING")
            .set(C.LATEST_SUBMISSION_ID, submission)
            .set(C.SUBMISSION_COUNT, number)
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(C.VERSION.eq(version))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun review(company: UUID, id: UUID, version: Long, status: String): Long? =
        sql.update(C)
            .set(C.VERSION, version + 1)
            .set(C.STATUS, status)
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(C.VERSION.eq(version))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun withdraw(company: UUID, id: UUID, version: Long): Long? =
        sql.update(C)
            .set(C.VERSION, version + 1)
            .set(C.STATUS, "DRAFT")
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.eq(id))
            .and(C.VERSION.eq(version))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: ExpenseDraftsRecord) {
        sql.insertInto(D).set(row).execute()
    }

    override fun insertLines(rows: List<ExpenseDraftLinesRecord>) {
        if (rows.isNotEmpty()) sql.batchInsert(rows).execute()
    }

    override fun insertReceipts(rows: List<ExpenseDraftReceiptsRecord>) {
        if (rows.isNotEmpty()) sql.batchInsert(rows).execute()
    }

    override fun appendChange(row: ExpenseClaimChangesRecord) {
        sql.insertInto(H).set(row).execute()
    }
}
