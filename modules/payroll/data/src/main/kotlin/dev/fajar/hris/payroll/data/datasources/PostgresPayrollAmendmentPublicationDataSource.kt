package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.schema.tables.PayrollAmendmentPublications.PAYROLL_AMENDMENT_PUBLICATIONS as P
import dev.fajar.hris.schema.tables.records.PayrollAmendmentPublicationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresPayrollAmendmentPublicationDataSource(private val sql: DSLContext) :
    PayrollAmendmentPublicationDataSource {
    override fun find(
        company: UUID,
        amendment: UUID,
        id: UUID,
    ): PayrollAmendmentPublicationsRecord? =
        sql.selectFrom(P)
            .where(P.COMPANY_ID.eq(company), P.AMENDMENT_ID.eq(amendment), P.ID.eq(id))
            .fetchOne()

    override fun forJob(company: UUID, job: UUID): PayrollAmendmentPublicationsRecord? =
        sql.selectFrom(P).where(P.COMPANY_ID.eq(company), P.JOB_ID.eq(job)).fetchOne()

    override fun list(company: UUID, amendment: UUID): List<PayrollAmendmentPublicationsRecord> =
        sql.selectFrom(P)
            .where(P.COMPANY_ID.eq(company), P.AMENDMENT_ID.eq(amendment))
            .orderBy(P.ATTEMPT)
            .limit(8)
            .fetch()

    override fun insert(row: PayrollAmendmentPublicationsRecord) {
        sql.insertInto(P).set(row).execute()
    }

    override fun finish(
        company: UUID,
        id: UUID,
        status: String,
        at: OffsetDateTime,
        actorId: UUID,
        code: String?,
    ): Boolean =
        sql.update(P)
            .set(P.STATUS, status)
            .set(P.COMPLETED_AT, at)
            .set(P.COMPLETED_BY, actorId)
            .set(P.FAILURE_CODE, code)
            .where(P.COMPANY_ID.eq(company), P.ID.eq(id), P.STATUS.eq("PENDING"))
            .execute() == 1

    override fun insertRevisions(company: UUID, publication: UUID, at: OffsetDateTime) {
        sql.execute(
            """
            INSERT INTO payroll_assessment_revisions(company_id,assessment_id,revision,amendment_calculation_id,published_at,amendment_publication_id)
            SELECT o.company_id,o.assessment_id,o.assessment_revision+1,o.calculation_id,?::timestamptz,p.id
            FROM payroll_amendment_results o JOIN payroll_amendment_publications p
              ON p.company_id=o.company_id AND p.calculation_id=o.calculation_id
            WHERE p.company_id=? AND p.id=? ORDER BY o.ordinal
            """
                .trimIndent(),
            at,
            company,
            publication,
        )
    }

    override fun advanceHeads(company: UUID, publication: UUID) {
        sql.execute(
            """
            UPDATE payroll_assessment_heads h SET revision=o.assessment_revision+1
            FROM payroll_amendment_results o JOIN payroll_amendment_publications p
              ON p.company_id=o.company_id AND p.calculation_id=o.calculation_id
            WHERE p.company_id=? AND p.id=? AND h.company_id=o.company_id AND h.assessment_id=o.assessment_id
              AND h.revision=o.assessment_revision
            """
                .trimIndent(),
            company,
            publication,
        )
    }
}
