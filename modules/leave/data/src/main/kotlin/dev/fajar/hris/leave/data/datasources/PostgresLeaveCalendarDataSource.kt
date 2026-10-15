package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.*
import dev.fajar.hris.leave.data.queries.leaveCalendarTeamCondition
import dev.fajar.hris.schema.tables.LeaveAllocations.LEAVE_ALLOCATIONS as A
import dev.fajar.hris.schema.tables.LeaveRequests.LEAVE_REQUESTS as R
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveCalendarDataSource(private val sql: DSLContext) : LeaveCalendarDataSource {
    override fun month(
        company: UUID,
        manager: UUID?,
        accessDate: LocalDate,
        from: LocalDate,
        until: LocalDate,
    ): List<LeaveCalendarDayRow> =
        sql.select(
                A.WORK_DATE,
                DSL.countDistinct(A.EMPLOYMENT_ID),
                DSL.countDistinct(R.ID),
                DSL.countDistinct(R.ID).filterWhere(R.STATUS.eq("PENDING")),
                DSL.countDistinct(R.ID).filterWhere(R.STATUS.eq("APPROVED")),
                DSL.countDistinct(R.ID).filterWhere(R.STATUS.eq("CANCELLATION_PENDING")),
            )
            .from(A)
            .join(R)
            .on(R.COMPANY_ID.eq(A.COMPANY_ID), R.ID.eq(A.REQUEST_ID))
            .where(A.COMPANY_ID.eq(company), A.WORK_DATE.between(from, until))
            .and(R.STATUS.`in`("PENDING", "APPROVED", "CANCELLATION_PENDING"))
            .and(leaveCalendarTeamCondition(company, manager, accessDate))
            .groupBy(A.WORK_DATE)
            .orderBy(A.WORK_DATE)
            .limit(31)
            .fetch {
                LeaveCalendarDayRow(
                    requireNotNull(it.value1()),
                    requireNotNull(it.value2()),
                    requireNotNull(it.value3()),
                    requireNotNull(it.value4()),
                    requireNotNull(it.value5()),
                    requireNotNull(it.value6()),
                )
            }

    override fun entries(
        company: UUID,
        manager: UUID?,
        accessDate: LocalDate,
        date: LocalDate,
        after: UUID?,
        limit: Int,
    ): List<LeaveCalendarEntryRow> =
        sql.select(
                R.ID,
                R.EMPLOYMENT_ID,
                R.EMPLOYEE_NUMBER,
                R.EMPLOYEE_NAME,
                R.TYPE_CODE,
                R.TYPE_NAME,
                DSL.sum(A.SLOT),
                R.STATUS,
                R.VERSION,
            )
            .from(A)
            .join(R)
            .on(R.COMPANY_ID.eq(A.COMPANY_ID), R.ID.eq(A.REQUEST_ID))
            .where(A.COMPANY_ID.eq(company), A.WORK_DATE.eq(date))
            .and(R.STATUS.`in`("PENDING", "APPROVED", "CANCELLATION_PENDING"))
            .and(leaveCalendarTeamCondition(company, manager, accessDate))
            .and(after?.let { R.ID.gt(it) } ?: DSL.noCondition())
            .groupBy(
                R.ID,
                R.EMPLOYMENT_ID,
                R.EMPLOYEE_NUMBER,
                R.EMPLOYEE_NAME,
                R.TYPE_CODE,
                R.TYPE_NAME,
                R.STATUS,
                R.VERSION,
            )
            .orderBy(R.ID)
            .limit(limit)
            .fetch {
                LeaveCalendarEntryRow(
                    requireNotNull(it.value1()),
                    requireNotNull(it.value2()),
                    requireNotNull(it.value3()),
                    requireNotNull(it.value4()),
                    requireNotNull(it.value5()),
                    requireNotNull(it.value6()),
                    requireNotNull(it.value7()).intValueExact(),
                    requireNotNull(it.value8()),
                    requireNotNull(it.value9()),
                )
            }
}
