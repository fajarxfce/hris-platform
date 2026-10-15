package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PayrollPaymentRecovery(
    val id: UUID,
    val assessmentId: UUID,
    val assessmentRevision: Long,
    val paymentVersion: Long,
    val amount: BigDecimal,
    val transactionReference: String,
    val occurredAt: Instant,
    val recordedAt: Instant,
    val actorId: UUID,
    val reason: String,
)
