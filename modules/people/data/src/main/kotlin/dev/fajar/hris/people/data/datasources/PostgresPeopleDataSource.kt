package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.queries.activeEmploymentRevision
import dev.fajar.hris.people.data.queries.employeesAtInstant
import dev.fajar.hris.people.data.queries.latestEmploymentRevision
import dev.fajar.hris.schema.Tables.EMPLOYMENT_REVISION_CANCELLATIONS as C
import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import dev.fajar.hris.schema.tables.EmploymentRevisions.EMPLOYMENT_REVISIONS as R
import dev.fajar.hris.schema.tables.Employments.EMPLOYMENTS as E
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPeopleDataSource(private val sql: DSLContext) : PeopleDataSource {
    override fun hasOpenEmploymentAtOrAfter(
        companyId: UUID,
        personId: UUID,
        exceptId: UUID?,
        from: LocalDate,
    ): Boolean {
        val current = EMPLOYEES_AT.call(companyId, from)
        val present =
            DSL.exists(
                sql.selectOne()
                    .from(current)
                    .where(current.PERSON_ID.eq(personId))
                    .and(exceptId?.let { current.ID.ne(it) } ?: DSL.noCondition())
                    .and(current.STATUS.ne("ENDED"))
                    .and(current.END_DATE.isNull.or(current.END_DATE.ge(from)))
            )
        val future =
            DSL.exists(
                sql.selectOne()
                    .from(R)
                    .join(E)
                    .on(E.COMPANY_ID.eq(R.COMPANY_ID).and(E.ID.eq(R.EMPLOYMENT_ID)))
                    .where(R.COMPANY_ID.eq(companyId))
                    .and(E.PERSON_ID.eq(personId))
                    .and(exceptId?.let { E.ID.ne(it) } ?: DSL.noCondition())
                    .and(R.EFFECTIVE_FROM.gt(from))
                    .and(R.STATUS.ne("ENDED"))
                    .and(R.END_DATE.isNull.or(R.END_DATE.ge(R.EFFECTIVE_FROM)))
                    .and(latestEmploymentRevision(R))
            )
        return sql.fetchExists(sql.selectOne().where(present.or(future)))
    }

    override fun hasReportingDependentsAtOrAfter(
        companyId: UUID,
        id: UUID,
        from: LocalDate,
    ): Boolean {
        val current = EMPLOYEES_AT.call(companyId, from)
        val present =
            DSL.exists(
                sql.selectOne()
                    .from(current)
                    .where(current.MANAGER_ID.eq(id))
                    .and(current.STATUS.ne("ENDED"))
                    .and(current.END_DATE.isNull.or(current.END_DATE.ge(from)))
            )
        val future =
            DSL.exists(
                sql.selectOne()
                    .from(R)
                    .where(R.COMPANY_ID.eq(companyId))
                    .and(R.MANAGER_ID.eq(id))
                    .and(R.EFFECTIVE_FROM.gt(from))
                    .and(R.STATUS.ne("ENDED"))
                    .and(R.END_DATE.isNull.or(R.END_DATE.ge(R.EFFECTIVE_FROM)))
                    .and(latestEmploymentRevision(R))
            )
        return sql.fetchExists(sql.selectOne().where(present.or(future)))
    }

    override fun currentVersion(companyId: UUID, id: UUID): Long? =
        sql.select(E.VERSION)
            .from(E)
            .where(E.COMPANY_ID.eq(companyId))
            .and(E.ID.eq(id))
            .fetchOne(E.VERSION)

    override fun findRevision(companyId: UUID, id: UUID, revision: Long) =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.EMPLOYMENT_ID.eq(id))
            .and(R.REVISION.eq(revision))
            .fetchOne()

    override fun hasRevisionsAfter(companyId: UUID, id: UUID, date: LocalDate) =
        sql.fetchExists(
            sql.selectOne()
                .from(R)
                .where(R.COMPANY_ID.eq(companyId))
                .and(R.EMPLOYMENT_ID.eq(id))
                .and(R.EFFECTIVE_FROM.gt(date))
                .and(activeEmploymentRevision(R))
        )

    override fun cancellations(
        companyId: UUID,
        id: UUID,
        revisions: Set<Long>,
    ): List<EmploymentRevisionCancellationsRecord> =
        sql.selectFrom(C)
            .where(C.COMPANY_ID.eq(companyId))
            .and(C.EMPLOYMENT_ID.eq(id))
            .and(C.REVISION.`in`(revisions))
            .fetch()

    override fun insertCancellation(record: EmploymentRevisionCancellationsRecord) {
        sql.executeInsert(record)
    }

    override fun employeeIds(companyId: UUID, limit: Int): List<UUID> =
        sql.select(E.ID)
            .from(E)
            .where(E.COMPANY_ID.eq(companyId))
            .orderBy(E.ID)
            .limit(limit)
            .fetch(E.ID)
            .map { requireNotNull(it) }

    override fun lock(companyId: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "people:$companyId")
            .execute()
    }

    override fun find(companyId: UUID, id: UUID, asOf: LocalDate): EmployeesAtRecord? {
        val employees = EMPLOYEES_AT.call(companyId, asOf)
        return sql.selectFrom(employees).where(employees.ID.eq(id)).fetchOne()
    }

    override fun findAtInstant(companyId: UUID, id: UUID, at: OffsetDateTime): EmployeesAtRecord? {
        val employees = employeesAtInstant(companyId, at)
        return sql.selectFrom(employees).where(employees.ID.eq(id)).fetchOne()
    }

    override fun list(
        companyId: UUID,
        accountId: UUID,
        visibility: String,
        asOf: LocalDate,
        accessAt: OffsetDateTime,
        query: String,
        after: String?,
        limit: Int,
    ): List<EmployeesAtRecord> {
        val employees = EMPLOYEES_AT.call(companyId, asOf)
        val current = employeesAtInstant(companyId, accessAt)
        val team =
            employees.ID.`in`(
                DSL.select(current.ID)
                    .from(current)
                    .where(current.MANAGER_ACCOUNT_ID.eq(accountId))
                    .and(current.STATUS.ne("ENDED"))
            )
        val scope =
            when (visibility) {
                "ALL" -> DSL.noCondition()
                "TEAM" -> team
                "TEAM_AND_SELF" -> team.or(employees.ACCOUNT_ID.eq(accountId))
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

    override fun effectiveRevisions(
        companyId: UUID,
        id: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<EmploymentRevisionsRecord> {
        val baseline = R.`as`("baseline")
        val baselineDate =
            DSL.select(DSL.max(baseline.EFFECTIVE_FROM))
                .from(baseline)
                .where(baseline.COMPANY_ID.eq(companyId))
                .and(baseline.EMPLOYMENT_ID.eq(id))
                .and(baseline.EFFECTIVE_FROM.le(from))
                .and(activeEmploymentRevision(baseline))
        return sql.select(R.asterisk())
            .distinctOn(R.EFFECTIVE_FROM)
            .from(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.EMPLOYMENT_ID.eq(id))
            .and(R.EFFECTIVE_FROM.le(until))
            .and(activeEmploymentRevision(R))
            .and(R.EFFECTIVE_FROM.ge(from).or(R.EFFECTIVE_FROM.eq(baselineDate)))
            .orderBy(R.EFFECTIVE_FROM, R.REVISION.desc())
            .fetchInto(R)
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
                where h.company_id=? and h.manager_id is not null and not exists(
                    select 1 from employment_revision_cancellations c where c.company_id=h.company_id and c.employment_id=h.employment_id and c.revision=h.revision)
        ) select h.* from employment_revisions h join reachable r on r.id=h.employment_id where h.company_id=? and not exists(
            select 1 from employment_revision_cancellations c where c.company_id=h.company_id and c.employment_id=h.employment_id and c.revision=h.revision)
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
