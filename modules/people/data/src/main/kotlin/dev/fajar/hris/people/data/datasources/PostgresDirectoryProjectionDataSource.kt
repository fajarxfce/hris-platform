package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.DirectoryCandidateRow
import dev.fajar.hris.schema.tables.DirectoryEmployeeRecords.DIRECTORY_EMPLOYEE_RECORDS as D
import dev.fajar.hris.schema.tables.DirectoryRefreshQueue.DIRECTORY_REFRESH_QUEUE as Q
import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import dev.fajar.hris.schema.tables.EmploymentRevisionCancellations.EMPLOYMENT_REVISION_CANCELLATIONS as C
import dev.fajar.hris.schema.tables.EmploymentRevisions.EMPLOYMENT_REVISIONS as H
import dev.fajar.hris.schema.tables.Employments.EMPLOYMENTS as E
import dev.fajar.hris.schema.tables.OrganizationUnits.ORGANIZATION_UNITS
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import dev.fajar.hris.schema.tables.records.DirectoryEmployeeRecordsRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresDirectoryProjectionDataSource(private val sql: DSLContext) :
    DirectoryProjectionDataSource {
    override fun dueCompanies(now: Instant, limit: Int): List<UUID> =
        sql.select(Q.COMPANY_ID)
            .from(Q)
            .where(Q.REFRESH_AT.le(now.atOffset(ZoneOffset.UTC)))
            .groupBy(Q.COMPANY_ID)
            .orderBy(DSL.min(Q.REFRESH_AT), Q.COMPANY_ID)
            .limit(limit)
            .fetch(Q.COMPANY_ID)
            .map { requireNotNull(it) }

    override fun lockDue(company: UUID, now: Instant, limit: Int): List<UUID> =
        sql.select(Q.EMPLOYMENT_ID)
            .from(Q)
            .where(Q.COMPANY_ID.eq(company), Q.REFRESH_AT.le(now.atOffset(ZoneOffset.UTC)))
            .orderBy(Q.EMPLOYMENT_ID)
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .fetch(Q.EMPLOYMENT_ID)
            .map { requireNotNull(it) }

    override fun candidates(
        company: UUID,
        ids: List<UUID>,
        today: LocalDate,
    ): List<DirectoryCandidateRow> {
        val current = EMPLOYEES_AT.call(company, today)
        val position = ORGANIZATION_UNITS.`as`("directory_position")
        val department = ORGANIZATION_UNITS.`as`("directory_department")
        val next =
            DSL.select(DSL.min(H.EFFECTIVE_FROM))
                .from(H)
                .where(
                    H.COMPANY_ID.eq(E.COMPANY_ID),
                    H.EMPLOYMENT_ID.eq(E.ID),
                    H.EFFECTIVE_FROM.gt(today),
                )
                .andNotExists(
                    DSL.selectOne()
                        .from(C)
                        .where(
                            C.COMPANY_ID.eq(H.COMPANY_ID),
                            C.EMPLOYMENT_ID.eq(H.EMPLOYMENT_ID),
                            C.REVISION.eq(H.REVISION),
                        )
                )
                .asField<LocalDate>("next_revision")
        return sql.select(
                E.ID,
                E.EMPLOYEE_NUMBER,
                P.LEGAL_NAME,
                position.ID,
                position.NAME,
                department.ID,
                department.NAME,
                current.STATUS,
                current.START_DATE,
                current.END_DATE,
                next,
            )
            .from(E)
            .join(P)
            .on(P.ID.eq(E.PERSON_ID))
            .leftJoin(current)
            .on(current.ID.eq(E.ID))
            .leftJoin(position)
            .on(position.COMPANY_ID.eq(company), position.ID.eq(current.POSITION_ID))
            .leftJoin(department)
            .on(department.COMPANY_ID.eq(company), department.ID.eq(current.DEPARTMENT_ID))
            .where(E.COMPANY_ID.eq(company), E.ID.`in`(ids))
            .orderBy(E.ID)
            .fetch { row ->
                DirectoryCandidateRow(
                    requireNotNull(row[E.ID]),
                    requireNotNull(row[E.EMPLOYEE_NUMBER]),
                    requireNotNull(row[P.LEGAL_NAME]),
                    row[position.ID],
                    row[position.NAME],
                    row[department.ID],
                    row[department.NAME],
                    row[current.STATUS],
                    row[current.START_DATE],
                    row[current.END_DATE],
                    row[next],
                )
            }
    }

    override fun find(company: UUID, id: UUID): DirectoryEmployeeRecordsRecord? =
        sql.selectFrom(D).where(D.COMPANY_ID.eq(company), D.ID.eq(id)).fetchOne()

    override fun save(record: DirectoryEmployeeRecordsRecord) {
        val changed =
            if (record.version == 0L) sql.insertInto(D).set(record).execute()
            else
                sql.update(D)
                    .set(record)
                    .where(
                        D.COMPANY_ID.eq(record.companyId),
                        D.ID.eq(record.id),
                        D.VERSION.eq(requireNotNull(record.version) - 1),
                    )
                    .execute()
        check(changed == 1)
    }

    override fun schedule(company: UUID, id: UUID, next: Instant?) {
        check(
            sql.update(Q)
                .set(Q.REFRESH_AT, next?.atOffset(ZoneOffset.UTC))
                .where(Q.COMPANY_ID.eq(company), Q.EMPLOYMENT_ID.eq(id))
                .execute() == 1
        )
    }
}
