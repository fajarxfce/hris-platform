package dev.fajar.hris.payroll.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class PayrollPaymentProgressRow(
    val batchId: UUID,
    val itemId: UUID,
    val status: String,
    val createdAt: OffsetDateTime,
    val releasedAt: OffsetDateTime?,
    val resolvedAt: OffsetDateTime?,
)
