package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.*
import java.time.LocalDate

data class PayrollDayResolutionRequest(
    val workDate: LocalDate,
    val portion: PayrollDayPortion,
    val disposition: PayrollDayDisposition,
    val reference: String,
)
