package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.LocalDate

data class PayrollHolidayInput(
    val kind: PayrollHolidayKind,
    val holidayDate: LocalDate,
    val continuousServiceFrom: LocalDate,
    val priorPayment: PayrollPriorHolidayPayment,
    val reviewReference: String,
    val promisedAmount: BigDecimal?,
)
