package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.OffboardingSchedulesRecord
import java.time.OffsetDateTime
import java.util.UUID

interface OffboardingScheduleDataSource {
    fun forCase(companyId: UUID, caseId: UUID, limit: Int): List<OffboardingScheduleRow>

    fun find(companyId: UUID, id: UUID): OffboardingScheduleRow?

    fun forJob(companyId: UUID, jobId: UUID): OffboardingSchedulesRecord?

    fun insert(record: OffboardingSchedulesRecord)

    fun finish(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: String,
        finishedAt: OffsetDateTime,
        failureCode: String?,
    ): Long?
}
