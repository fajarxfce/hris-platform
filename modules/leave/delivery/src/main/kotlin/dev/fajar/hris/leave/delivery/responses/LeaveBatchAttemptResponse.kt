package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.util.UUID

data class LeaveBatchAttemptResponse(
    val jobId: UUID,
    val number: Int,
    val baseCompleted: Int,
    val startedAt: Instant,
    val reason: String,
)
