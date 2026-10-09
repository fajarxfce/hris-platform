package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.CompensationRow
import dev.fajar.hris.schema.tables.EmployeeCompensationRevisions.EMPLOYEE_COMPENSATION_REVISIONS as R
import dev.fajar.hris.schema.tables.EmployeeCompensations.EMPLOYEE_COMPENSATIONS as C
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCompensationDataSource(private val sql: DSLContext) : CompensationDataSource {
    override fun revision(company: UUID, employee: UUID, revision: Long): CompensationRow? =
        sql.select(C.VERSION, *R.fields())
            .from(C)
            .join(R)
            .on(R.COMPANY_ID.eq(C.COMPANY_ID).and(R.EMPLOYMENT_ID.eq(C.EMPLOYMENT_ID)))
            .where(C.COMPANY_ID.eq(company), C.EMPLOYMENT_ID.eq(employee), R.REVISION.eq(revision))
            .fetchOne { CompensationRow(it.get(C.VERSION)!!, it.into(R)) }

    override fun current(company: UUID, employee: UUID): CompensationRow? =
        sql.select(C.VERSION, *R.fields())
            .from(C)
            .join(R)
            .on(
                R.COMPANY_ID.eq(C.COMPANY_ID)
                    .and(R.EMPLOYMENT_ID.eq(C.EMPLOYMENT_ID))
                    .and(R.REVISION.eq(C.VERSION))
            )
            .where(C.COMPANY_ID.eq(company))
            .and(C.EMPLOYMENT_ID.eq(employee))
            .fetchOne { CompensationRow(it.get(C.VERSION)!!, it.into(R)) }

    override fun effective(company: UUID, employee: UUID, month: LocalDate): CompensationRow? =
        sql.select(C.VERSION, *R.fields())
            .from(C)
            .join(R)
            .on(R.COMPANY_ID.eq(C.COMPANY_ID).and(R.EMPLOYMENT_ID.eq(C.EMPLOYMENT_ID)))
            .where(C.COMPANY_ID.eq(company))
            .and(C.EMPLOYMENT_ID.eq(employee))
            .and(R.EFFECTIVE_FROM.le(month))
            .orderBy(R.EFFECTIVE_FROM.desc(), R.REVISION.desc())
            .limit(1)
            .fetchOne { CompensationRow(it.get(C.VERSION)!!, it.into(R)) }

    override fun list(
        company: UUID,
        month: LocalDate,
        after: UUID?,
        limit: Int,
    ): List<CompensationRow> =
        sql.select(C.VERSION, *R.fields())
            .distinctOn(C.EMPLOYMENT_ID)
            .from(C)
            .join(R)
            .on(R.COMPANY_ID.eq(C.COMPANY_ID).and(R.EMPLOYMENT_ID.eq(C.EMPLOYMENT_ID)))
            .where(C.COMPANY_ID.eq(company))
            .and(R.EFFECTIVE_FROM.le(month))
            .and(after?.let { C.EMPLOYMENT_ID.gt(it) } ?: DSL.noCondition())
            .orderBy(C.EMPLOYMENT_ID, R.EFFECTIVE_FROM.desc(), R.REVISION.desc())
            .limit(limit)
            .fetch { CompensationRow(it.get(C.VERSION)!!, it.into(R)) }

    override fun history(
        company: UUID,
        employee: UUID,
        after: Long?,
        limit: Int,
    ): List<CompensationRow> =
        sql.select(C.VERSION, *R.fields())
            .from(C)
            .join(R)
            .on(R.COMPANY_ID.eq(C.COMPANY_ID).and(R.EMPLOYMENT_ID.eq(C.EMPLOYMENT_ID)))
            .where(C.COMPANY_ID.eq(company))
            .and(C.EMPLOYMENT_ID.eq(employee))
            .and(after?.let { R.REVISION.gt(it) } ?: DSL.noCondition())
            .orderBy(R.REVISION)
            .limit(limit)
            .fetch { CompensationRow(it.get(C.VERSION)!!, it.into(R)) }

    override fun insert(row: EmployeeCompensationsRecord) {
        sql.insertInto(C).set(row).execute()
    }

    override fun advance(company: UUID, employee: UUID, expectedVersion: Long): Long? =
        sql.update(C)
            .set(C.VERSION, expectedVersion + 1)
            .where(C.COMPANY_ID.eq(company))
            .and(C.EMPLOYMENT_ID.eq(employee))
            .and(C.VERSION.eq(expectedVersion))
            .returning(C.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: EmployeeCompensationRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }
}
