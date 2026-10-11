package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.DirectoryEmployeeRow
import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import dev.fajar.hris.schema.tables.OrganizationUnits.ORGANIZATION_UNITS
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCompanyDirectoryDataSource(private val sql: DSLContext) : CompanyDirectoryDataSource {
    override fun list(
        companyId: UUID,
        asOf: LocalDate,
        statuses: Set<String>,
        query: String,
        after: String?,
        limit: Int,
    ): List<DirectoryEmployeeRow> {
        val employees = EMPLOYEES_AT.call(companyId, asOf)
        val positions = ORGANIZATION_UNITS.`as`("directory_position")
        val departments = ORGANIZATION_UNITS.`as`("directory_department")
        return sql.select(
                employees.ID,
                employees.EMPLOYEE_NUMBER,
                employees.LEGAL_NAME,
                positions.NAME,
                departments.NAME,
            )
            .from(employees)
            .leftJoin(positions)
            .on(positions.COMPANY_ID.eq(companyId).and(positions.ID.eq(employees.POSITION_ID)))
            .leftJoin(departments)
            .on(
                departments.COMPANY_ID.eq(companyId).and(departments.ID.eq(employees.DEPARTMENT_ID))
            )
            .where(employees.COMPANY_ID.eq(companyId))
            .and(employees.STATUS.`in`(statuses))
            .and(employees.START_DATE.le(asOf))
            .and(employees.END_DATE.isNull.or(employees.END_DATE.ge(asOf)))
            .and(
                employees.LEGAL_NAME.containsIgnoreCase(query)
                    .or(employees.EMPLOYEE_NUMBER.containsIgnoreCase(query))
            )
            .and(after?.let { employees.EMPLOYEE_NUMBER.gt(it) } ?: DSL.noCondition())
            .orderBy(employees.EMPLOYEE_NUMBER)
            .limit(limit)
            .fetch { row ->
                DirectoryEmployeeRow(
                    requireNotNull(row[employees.ID]),
                    requireNotNull(row[employees.EMPLOYEE_NUMBER]),
                    requireNotNull(row[employees.LEGAL_NAME]),
                    row[positions.NAME],
                    row[departments.NAME],
                )
            }
    }
}
