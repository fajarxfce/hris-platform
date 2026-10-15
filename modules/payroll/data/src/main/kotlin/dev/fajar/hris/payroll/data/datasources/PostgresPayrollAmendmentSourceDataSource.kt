package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.PayrollAssessmentEvidence.PAYROLL_ASSESSMENT_EVIDENCE as E
import dev.fajar.hris.schema.tables.PayrollAssessmentHeads.PAYROLL_ASSESSMENT_HEADS as HEAD
import dev.fajar.hris.schema.tables.PayrollAssessmentRevisions.PAYROLL_ASSESSMENT_REVISIONS as V
import dev.fajar.hris.schema.tables.PayrollAssessments.PAYROLL_ASSESSMENTS as A
import dev.fajar.hris.schema.tables.PayrollFinalizations.PAYROLL_FINALIZATIONS as F
import dev.fajar.hris.schema.tables.PayrollRunTargets.PAYROLL_RUN_TARGETS as T
import dev.fajar.hris.schema.tables.PayrollTaxOpeningRevisions.PAYROLL_TAX_OPENING_REVISIONS as O
import dev.fajar.hris.schema.tables.PayrollTaxOpenings.PAYROLL_TAX_OPENINGS as H
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollAmendmentSourceDataSource(private val sql: DSLContext) :
    PayrollAmendmentSourceDataSource {
    override fun candidates(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        employeeId: UUID?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<PayrollAmendmentCandidateRow> =
        sql.select(
                A.COMPANY_ID,
                A.ID,
                A.EMPLOYMENT_ID,
                T.EMPLOYEE_NUMBER,
                T.EMPLOYEE_NAME,
                A.TAX_MONTH,
                V.REVISION,
                V.PUBLISHED_AT,
            )
            .from(A)
            .join(HEAD)
            .on(HEAD.COMPANY_ID.eq(A.COMPANY_ID).and(HEAD.ASSESSMENT_ID.eq(A.ID)))
            .join(V)
            .on(
                V.COMPANY_ID.eq(HEAD.COMPANY_ID)
                    .and(V.ASSESSMENT_ID.eq(HEAD.ASSESSMENT_ID))
                    .and(V.REVISION.eq(HEAD.REVISION))
            )
            .join(T)
            .on(
                T.COMPANY_ID.eq(A.COMPANY_ID)
                    .and(T.RUN_ID.eq(A.RUN_ID))
                    .and(T.ORDINAL.eq(A.ORDINAL))
            )
            .join(F)
            .on(
                F.COMPANY_ID.eq(A.COMPANY_ID)
                    .and(F.ID.eq(A.FINALIZATION_ID))
                    .and(F.PUBLISHED_AT.isNotNull)
            )
            .where(
                A.COMPANY_ID.eq(company),
                A.TAX_MONTH.between(from, until),
                employeeId?.let { A.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition(),
                DSL.notExists(
                    sql.selectOne()
                        .from(P)
                        .where(P.ID.eq(A.PERSON_ID), P.ACCOUNT_ID.eq(excludedAccountId))
                ),
                T.EMPLOYEE_NAME.containsIgnoreCase(query)
                    .or(T.EMPLOYEE_NUMBER.containsIgnoreCase(query)),
                after?.let { A.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(A.ID)
            .limit(limit)
            .fetch {
                PayrollAmendmentCandidateRow(
                    it.get(A.COMPANY_ID)!!,
                    it.get(A.ID)!!,
                    it.get(A.EMPLOYMENT_ID)!!,
                    it.get(T.EMPLOYEE_NUMBER)!!,
                    it.get(T.EMPLOYEE_NAME)!!,
                    it.get(A.TAX_MONTH)!!,
                    it.get(V.REVISION)!!,
                    it.get(V.PUBLISHED_AT)!!,
                )
            }

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
                E.FACTS,
                E.CALCULATION,
                E.REVISION,
                E.PREVIOUS_ASSESSMENT_REVISION,
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
            .join(HEAD)
            .on(HEAD.COMPANY_ID.eq(A.COMPANY_ID).and(HEAD.ASSESSMENT_ID.eq(A.ID)))
            .join(E)
            .on(
                E.COMPANY_ID.eq(A.COMPANY_ID)
                    .and(E.ASSESSMENT_ID.eq(A.ID))
                    .and(E.REVISION.eq(HEAD.REVISION))
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
                    it.get(E.FACTS)!!.data(),
                    it.get(E.CALCULATION)!!.data(),
                    it.get(E.REVISION)!!,
                    it.get(E.PREVIOUS_ASSESSMENT_REVISION)!!,
                )
            }
    }
}
