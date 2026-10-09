package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollRunAttemptResponse(
    val jobId: UUID,
    val number: Int,
    val baseCompleted: Int,
    val startedAt: Instant,
    val reason: String,
)
