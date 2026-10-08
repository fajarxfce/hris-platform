package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.util.UUID

data class WorkPeriodResponse(
    val id: UUID,
    val month: String,
    val status: String,
    val timezone: String?,
    val jobId: UUID?,
    val version: Long,
    val startedAt: Instant?,
    val closedAt: Instant?,
    val failureCode: String?,
)
