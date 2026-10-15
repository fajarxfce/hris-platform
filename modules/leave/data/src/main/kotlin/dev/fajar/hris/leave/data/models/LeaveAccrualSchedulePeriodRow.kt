package dev.fajar.hris.leave.data.models

import dev.fajar.hris.schema.tables.records.LeaveBatchesRecord
import java.time.OffsetDateTime

data class LeaveAccrualSchedulePeriodRow(
    val scheduledFor: OffsetDateTime,
    val batch: LeaveBatchesRecord,
    val jobStatus: String,
    val completedEmployees: Int,
)
