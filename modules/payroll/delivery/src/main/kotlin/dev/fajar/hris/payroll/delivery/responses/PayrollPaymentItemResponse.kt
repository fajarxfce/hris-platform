package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollPaymentItemResponse(
    val id: UUID,
    val assessmentId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: YearMonth,
    val plannedPaymentDate: LocalDate,
    val amount: String,
    val currency: String,
    val destination: PayrollPaymentDestinationResponse,
    val status: String,
    val version: Int,
    val batchVersion: Long,
    val transactionReference: String?,
    val occurredAt: Instant?,
)
