package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveAccrualSchedulePeriodRow
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface LeaveAccrualScheduleDataSource {
    fun insert(
        schedule: LeaveAccrualSchedulesRecord,
        entries: List<LeaveAccrualScheduleEntriesRecord>,
    )

    fun find(company: UUID, id: UUID): LeaveAccrualSchedulesRecord?

    fun periods(company: UUID, id: UUID): List<LeaveAccrualSchedulePeriodRow>

    fun list(company: UUID, after: UUID?, limit: Int): List<LeaveAccrualSchedulesRecord>
}
