package dev.fajar.hris.payroll.delivery.requests

import java.time.*
import java.util.UUID

data class PayrollPeriodRequest(
    val id: UUID,
    val earningsMonth: YearMonth,
    val plannedPaymentDate: LocalDate,
    val employeeIds: Set<UUID>,
    val reason: String,
)
