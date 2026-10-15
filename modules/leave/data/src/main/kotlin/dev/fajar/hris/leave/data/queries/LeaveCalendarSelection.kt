package dev.fajar.hris.leave.data.queries

import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import dev.fajar.hris.schema.tables.LeaveRequests.LEAVE_REQUESTS as R
import java.time.LocalDate
import java.util.UUID
import org.jooq.Condition
import org.jooq.impl.DSL

/** A raw reporting-line filter; the use case independently authorizes its selection. */
fun leaveCalendarTeamCondition(company: UUID, manager: UUID?, accessDate: LocalDate): Condition {
    if (manager == null) return DSL.noCondition()
    val current = EMPLOYEES_AT.call(company, accessDate)
    return R.EMPLOYMENT_ID.`in`(
        DSL.select(current.ID)
            .from(current)
            .where(current.MANAGER_ACCOUNT_ID.eq(manager), current.STATUS.ne("ENDED"))
    )
}
