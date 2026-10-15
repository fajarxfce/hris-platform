package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveCarryAllocationRow
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface LeaveCarryDataSource {
    fun buckets(
        company: UUID,
        employee: UUID,
        type: UUID,
        years: Set<Int>,
    ): List<LeaveCarryBucketsRecord>

    fun allocations(company: UUID, request: UUID): List<LeaveCarryAllocationRow>

    fun create(row: LeaveCarryBucketsRecord)

    fun allocate(rows: List<LeaveCarryAllocationsRecord>)

    fun append(rows: List<LeaveCarryMovementsRecord>)
}
