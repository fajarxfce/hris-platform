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
        sql.fetchOne(
                """
                WITH run_scope AS MATERIALIZED (
                  SELECT id,company_id,earnings_month FROM payroll_runs WHERE company_id=? AND id=? LIMIT 1
                )
                SELECT count(*) FILTER(WHERE e.version<>t.employment_version)::integer changed,
                 (count(*)-count(DISTINCT e.person_id))::integer duplicates,
                 count(*) FILTER(WHERE EXISTS(SELECT 1 FROM payroll_assessments a WHERE a.company_id=r.company_id AND a.person_id=e.person_id
                   AND a.tax_month>=date_trunc('year',r.earnings_month)::date AND a.tax_month<(date_trunc('year',r.earnings_month)+interval '1 year')::date))::integer assessed,
                 count(*) FILTER(WHERE (o.calculation->'holiday'->>'amount')::numeric>0 AND EXISTS(SELECT 1 FROM payroll_assessments a WHERE a.company_id=r.company_id AND a.person_id=e.person_id
                   AND a.holiday_year=extract(year FROM (o.calculation->'holiday'->>'holidayDate')::date)::integer AND a.holiday_kind=o.facts->'input'->'holidayAllowance'->>'kind'))::integer holidays
                FROM run_scope r
                 CROSS JOIN LATERAL(SELECT t.ordinal,t.employment_id,t.employment_version FROM payroll_run_targets t
                   WHERE t.company_id=r.company_id AND t.run_id=r.id ORDER BY t.ordinal LIMIT 5000) t
                 CROSS JOIN LATERAL(SELECT e.person_id,e.version FROM employments e WHERE e.company_id=r.company_id AND e.id=t.employment_id LIMIT 1) e
                 CROSS JOIN LATERAL(SELECT o.facts,o.calculation FROM payroll_run_results o WHERE o.company_id=r.company_id AND o.run_id=r.id AND o.ordinal=t.ordinal LIMIT 1) o
                """
                    .trimIndent(),
                company,
                run,
            )!!
            .let {
                PayrollPublicationReadinessRow(
                    it.get("changed", Int::class.javaObjectType)!!,
                    it.get("duplicates", Int::class.javaObjectType)!!,
                    it.get("assessed", Int::class.javaObjectType)!!,
                    it.get("holidays", Int::class.javaObjectType)!!,
                )
            }

    override fun insertAssessments(company: UUID, finalization: UUID, at: OffsetDateTime) {
        sql.execute(
            """
            WITH publication AS MATERIALIZED (
              SELECT f.company_id,f.id,f.run_id,r.earnings_month FROM payroll_finalizations f
               JOIN payroll_runs r ON r.company_id=f.company_id AND r.id=f.run_id
              WHERE f.company_id=? AND f.id=? LIMIT 1
            )
            INSERT INTO payroll_assessments(company_id,id,finalization_id,run_id,ordinal,employment_id,person_id,tax_month,published_at,holiday_kind,holiday_year)
            SELECT f.company_id,gen_random_uuid(),f.id,f.run_id,t.ordinal,t.employment_id,e.person_id,f.earnings_month,?::timestamptz,
              CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN o.facts->'input'->'holidayAllowance'->>'kind' END,
              CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN extract(year FROM (o.calculation->'holiday'->>'holidayDate')::date)::integer END
            FROM publication f
              CROSS JOIN LATERAL(SELECT t.ordinal,t.employment_id FROM payroll_run_targets t
                WHERE t.company_id=f.company_id AND t.run_id=f.run_id ORDER BY t.ordinal LIMIT 5000) t
              CROSS JOIN LATERAL(SELECT e.person_id FROM employments e WHERE e.company_id=f.company_id AND e.id=t.employment_id LIMIT 1) e
              CROSS JOIN LATERAL(SELECT o.facts,o.calculation FROM payroll_run_results o
                WHERE o.company_id=f.company_id AND o.run_id=f.run_id AND o.ordinal=t.ordinal AND o.status='SUCCEEDED' LIMIT 1) o
            ORDER BY t.ordinal
            """
                .trimIndent(),
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
