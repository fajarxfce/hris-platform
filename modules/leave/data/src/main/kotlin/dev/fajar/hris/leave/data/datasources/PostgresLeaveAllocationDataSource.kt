package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveOccupancyRow
import dev.fajar.hris.schema.tables.LeaveAllocations.LEAVE_ALLOCATIONS as A
import dev.fajar.hris.schema.tables.records.LeaveAllocationsRecord
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveAllocationDataSource(private val sql: DSLContext) : LeaveAllocationDataSource {
    override fun occupancy(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<LeaveOccupancyRow> =
        sql.select(A.WORK_DATE, DSL.sum(A.SLOT))
            .from(A)
            .where(A.COMPANY_ID.eq(company))
            .and(A.EMPLOYMENT_ID.eq(employee))
            .and(A.WORK_DATE.between(from, until))
            .groupBy(A.WORK_DATE)
            .fetch { LeaveOccupancyRow(it.value1(), it.value2().intValueExact()) }

    override fun insert(rows: List<LeaveAllocationsRecord>) {
        sql.batchInsert(rows).execute()
    }

    override fun remove(company: UUID, id: UUID): Int =
        sql.deleteFrom(A).where(A.COMPANY_ID.eq(company)).and(A.REQUEST_ID.eq(id)).execute()
}
