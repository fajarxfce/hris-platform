package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.*
import java.util.UUID

data class PayrollPayslipSummary(
    val id: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: YearMonth,
    val plannedPaymentDate: LocalDate,
    val taxableGross: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
    val publishedAt: Instant,
) {
    val currency: String
        get() = "IDR"

    val version: Long
        get() = 0
}
