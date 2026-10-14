package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.*
import dev.fajar.hris.people.data.queries.nextEmploymentRevisionDate
import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import dev.fajar.hris.schema.tables.Employments.EMPLOYMENTS as E
import dev.fajar.hris.schema.tables.OrganizationUnits.ORGANIZATION_UNITS as U
import dev.fajar.hris.schema.tables.Persons.PERSONS as P
import dev.fajar.hris.schema.tables.SelfProfileRecords.SELF_PROFILE_RECORDS as R
import dev.fajar.hris.schema.tables.SelfProfileRefreshQueue.SELF_PROFILE_REFRESH_QUEUE as Q
import dev.fajar.hris.schema.tables.records.SelfProfileRecordsRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresSelfProfileProjectionDataSource(private val sql: DSLContext) :
    SelfProfileProjectionDataSource {
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
    ): List<SelfProfileCandidateRow> {
        val employee = EMPLOYEES_AT.call(company, today).`as`("profile_employee")
        val manager = EMPLOYEES_AT.call(company, today).`as`("profile_manager")
        val branch = U.`as`("profile_branch")
        val department = U.`as`("profile_department")
        val position = U.`as`("profile_position")
        val costCenter = U.`as`("profile_cost_center")
        val next = nextEmploymentRevisionDate(E.COMPANY_ID, E.ID, today).`as`("employee_next")
        val managerNext =
            nextEmploymentRevisionDate(E.COMPANY_ID, employee.MANAGER_ID, today)
                .`as`("manager_next")
        return sql.select(
                E.ID,
                employee.asterisk(),
                P.OWNER_COMPANY_ID,
                P.VERSION,
                branch.asterisk(),
                department.asterisk(),
                position.asterisk(),
                costCenter.asterisk(),
                manager.ID,
                manager.EMPLOYEE_NUMBER,
                manager.LEGAL_NAME,
                manager.STATUS,
                manager.START_DATE,
                manager.END_DATE,
                next,
                managerNext,
            )
            .from(E)
            .join(P)
            .on(P.ID.eq(E.PERSON_ID))
            .leftJoin(employee)
            .on(employee.ID.eq(E.ID), employee.ACCOUNT_ID.isNotNull)
            .leftJoin(manager)
            .on(manager.ID.eq(employee.MANAGER_ID))
            .leftJoin(branch)
            .on(branch.COMPANY_ID.eq(company), branch.ID.eq(employee.BRANCH_ID))
            .leftJoin(department)
            .on(department.COMPANY_ID.eq(company), department.ID.eq(employee.DEPARTMENT_ID))
            .leftJoin(position)
            .on(position.COMPANY_ID.eq(company), position.ID.eq(employee.POSITION_ID))
            .leftJoin(costCenter)
            .on(costCenter.COMPANY_ID.eq(company), costCenter.ID.eq(employee.COST_CENTER_ID))
            .where(E.COMPANY_ID.eq(company), E.ID.`in`(ids))
            .orderBy(E.ID)
            .fetch { row ->
                SelfProfileCandidateRow(
                    requireNotNull(row[E.ID]),
                    row[employee.ID]?.let { row.into(employee) },
                    requireNotNull(row[P.OWNER_COMPANY_ID]),
                    requireNotNull(row[P.VERSION]),
                    row[branch.ID]?.let { row.into(branch) },
                    row[department.ID]?.let { row.into(department) },
                    row[position.ID]?.let { row.into(position) },
                    row[costCenter.ID]?.let { row.into(costCenter) },
                    row[manager.ID]?.let {
                        SelfProfileManagerRow(
                            it,
                            requireNotNull(row[manager.EMPLOYEE_NUMBER]),
                            requireNotNull(row[manager.LEGAL_NAME]),
                            requireNotNull(row[manager.STATUS]),
                            requireNotNull(row[manager.START_DATE]),
                            row[manager.END_DATE],
                        )
                    },
                    row[next],
                    row[managerNext],
                )
            }
    }

    override fun find(company: UUID, id: UUID): SelfProfileRecordsRecord? =
        sql.selectFrom(R).where(R.COMPANY_ID.eq(company), R.ID.eq(id)).fetchOne()

    override fun save(record: SelfProfileRecordsRecord) {
        val changed =
            if (record.version == 0L) sql.insertInto(R).set(record).execute()
            else
                sql.update(R)
                    .set(record)
                    .where(
                        R.COMPANY_ID.eq(record.companyId),
                        R.ID.eq(record.id),
                        R.VERSION.eq(requireNotNull(record.version) - 1),
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
