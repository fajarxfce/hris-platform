package dev.fajar.hris.workforce.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.data.datasources.AttendanceAdjustmentDataSource
import dev.fajar.hris.workforce.data.mappers.*
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.AttendanceAdjustmentRepository
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredAttendanceAdjustmentRepository(
    private val source: AttendanceAdjustmentDataSource,
    private val json: ObjectMapper,
) : AttendanceAdjustmentRepository {
    override fun latest(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
    ): Result<AttendanceAdjustment?> = safeDatabaseCall {
        source.latest(companyId, jobId, employeeId)?.toAdjustment(json)
    }

    override fun find(companyId: UUID, id: UUID): Result<AttendanceAdjustment?> = safeDatabaseCall {
        source.find(companyId, id)?.toAdjustment(json)
    }

    override fun history(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
        before: Long?,
        limit: Int,
    ): Result<Page<AttendanceAdjustmentSummary>> = safeDatabaseCall {
        val rows = source.history(companyId, jobId, employeeId, before, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun append(
        companyId: UUID,
        adjustment: AttendanceAdjustment,
    ): Result<MutationReceipt> = safeDatabaseCall {
        source.append(adjustment.toRow(companyId, json))
        MutationReceipt(adjustment.summary.id, adjustment.summary.revision)
    }
}
