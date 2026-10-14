package dev.fajar.hris.payroll.delivery.responses

data class PayrollCalculationPolicyResponse(
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: String,
    val effectiveUntil: String,
    val incomeTaxRuleId: String,
    val insuranceRuleId: String,
)
