package dev.fajar.hris.payroll.domain.entities

import java.time.*
import java.util.UUID

data class PayrollPeriod(
    val id: UUID,
    val earningsMonth: YearMonth,
    val plannedPaymentDate: LocalDate,
    val timezone: String,
    val participantCount: Int,
    val authorId: UUID,
    val createdAt: Instant,
    val status: PayrollPeriodStatus,
    val version: Long,
) {
    val plannedPaymentMonth: YearMonth
        get() = YearMonth.from(plannedPaymentDate)
}
