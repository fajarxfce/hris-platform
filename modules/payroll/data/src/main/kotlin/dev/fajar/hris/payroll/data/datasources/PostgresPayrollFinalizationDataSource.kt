package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollPublicationReadinessRow
import dev.fajar.hris.schema.tables.PayrollFinalizations.PAYROLL_FINALIZATIONS as F
import dev.fajar.hris.schema.tables.records.PayrollFinalizationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollFinalizationDataSource(private val sql: DSLContext) :
    PayrollFinalizationDataSource {
    override fun find(company: UUID, id: UUID): PayrollFinalizationsRecord? =
        sql.selectFrom(F).where(F.COMPANY_ID.eq(company), F.ID.eq(id)).fetchOne()

    override fun forJob(company: UUID, job: UUID): PayrollFinalizationsRecord? =
        sql.selectFrom(F).where(F.COMPANY_ID.eq(company), F.JOB_ID.eq(job)).fetchOne()

    override fun latest(company: UUID, run: UUID): PayrollFinalizationsRecord? =
        sql.selectFrom(F)
            .where(F.COMPANY_ID.eq(company), F.RUN_ID.eq(run))
            .orderBy(F.ATTEMPT.desc())
            .limit(1)
            .fetchOne()

    override fun list(
        company: UUID,
        run: UUID,
        after: Int?,
        limit: Int,
    ): List<PayrollFinalizationsRecord> =
        sql.selectFrom(F)
            .where(
                F.COMPANY_ID.eq(company),
                F.RUN_ID.eq(run),
                after?.let { F.ATTEMPT.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(F.ATTEMPT)
            .limit(limit)
            .fetch()

    override fun insert(row: PayrollFinalizationsRecord) {
        sql.insertInto(F).set(row).execute()
    }

    override fun readiness(company: UUID, run: UUID): PayrollPublicationReadinessRow =
        sql.fetchOne("select * from payroll_publication_readiness(?,?)", company, run)!!.let {
            PayrollPublicationReadinessRow(
                it.get("changed", Int::class.javaObjectType)!!,
                it.get("duplicates", Int::class.javaObjectType)!!,
                it.get("assessed", Int::class.javaObjectType)!!,
                it.get("holidays", Int::class.javaObjectType)!!,
            )
        }

    override fun insertAssessments(company: UUID, finalization: UUID, at: OffsetDateTime) {
        sql.execute(
            "select insert_payroll_assessments(?,?,?::timestamptz)",
            company,
            finalization,
            at,
        )
    }

    override fun publish(company: UUID, id: UUID, at: OffsetDateTime): Boolean =
        sql.update(F)
            .set(F.PUBLISHED_AT, at)
            .where(F.COMPANY_ID.eq(company), F.ID.eq(id), F.PUBLISHED_AT.isNull)
            .execute() == 1
}
