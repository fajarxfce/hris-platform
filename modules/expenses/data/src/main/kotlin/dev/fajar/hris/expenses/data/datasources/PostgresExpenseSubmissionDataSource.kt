package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.*
import dev.fajar.hris.schema.tables.ExpenseCategories.EXPENSE_CATEGORIES as P
import dev.fajar.hris.schema.tables.ExpenseCategoryRevisions.EXPENSE_CATEGORY_REVISIONS as PR
import dev.fajar.hris.schema.tables.ExpenseClaims.EXPENSE_CLAIMS as C
import dev.fajar.hris.schema.tables.ExpenseDraftLines.EXPENSE_DRAFT_LINES as DL
import dev.fajar.hris.schema.tables.ExpenseDrafts.EXPENSE_DRAFTS as D
import dev.fajar.hris.schema.tables.ExpenseSubmissions.EXPENSE_SUBMISSIONS as S
import dev.fajar.hris.schema.tables.ExpenseSubmittedLines.EXPENSE_SUBMITTED_LINES as L
import dev.fajar.hris.schema.tables.ExpenseSubmittedReceipts.EXPENSE_SUBMITTED_RECEIPTS as R
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresExpenseSubmissionDataSource(private val sql: DSLContext) :
    ExpenseSubmissionDataSource {
    override fun contributors(company: UUID, claim: UUID): Set<UUID> =
        sql.selectDistinct(D.ACTOR_ID)
            .from(D)
            .where(D.COMPANY_ID.eq(company))
            .and(D.CLAIM_ID.eq(claim))
            .orderBy(D.ACTOR_ID)
            .limit(101)
            .fetch(D.ACTOR_ID)
            .toSet()

    override fun find(company: UUID, id: UUID): ExpenseSubmissionsRecord? =
        sql.selectFrom(S).where(S.COMPANY_ID.eq(company)).and(S.ID.eq(id)).fetchOne()

    override fun list(
        company: UUID,
        claim: UUID,
        after: Int?,
        limit: Int,
    ): List<ExpenseSubmissionSummaryRow> =
        sql.select(
                S.ID,
                S.NUMBER,
                S.DRAFT_REVISION,
                S.APPROVAL_ID,
                D.TITLE,
                D.TOTAL_AMOUNT,
                S.SUBMITTED_BY,
                S.SUBMITTED_AT,
            )
            .from(S)
            .join(D)
            .on(
                D.COMPANY_ID.eq(S.COMPANY_ID)
                    .and(D.CLAIM_ID.eq(S.CLAIM_ID))
                    .and(D.REVISION.eq(S.DRAFT_REVISION))
            )
            .where(S.COMPANY_ID.eq(company))
            .and(S.CLAIM_ID.eq(claim))
            .and(after?.let { S.NUMBER.lt(it) } ?: DSL.noCondition())
            .orderBy(S.NUMBER.desc())
            .limit(limit)
            .fetch {
                ExpenseSubmissionSummaryRow(
                    it.value1(),
                    it.value2(),
                    it.value3(),
                    it.value4(),
                    it.value5(),
                    it.value6(),
                    it.value7(),
                    it.value8(),
                )
            }

    override fun lines(company: UUID, id: UUID): List<ExpenseSubmittedLineRow> =
        sql.select(L.asterisk(), DL.asterisk(), P.CODE, PR.asterisk())
            .from(L)
            .join(DL)
            .on(
                DL.COMPANY_ID.eq(L.COMPANY_ID)
                    .and(DL.CLAIM_ID.eq(L.CLAIM_ID))
                    .and(DL.DRAFT_REVISION.eq(L.DRAFT_REVISION))
                    .and(DL.ID.eq(L.LINE_ID))
            )
            .join(P)
            .on(P.COMPANY_ID.eq(L.COMPANY_ID).and(P.ID.eq(L.CATEGORY_ID)))
            .join(PR)
            .on(
                PR.COMPANY_ID.eq(L.COMPANY_ID)
                    .and(PR.CATEGORY_ID.eq(L.CATEGORY_ID))
                    .and(PR.REVISION.eq(L.CATEGORY_REVISION))
            )
            .where(L.COMPANY_ID.eq(company))
            .and(L.SUBMISSION_ID.eq(id))
            .orderBy(DL.ORDINAL)
            .limit(20)
            .fetch {
                ExpenseSubmittedLineRow(
                    it.into(DL),
                    it.into(L),
                    requireNotNull(it[P.CODE]),
                    it.into(PR),
                )
            }

    override fun receipts(company: UUID, id: UUID): List<ExpenseSubmittedReceiptsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company))
            .and(R.SUBMISSION_ID.eq(id))
            .orderBy(R.LINE_ID, R.ORDINAL)
            .limit(60)
            .fetch()

    override fun duplicateDigests(
        company: UUID,
        exceptClaim: UUID,
        digests: Set<String>,
    ): Set<String> =
        sql.selectDistinct(R.SHA256)
            .from(R)
            .join(C)
            .on(
                C.COMPANY_ID.eq(R.COMPANY_ID)
                    .and(C.ID.eq(R.CLAIM_ID))
                    .and(C.LATEST_SUBMISSION_ID.eq(R.SUBMISSION_ID))
            )
            .where(C.COMPANY_ID.eq(company))
            .and(C.ID.ne(exceptClaim))
            .and(C.STATUS.eq("PENDING"))
            .and(R.SHA256.`in`(digests))
            .orderBy(R.SHA256)
            .limit(60)
            .fetch(R.SHA256)
            .toSet()

    override fun insert(row: ExpenseSubmissionsRecord) {
        sql.insertInto(S).set(row).execute()
    }

    override fun insertLines(rows: List<ExpenseSubmittedLinesRecord>) {
        if (rows.isNotEmpty()) sql.batchInsert(rows).execute()
    }

    override fun insertReceipts(rows: List<ExpenseSubmittedReceiptsRecord>) {
        if (rows.isNotEmpty()) sql.batchInsert(rows).execute()
    }
}
