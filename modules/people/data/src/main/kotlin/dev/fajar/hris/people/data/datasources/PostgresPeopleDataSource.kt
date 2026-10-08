package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import dev.fajar.hris.schema.tables.EmploymentRevisions.EMPLOYMENT_REVISIONS as R
import dev.fajar.hris.schema.tables.Employments.EMPLOYMENTS as E
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPeopleDataSource(private val sql: DSLContext) : PeopleDataSource {
    override fun lock(companyId: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "people:$companyId")
            .execute()
    }

    override fun find(companyId: UUID, id: UUID, asOf: LocalDate): EmployeesAtRecord? {
        val employees = EMPLOYEES_AT.call(companyId, asOf)
        return sql.selectFrom(employees).where(employees.ID.eq(id)).fetchOne()
    }

    override fun list(
        companyId: UUID,
        accountId: UUID,
        visibility: String,
        asOf: LocalDate,
        query: String,
        after: String?,
        limit: Int,
    ): List<EmployeesAtRecord> {
        val employees = EMPLOYEES_AT.call(companyId, asOf)
        val scope =
            when (visibility) {
                "ALL" -> DSL.noCondition()
                "TEAM" -> employees.MANAGER_ACCOUNT_ID.eq(accountId)
                "SELF" -> employees.ACCOUNT_ID.eq(accountId)
                else -> DSL.falseCondition()
            }
        return sql.selectFrom(employees)
            .where(scope)
            .and(
                employees.LEGAL_NAME.containsIgnoreCase(query)
                    .or(employees.EMPLOYEE_NUMBER.containsIgnoreCase(query))
            )
            .and(after?.let { employees.EMPLOYEE_NUMBER.gt(it) } ?: DSL.noCondition())
            .orderBy(employees.EMPLOYEE_NUMBER)
            .limit(limit)
            .fetch()
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<EmploymentRevisionsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.EMPLOYMENT_ID.eq(id))
            .and(after?.let { R.REVISION.lt(it) } ?: DSL.noCondition())
            .orderBy(R.REVISION.desc())
            .limit(limit)
            .fetch()

    override fun reportingHistory(
        companyId: UUID,
        employeeId: UUID,
        managerId: UUID?,
    ): List<EmploymentRevisionsRecord> =
        sql.fetch(
                """
        with recursive reachable(id) as (
            values (?::uuid),(?::uuid)
            union select h.manager_id from employment_revisions h join reachable r on r.id=h.employment_id
                where h.company_id=? and h.manager_id is not null
        ) select h.* from employment_revisions h join reachable r on r.id=h.employment_id where h.company_id=?
    """,
                employeeId,
                managerId,
                companyId,
                companyId,
            )
            .into(R)

    override fun insertPerson(row: PersonsRecord) {
        sql.insertInto(P).set(row).execute()
    }

    override fun insertEmployment(row: EmploymentsRecord) {
        sql.insertInto(E).set(row).execute()
    }

    override fun insertRevision(row: EmploymentRevisionsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun advanceVersion(companyId: UUID, id: UUID, expectedVersion: Long): Long? =
        sql.update(E)
            .set(E.VERSION, expectedVersion + 1)
            .where(E.COMPANY_ID.eq(companyId))
            .and(E.ID.eq(id))
            .and(E.VERSION.eq(expectedVersion))
            .returning(E.VERSION)
            .fetchOne()
            ?.version
}
