package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollPayUnitsResponse(
    val total: String,
    val payable: String,
    val unpaid: String,
    val employedDates: Set<LocalDate>,
)
