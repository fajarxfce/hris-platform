package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.OffboardingSchedulesRecord
import java.time.OffsetDateTime

data class OffboardingScheduleRow(
    val schedule: OffboardingSchedulesRecord,
    val jobStatus: String,
    val jobVersion: Long,
    val attempts: Int,
    val cancellationRequested: Boolean,
    val completedItems: Int,
    val jobFailureCode: String?,
    val jobFinishedAt: OffsetDateTime?,
)
