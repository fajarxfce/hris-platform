package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.util.UUID

data class LeaveBatchResultResponse(
    val ordinal: Int,
    val employeeId: UUID,
    val jobId: UUID,
    val status: String,
    val resourceId: UUID?,
    val failureCode: String?,
    val parameters: Map<String, String>,
    val completedAt: Instant,
)
