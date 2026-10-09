package dev.fajar.hris.payroll.data.models

import java.math.BigDecimal
import java.time.*
import java.util.UUID

data class PayrollPayslipSummaryRow(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: LocalDate,
    val plannedPaymentDate: LocalDate,
    val taxableGross: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
    val publishedAt: OffsetDateTime,
)
