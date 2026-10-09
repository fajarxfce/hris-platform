package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveBatchResult(
    val ordinal: Int,
    val employeeId: UUID,
    val jobId: UUID,
    val status: LeaveBatchResultStatus,
    val resourceId: UUID?,
    val failureCode: String?,
    val parameters: Map<String, String>,
    val completedAt: Instant,
)
