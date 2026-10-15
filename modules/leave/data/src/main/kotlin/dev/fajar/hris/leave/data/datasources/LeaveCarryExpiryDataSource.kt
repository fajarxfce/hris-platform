package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveCarryExpiryTargetRow
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface LeaveCarryExpiryDataSource {
    fun find(company: UUID, id: UUID, lock: Boolean): LeaveCarryExpiriesRecord?

    fun forJob(company: UUID, job: UUID, lock: Boolean): LeaveCarryExpiriesRecord?

    fun list(company: UUID, after: UUID?, limit: Int): List<LeaveCarryExpiriesRecord>

    fun insert(row: LeaveCarryExpiriesRecord)

    fun insertAttempt(row: LeaveCarryExpiryAttemptsRecord)

    fun next(company: UUID, id: UUID, after: Int): LeaveCarryExpiryTargetRow?

    fun completed(company: UUID, id: UUID): Int

    fun outcome(row: LeaveCarryExpiryOutcomesRecord)

    fun results(
        company: UUID,
        id: UUID,
        after: Int?,
        limit: Int,
    ): List<LeaveCarryExpiryOutcomesRecord>

    fun attempts(company: UUID, id: UUID): List<LeaveCarryExpiryAttemptsRecord>

    fun resume(company: UUID, id: UUID, version: Long, job: UUID): Boolean
}
