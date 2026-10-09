package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.*
import java.time.LocalDate

data class PayrollHolidayInputRequest(
    val kind: PayrollHolidayKind,
    val holidayDate: LocalDate,
    val continuousServiceFrom: LocalDate,
    val priorPayment: PayrollPriorHolidayPayment,
    val reviewReference: String,
    val promisedAmount: String? = null,
)
