package dev.fajar.hris.workforce.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

interface WorkPeriodRepository {
    fun lockMonth(companyId: UUID, month: YearMonth, exclusive: Boolean): Result<WorkPeriod>

    fun lockRange(companyId: UUID, from: YearMonth): Result<List<WorkPeriod>>

    fun find(companyId: UUID, month: YearMonth): Result<WorkPeriod?>

    fun forJob(companyId: UUID, jobId: UUID, exclusive: Boolean): Result<WorkPeriod?>

    fun list(companyId: UUID, from: YearMonth, until: YearMonth): Result<List<WorkPeriod>>

    fun start(
        companyId: UUID,
        period: WorkPeriod,
        jobId: UUID,
        timezone: String,
        at: Instant,
        employeeIds: List<UUID>,
    ): Result<WorkPeriod>

    fun nextTarget(companyId: UUID, jobId: UUID, after: UUID?): Result<UUID?>

    fun snapshot(companyId: UUID, jobId: UUID, employeeId: UUID): Result<WorkPeriodSnapshot?>

    fun saveSnapshot(companyId: UUID, jobId: UUID, snapshot: WorkPeriodSnapshot): Result<Unit>

    fun snapshotCount(companyId: UUID, jobId: UUID): Result<Int>

    fun finish(companyId: UUID, period: WorkPeriod, at: Instant): Result<WorkPeriod>

    fun requireReview(companyId: UUID, period: WorkPeriod, failureCode: String): Result<WorkPeriod>
}
