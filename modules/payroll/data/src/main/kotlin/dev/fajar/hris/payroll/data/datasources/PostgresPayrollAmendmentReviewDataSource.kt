package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.schema.tables.PayrollAmendmentReviewChanges.PAYROLL_AMENDMENT_REVIEW_CHANGES as H
import dev.fajar.hris.schema.tables.PayrollAmendmentReviews.PAYROLL_AMENDMENT_REVIEWS as R
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext

class PostgresPayrollAmendmentReviewDataSource(private val sql: DSLContext) :
    PayrollAmendmentReviewDataSource {
    override fun find(company: UUID, id: UUID): PayrollAmendmentReviewsRecord? =
        sql.selectFrom(R).where(R.COMPANY_ID.eq(company), R.ID.eq(id)).fetchOne()

    override fun latest(company: UUID, amendmentId: UUID): PayrollAmendmentReviewsRecord? =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company), R.AMENDMENT_ID.eq(amendmentId))
            .orderBy(R.REVIEW_NUMBER.desc())
            .limit(1)
            .fetchOne()

    override fun list(company: UUID, amendmentId: UUID): List<PayrollAmendmentReviewsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company), R.AMENDMENT_ID.eq(amendmentId))
            .orderBy(R.REVIEW_NUMBER)
            .limit(8)
            .fetch()

    override fun insert(row: PayrollAmendmentReviewsRecord) {
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

    override fun append(row: PayrollAmendmentReviewChangesRecord) {
        sql.insertInto(H).set(row).execute()
    }

    override fun changes(company: UUID, id: UUID): List<PayrollAmendmentReviewChangesRecord> =
        sql.selectFrom(H)
            .where(H.COMPANY_ID.eq(company), H.REVIEW_ID.eq(id))
            .orderBy(H.REVISION)
            .limit(10)
            .fetch()
}
