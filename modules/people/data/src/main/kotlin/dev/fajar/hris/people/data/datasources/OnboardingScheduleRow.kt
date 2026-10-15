package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.LifecycleTemplatesRecord
import dev.fajar.hris.schema.tables.records.OnboardingSchedulesRecord
import java.time.OffsetDateTime

data class OnboardingScheduleRow(
    val schedule: OnboardingSchedulesRecord,
    val template: LifecycleTemplatesRecord,
    val jobStatus: String,
    val jobVersion: Long,
    val attempts: Int,
    val cancellationRequested: Boolean,
    val completedItems: Int,
    val jobFailureCode: String?,
    val jobFinishedAt: OffsetDateTime?,
)
