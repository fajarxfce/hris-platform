package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.*
import java.util.UUID

data class PayrollPayable(
    val assessmentId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: YearMonth,
    val plannedPaymentDate: LocalDate,
    val amount: BigDecimal,
    val publishedAt: Instant,
    val assessmentRevision: Long = 0,
    val paymentVersion: Long = 0,
    val confirmedPaid: BigDecimal = BigDecimal.ZERO,
    val confirmedRecovered: BigDecimal = BigDecimal.ZERO,
)
