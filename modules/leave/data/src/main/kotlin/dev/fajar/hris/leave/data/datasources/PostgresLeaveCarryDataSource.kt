package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveCarryAllocationRow
import dev.fajar.hris.schema.tables.LeaveCarryAllocations.LEAVE_CARRY_ALLOCATIONS as A
import dev.fajar.hris.schema.tables.LeaveCarryBuckets.LEAVE_CARRY_BUCKETS as B
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext

class PostgresLeaveCarryDataSource(private val sql: DSLContext) : LeaveCarryDataSource {
    override fun buckets(
        company: UUID,
        employee: UUID,
        type: UUID,
        years: Set<Int>,
    ): List<LeaveCarryBucketsRecord> =
        sql.selectFrom(B)
            .where(B.COMPANY_ID.eq(company))
            .and(B.EMPLOYMENT_ID.eq(employee))
            .and(B.TYPE_ID.eq(type))
            .and(B.BALANCE_YEAR.`in`(years))
            .orderBy(B.BALANCE_YEAR)
            .limit(2)
            .fetch()

    override fun allocations(company: UUID, request: UUID): List<LeaveCarryAllocationRow> =
        sql.select(A.fields().toList() + B.BALANCE_YEAR)
            .from(A)
            .join(B)
            .on(B.COMPANY_ID.eq(A.COMPANY_ID).and(B.ID.eq(A.BUCKET_ID)))
            .where(A.COMPANY_ID.eq(company))
            .and(A.REQUEST_ID.eq(request))
            .orderBy(B.BALANCE_YEAR)
            .limit(2)
            .fetch { LeaveCarryAllocationRow(it.into(A), it[B.BALANCE_YEAR]) }

    override fun create(row: LeaveCarryBucketsRecord) {
        sql.executeInsert(row)
    }

    override fun allocate(rows: List<LeaveCarryAllocationsRecord>) {
        sql.batchInsert(rows).execute()
    }

    override fun append(rows: List<LeaveCarryMovementsRecord>) {
        sql.batchInsert(rows).execute()
    }
}
