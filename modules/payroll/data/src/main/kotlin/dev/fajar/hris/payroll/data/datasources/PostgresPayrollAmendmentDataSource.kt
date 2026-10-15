package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.mappers.toPayrollAmendmentSummaryRow
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.PayrollAmendmentCalculations.PAYROLL_AMENDMENT_CALCULATIONS as C
import dev.fajar.hris.schema.tables.PayrollAmendmentResults.PAYROLL_AMENDMENT_RESULTS as O
import dev.fajar.hris.schema.tables.PayrollAmendmentRevisions.PAYROLL_AMENDMENT_REVISIONS as R
import dev.fajar.hris.schema.tables.PayrollAmendments.PAYROLL_AMENDMENTS as H
import dev.fajar.hris.schema.tables.PayrollAssessments.PAYROLL_ASSESSMENTS as A
import dev.fajar.hris.schema.tables.PayrollRunTargets.PAYROLL_RUN_TARGETS as T
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL

private val amendmentSummaryFields =
    arrayOf(
        *H.fields(),
        R.REVISION,
        R.STATUS,
        R.ACTION,
        R.LATEST_CALCULATION_ID,
        R.LATEST_REVIEW_ID,
        R.ACTOR_ID,
        R.RECORDED_AT,
        R.REASON,
    )

class PostgresPayrollAmendmentDataSource(private val sql: DSLContext) : PayrollAmendmentDataSource {
    override fun preparers(company: UUID, id: UUID): Set<UUID> =
        sql.selectDistinct(R.ACTOR_ID)
            .from(R)
            .where(
                R.COMPANY_ID.eq(company),
                R.AMENDMENT_ID.eq(id),
                R.ACTION.`in`("SAVED", "CALCULATED", "CALCULATION_FAILED", "SUBMITTED"),
            )
            .orderBy(R.ACTOR_ID)
            .limit(201)
            .fetchSet(R.ACTOR_ID)

    override fun summary(company: UUID, id: UUID): PayrollAmendmentSummaryRow? =
        sql.select(*amendmentSummaryFields)
            .from(H)
            .join(R)
            .on(
                R.COMPANY_ID.eq(H.COMPANY_ID)
                    .and(R.AMENDMENT_ID.eq(H.ID))
                    .and(R.REVISION.eq(H.VERSION))
            )
            .where(H.COMPANY_ID.eq(company), H.ID.eq(id))
            .fetchOne { it.toPayrollAmendmentSummaryRow() }

    override fun find(company: UUID, id: UUID): PayrollAmendmentRow? =
        sql.select(*amendmentSummaryFields, R.TERMS)
            .from(H)
            .join(R)
            .on(
                R.COMPANY_ID.eq(H.COMPANY_ID)
                    .and(R.AMENDMENT_ID.eq(H.ID))
                    .and(R.REVISION.eq(H.VERSION))
            )
            .where(H.COMPANY_ID.eq(company), H.ID.eq(id))
            .fetchOne {
                PayrollAmendmentRow(it.toPayrollAmendmentSummaryRow(), it.get(R.TERMS)!!.data())
            }

    override fun revision(company: UUID, id: UUID, version: Long): PayrollAmendmentRow? =
        sql.select(*amendmentSummaryFields, R.TERMS)
            .from(H)
            .join(R)
            .on(R.COMPANY_ID.eq(H.COMPANY_ID).and(R.AMENDMENT_ID.eq(H.ID)))
            .where(H.COMPANY_ID.eq(company), H.ID.eq(id), R.REVISION.eq(version))
            .fetchOne {
                PayrollAmendmentRow(it.toPayrollAmendmentSummaryRow(), it.get(R.TERMS)!!.data())
            }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<PayrollAmendmentSummaryRow> =
        sql.select(*amendmentSummaryFields)
            .from(H)
            .join(R)
            .on(R.COMPANY_ID.eq(H.COMPANY_ID).and(R.AMENDMENT_ID.eq(H.ID)))
            .where(
                H.COMPANY_ID.eq(company),
                H.ID.eq(id),
                after?.let { R.REVISION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(R.REVISION)
            .limit(limit)
            .fetch { it.toPayrollAmendmentSummaryRow() }

    override fun list(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        employeeId: UUID?,
        status: String?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<PayrollAmendmentSummaryRow> =
        sql.select(*amendmentSummaryFields)
            .from(H)
            .join(R)
            .on(
                R.COMPANY_ID.eq(H.COMPANY_ID)
                    .and(R.AMENDMENT_ID.eq(H.ID))
                    .and(R.REVISION.eq(H.VERSION))
            )
            .where(
                H.COMPANY_ID.eq(company),
                H.FIRST_MONTH.between(from, until),
                DSL.notExists(
                    sql.selectOne()
                        .from(P)
                        .where(P.ID.eq(H.PERSON_ID), P.ACCOUNT_ID.eq(excludedAccountId))
                ),
                employeeId?.let { H.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition(),
                status?.let { R.STATUS.eq(it) } ?: DSL.noCondition(),
                H.EMPLOYEE_NAME.containsIgnoreCase(query)
                    .or(H.EMPLOYEE_NUMBER.containsIgnoreCase(query)),
                after?.let { H.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(H.ID)
            .limit(limit)
            .fetch { it.toPayrollAmendmentSummaryRow() }

    override fun insert(row: PayrollAmendmentsRecord) {
        sql.insertInto(H).set(row).execute()
    }

    override fun advance(company: UUID, id: UUID, expectedVersion: Long): Long? =
        sql.update(H)
            .set(H.VERSION, expectedVersion + 1)
            .where(H.COMPANY_ID.eq(company), H.ID.eq(id), H.VERSION.eq(expectedVersion))
            .returning(H.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: PayrollAmendmentRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun calculationSummary(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): PayrollAmendmentCalculationsRecord? =
        sql.selectFrom(C)
            .where(C.COMPANY_ID.eq(company), C.AMENDMENT_ID.eq(amendmentId), C.ID.eq(calculationId))
            .fetchOne()

    override fun calculation(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): PayrollAmendmentCalculationRow? {
        val references =
            DSL.field("{0}->'sourceVersions'", JSONB::class.java, R.TERMS).`as`("source_references")
        return sql.select(*C.fields(), H.FIRST_ASSESSMENT_ID, references)
            .from(C)
            .join(H)
            .on(H.COMPANY_ID.eq(C.COMPANY_ID).and(H.ID.eq(C.AMENDMENT_ID)))
            .join(R)
            .on(
                R.COMPANY_ID.eq(C.COMPANY_ID)
                    .and(R.AMENDMENT_ID.eq(C.AMENDMENT_ID))
                    .and(R.REVISION.eq(C.DRAFT_REVISION))
            )
            .where(C.COMPANY_ID.eq(company), C.AMENDMENT_ID.eq(amendmentId), C.ID.eq(calculationId))
            .fetchOne {
                PayrollAmendmentCalculationRow(
                    it.into(C),
                    it.get(H.FIRST_ASSESSMENT_ID)!!,
                    it.get(references)!!.data(),
                )
            }
    }

    override fun results(company: UUID, calculationId: UUID): List<PayrollAmendmentResultRow> =
        sql.select(*O.fields(), *T.fields())
            .from(O)
            .join(A)
            .on(A.COMPANY_ID.eq(O.COMPANY_ID).and(A.ID.eq(O.ASSESSMENT_ID)))
            .join(T)
            .on(
                T.COMPANY_ID.eq(A.COMPANY_ID)
                    .and(T.RUN_ID.eq(A.RUN_ID))
                    .and(T.ORDINAL.eq(A.ORDINAL))
            )
            .where(O.COMPANY_ID.eq(company), O.CALCULATION_ID.eq(calculationId))
            .orderBy(O.ORDINAL)
            .limit(13)
            .fetch { PayrollAmendmentResultRow(it.into(O), it.into(T)) }

    override fun insertCalculation(row: PayrollAmendmentCalculationsRecord) {
        sql.insertInto(C).set(row).execute()
    }

    override fun insertResults(rows: List<PayrollAmendmentResultsRecord>) {
        sql.batchInsert(rows).execute()
    }
}
