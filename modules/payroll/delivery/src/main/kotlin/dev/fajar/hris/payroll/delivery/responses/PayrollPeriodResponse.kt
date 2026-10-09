package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollPeriodResponse(
    val id: UUID,
    val earningsMonth: String,
    val plannedPaymentMonth: String,
    val plannedPaymentDate: LocalDate,
    val timezone: String,
    val participantCount: Int,
    val authorId: UUID,
    val createdAt: Instant,
    val status: String,
    val version: Long,
)
