package dev.fajar.hris.payroll.domain.entities

import java.time.LocalDate

data class PayrollDayResolution(
    val workDate: LocalDate,
    val portion: PayrollDayPortion,
    val disposition: PayrollDayDisposition,
    val reference: String,
)
