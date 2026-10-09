package dev.fajar.hris.payroll.delivery.responses

data class PayrollPayBasisResponse(
    val overtimeRuleId: String,
    val holidayAllowanceRuleId: String,
    val proration: String,
    val workWeek: String,
    val shortestWorkDay: String?,
    val overtimeEligibility: String,
    val overtimeExemptionReference: String?,
    val regularNonFixedWage: String,
    val serviceMonthConvention: String,
    val rounding: String,
    val reviewReference: String,
)
