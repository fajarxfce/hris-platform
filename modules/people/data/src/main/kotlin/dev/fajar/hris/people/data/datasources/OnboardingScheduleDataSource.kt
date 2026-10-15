package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.OnboardingSchedulesRecord
import java.time.OffsetDateTime
import java.util.UUID

interface OnboardingScheduleDataSource {
    fun forEmployee(companyId: UUID, employmentId: UUID, limit: Int): List<OnboardingScheduleRow>

    fun find(companyId: UUID, id: UUID): OnboardingScheduleRow?

    fun forJob(companyId: UUID, jobId: UUID): OnboardingScheduleRow?

    fun insert(record: OnboardingSchedulesRecord)

    fun finish(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: String,
        finishedAt: OffsetDateTime,
        failureCode: String?,
    ): Long?
}
