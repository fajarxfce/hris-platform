package dev.fajar.hris.payroll.data.models

import java.time.*

data class PayrollPayUnitsData(
    val total: String,
    val payable: String,
    val unpaid: String,
    val employedDates: Set<LocalDate>,
)
