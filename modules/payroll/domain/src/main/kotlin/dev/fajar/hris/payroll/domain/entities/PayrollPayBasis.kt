package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.DayOfWeek

data class PayrollPayBasis(
    val overtimeRuleId: String,
    val holidayAllowanceRuleId: String,
    val proration: PayrollProrationBasis,
    val workWeek: PayrollWorkWeek,
    val shortestWorkDay: DayOfWeek?,
    val overtimeEligibility: OvertimeEligibility,
    val overtimeExemptionReference: String?,
    val regularNonFixedWage: BigDecimal,
    val serviceMonthConvention: ServiceMonthConvention,
    val rounding: EarningsRounding,
    val reviewReference: String,
)
