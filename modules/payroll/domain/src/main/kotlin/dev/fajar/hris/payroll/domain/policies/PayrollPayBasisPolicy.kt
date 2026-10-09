package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*

fun validatePayrollPayBasis(basis: PayrollPayBasis): Result<Unit> {
    val fields = linkedMapOf<String, String>()
    if (basis.overtimeRuleId != INDONESIAN_OVERTIME_PP35_V1)
        fields["terms.payBasis.overtimeRuleId"] = "unsupported"
    if (basis.holidayAllowanceRuleId != INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1)
        fields["terms.payBasis.holidayAllowanceRuleId"] = "unsupported"
    if (!validPayrollAmount(basis.regularNonFixedWage))
        fields["terms.payBasis.regularNonFixedWage"] = "out_of_range"
    if ((basis.workWeek == PayrollWorkWeek.SIX_DAYS) != (basis.shortestWorkDay != null))
        fields["terms.payBasis.shortestWorkDay"] = "invalid_work_week"
    if (
        basis.reviewReference.isBlank() ||
            basis.reviewReference.length > 200 ||
            basis.reviewReference.any(Char::isISOControl)
    )
        fields["terms.payBasis.reviewReference"] = "invalid"
    if (
        basis.overtimeEligibility == OvertimeEligibility.EXEMPT &&
            basis.overtimeExemptionReference.isNullOrBlank()
    )
        fields["terms.payBasis.overtimeExemptionReference"] = "required"
    if (
        basis.overtimeExemptionReference != null &&
            (basis.overtimeExemptionReference.isBlank() ||
                basis.overtimeExemptionReference.length > 200 ||
                basis.overtimeExemptionReference.any(Char::isISOControl))
    )
        fields["terms.payBasis.overtimeExemptionReference"] = "invalid"
    if (
        basis.overtimeEligibility == OvertimeEligibility.ELIGIBLE &&
            basis.overtimeExemptionReference != null
    )
        fields["terms.payBasis.overtimeExemptionReference"] = "not_applicable"
    return if (fields.isEmpty()) Result.Success(Unit)
    else
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_pay_basis", fields = fields))
}
