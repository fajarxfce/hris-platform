package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.PayrollAssessments.PAYROLL_ASSESSMENTS as A
import dev.fajar.hris.schema.tables.PayrollFinalizations.PAYROLL_FINALIZATIONS as F
import dev.fajar.hris.schema.tables.PayrollRunResults.PAYROLL_RUN_RESULTS as R
import dev.fajar.hris.schema.tables.PayrollRunTargets.PAYROLL_RUN_TARGETS as T
import dev.fajar.hris.schema.tables.PayrollTaxOpeningRevisions.PAYROLL_TAX_OPENING_REVISIONS as O
import dev.fajar.hris.schema.tables.PayrollTaxOpenings.PAYROLL_TAX_OPENINGS as H
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollAmendmentSourceDataSource(private val sql: DSLContext) :
    PayrollAmendmentSourceDataSource {
    override fun sources(company: UUID, assessmentId: UUID): List<PayrollAmendmentSourceRow> {
        val selected = A.`as`("selected_assessment")
        val start =
            DSL.field("date_trunc('year', {0})::date", LocalDate::class.java, selected.TAX_MONTH)
        val end =
            DSL.field(
                "(date_trunc('year', {0}) + interval '1 year')::date",
                LocalDate::class.java,
                selected.TAX_MONTH,
            )
        return sql.select(
                A.COMPANY_ID,
                A.ID,
                A.PERSON_ID,
                A.TAX_MONTH,
                *T.fields(),
                H.EMPLOYMENT_ID,
                H.TAX_YEAR,
                *O.fields(),
                R.FACTS,
                R.CALCULATION,
            )
            .from(selected)
            .join(A)
            .on(
                A.COMPANY_ID.eq(selected.COMPANY_ID)
                    .and(A.PERSON_ID.eq(selected.PERSON_ID))
                    .and(A.TAX_MONTH.ge(start))
                    .and(A.TAX_MONTH.lt(end))
            )
            .join(F)
            .on(
                F.COMPANY_ID.eq(A.COMPANY_ID)
                    .and(F.ID.eq(A.FINALIZATION_ID))
                    .and(F.PUBLISHED_AT.isNotNull)
            )
            .join(T)
            .on(
                T.COMPANY_ID.eq(A.COMPANY_ID)
                    .and(T.RUN_ID.eq(A.RUN_ID))
                    .and(T.ORDINAL.eq(A.ORDINAL))
            )
            .join(R)
            .on(
                R.COMPANY_ID.eq(A.COMPANY_ID)
                    .and(R.RUN_ID.eq(A.RUN_ID))
                    .and(R.ORDINAL.eq(A.ORDINAL))
            )
            .join(H)
            .on(H.COMPANY_ID.eq(A.COMPANY_ID).and(H.ID.eq(T.TAX_OPENING_ID)))
            .join(O)
            .on(
                O.COMPANY_ID.eq(H.COMPANY_ID)
                    .and(O.OPENING_ID.eq(H.ID))
                    .and(O.REVISION.eq(T.TAX_OPENING_REVISION))
            )
            .where(selected.COMPANY_ID.eq(company), selected.ID.eq(assessmentId))
            .orderBy(A.TAX_MONTH)
            .limit(13)
            .fetch {
                PayrollAmendmentSourceRow(
                    it.get(A.COMPANY_ID)!!,
                    it.get(A.ID)!!,
                    it.get(A.PERSON_ID)!!,
                    it.get(A.TAX_MONTH)!!,
                    it.into(T),
                    PayrollTaxOpeningRow(
                        it.get(H.EMPLOYMENT_ID)!!,
                        it.get(H.TAX_YEAR)!!,
                        it.into(O),
                    ),
                    it.get(R.FACTS)!!.data(),
                    it.get(R.CALCULATION)!!.data(),
                )
            }
    }
}
