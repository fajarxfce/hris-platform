package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** An immutable employee/month attendance revision; Payroll never changes this evidence. */
data class PayrollWorkAdjustment(
    val id: UUID,
    val revision: Long,
    val workDate: LocalDate,
    val recordedAt: Instant,
    val reason: String,
)
