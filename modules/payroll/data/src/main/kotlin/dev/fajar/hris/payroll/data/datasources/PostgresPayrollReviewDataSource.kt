package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollRunTotalsRow
import dev.fajar.hris.schema.tables.PayrollReviewChanges.PAYROLL_REVIEW_CHANGES as H
import dev.fajar.hris.schema.tables.PayrollReviews.PAYROLL_REVIEWS as R
import dev.fajar.hris.schema.tables.PayrollRunResults.PAYROLL_RUN_RESULTS as O
import dev.fajar.hris.schema.tables.records.*
import java.math.BigDecimal
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollReviewDataSource(private val sql: DSLContext) : PayrollReviewDataSource {
    override fun find(company: UUID, id: UUID): PayrollReviewsRecord? =
        sql.selectFrom(R).where(R.COMPANY_ID.eq(company), R.ID.eq(id)).fetchOne()

    override fun list(
        company: UUID,
        run: UUID,
        after: Int?,
        limit: Int,
    ): List<PayrollReviewsRecord> =
        sql.selectFrom(R)
            .where(
                R.COMPANY_ID.eq(company),
                R.RUN_ID.eq(run),
                after?.let { R.REVIEW_NUMBER.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(R.REVIEW_NUMBER)
            .limit(limit)
            .fetch()

    override fun latest(company: UUID, run: UUID): PayrollReviewsRecord? =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company), R.RUN_ID.eq(run))
            .orderBy(R.REVIEW_NUMBER.desc())
            .limit(1)
            .fetchOne()

    override fun totals(company: UUID, run: UUID): PayrollRunTotalsRow =
        sql.select(
                DSL.count(),
                DSL.coalesce(DSL.sum(O.TAXABLE_GROSS), BigDecimal.ZERO),
                DSL.coalesce(DSL.sum(O.WITHHELD), BigDecimal.ZERO),
                DSL.coalesce(DSL.sum(O.TAKE_HOME), BigDecimal.ZERO),
            )
            .from(O)
            .where(O.COMPANY_ID.eq(company), O.RUN_ID.eq(run), O.STATUS.eq("SUCCEEDED"))
            .fetchSingle { PayrollRunTotalsRow(it.value1(), it.value2(), it.value3(), it.value4()) }

    override fun insert(row: PayrollReviewsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun update(company: UUID, id: UUID, version: Long, status: String): Long? =
        sql.update(R)
            .set(R.VERSION, version + 1)
            .set(R.STATUS, status)
            .where(R.COMPANY_ID.eq(company), R.ID.eq(id), R.VERSION.eq(version))
            .returning(R.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: PayrollReviewChangesRecord) {
        sql.insertInto(H).set(row).execute()
    }

    override fun changes(company: UUID, review: UUID): List<PayrollReviewChangesRecord> =
        sql.selectFrom(H)
            .where(H.COMPANY_ID.eq(company), H.REVIEW_ID.eq(review))
            .orderBy(H.REVISION)
            .limit(10)
            .fetch()
}
