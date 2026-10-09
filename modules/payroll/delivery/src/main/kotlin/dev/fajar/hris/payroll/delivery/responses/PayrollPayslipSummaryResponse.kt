package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollPayslipSummaryResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: YearMonth,
    val plannedPaymentDate: LocalDate,
    val currency: String,
    val taxableGross: String,
    val withheld: String,
    val takeHome: String,
    val publishedAt: Instant,
    val version: Long,
)
