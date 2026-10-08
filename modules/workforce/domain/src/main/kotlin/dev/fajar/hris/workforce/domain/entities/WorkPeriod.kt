package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class WorkPeriod(
    val id: UUID,
    val month: YearMonth,
    val status: WorkPeriodStatus,
    val timezone: String?,
    val jobId: UUID?,
    val version: Long,
    val startedAt: Instant?,
    val closedAt: Instant?,
    val failureCode: String?,
)
