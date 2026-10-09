package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollPeriodChange(
    val version: Long,
    val status: PayrollPeriodStatus,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
