package dev.fajar.hris.payroll.data.models

data class PayrollPayBasisData(
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
