package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollPayableResponse(
    val assessmentId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: YearMonth,
    val plannedPaymentDate: LocalDate,
    val amount: String,
    val currency: String,
    val publishedAt: Instant,
)
