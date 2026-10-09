package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PayrollPaymentBatch(
    val id: UUID,
    val status: PayrollPaymentBatchStatus,
    val version: Long,
    val title: String,
    val totalAmount: BigDecimal,
    val itemCount: Int,
    val createdBy: UUID,
    val createdAt: Instant,
    val releasedBy: UUID?,
    val releasedAt: Instant?,
    val items: List<PayrollPaymentItem>,
)
