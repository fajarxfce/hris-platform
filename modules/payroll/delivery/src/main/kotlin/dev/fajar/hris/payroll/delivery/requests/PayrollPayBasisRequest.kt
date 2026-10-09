package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.*
import java.time.DayOfWeek

data class PayrollPayBasisRequest(
    val overtimeRuleId: String,
    val holidayAllowanceRuleId: String,
    val proration: PayrollProrationBasis,
    val workWeek: PayrollWorkWeek,
    val shortestWorkDay: DayOfWeek? = null,
    val overtimeEligibility: OvertimeEligibility,
    val overtimeExemptionReference: String? = null,
    val regularNonFixedWage: String,
    val serviceMonthConvention: ServiceMonthConvention,
    val rounding: EarningsRounding,
    val reviewReference: String,
)
