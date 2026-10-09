package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID

data class PayrollRunAttempt(
    val jobId: UUID,
    val number: Int,
    val baseCompleted: Int,
    val startedAt: Instant,
    val reason: String,
)
