package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollEmployeeRow
import dev.fajar.hris.schema.tables.Employments.EMPLOYMENTS as E
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPayrollEmployeeDataSource(private val sql: DSLContext) : PayrollEmployeeDataSource {
    override fun employee(company: UUID, employee: UUID): PayrollEmployeeRow? =
        sql.select(E.ID, E.EMPLOYEE_NUMBER, P.LEGAL_NAME, E.VERSION)
            .from(E)
            .join(P)
            .on(P.ID.eq(E.PERSON_ID))
            .where(E.COMPANY_ID.eq(company), E.ID.eq(employee))
            .fetchOne {
                PayrollEmployeeRow(
                    requireNotNull(it.value1()),
                    requireNotNull(it.value2()),
                    requireNotNull(it.value3()),
                    requireNotNull(it.value4()),
                )
            }

    override fun employees(
        company: UUID,
        query: String,
        after: String?,
        limit: Int,
    ): List<PayrollEmployeeRow> =
        sql.select(E.ID, E.EMPLOYEE_NUMBER, P.LEGAL_NAME, E.VERSION)
            .from(E)
            .join(P)
            .on(P.ID.eq(E.PERSON_ID))
            .where(E.COMPANY_ID.eq(company))
            .and(
                E.EMPLOYEE_NUMBER.containsIgnoreCase(query)
                    .or(P.LEGAL_NAME.containsIgnoreCase(query))
            )
            .and(after?.let { E.EMPLOYEE_NUMBER.gt(it) } ?: DSL.noCondition())
            .orderBy(E.EMPLOYEE_NUMBER)
            .limit(limit)
            .fetch {
                PayrollEmployeeRow(
                    requireNotNull(it.value1()),
                    requireNotNull(it.value2()),
                    requireNotNull(it.value3()),
                    requireNotNull(it.value4()),
                )
            }
}
