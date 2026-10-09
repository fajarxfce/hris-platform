package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveRequestSummaryRow
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface LeaveRequestDataSource {
    fun unresolved(
        company: UUID,
        employee: UUID,
        type: UUID,
        from: java.time.LocalDate,
        until: java.time.LocalDate,
        statuses: Set<String>,
    ): Boolean

    fun find(company: UUID, id: UUID): LeaveRequestsRecord?

    fun list(
        company: UUID,
        employee: UUID?,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<LeaveRequestSummaryRow>

    fun history(company: UUID, id: UUID, after: Long?, limit: Int): List<LeaveRequestChangesRecord>

    fun insert(row: LeaveRequestsRecord)

    fun update(
        company: UUID,
        id: UUID,
        expectedVersion: Long,
        status: String,
        cancellationId: UUID?,
    ): Long?

    fun append(row: LeaveRequestChangesRecord)
}
