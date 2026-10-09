package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.math.RoundingMode

/** Units are approved payroll facts, not an inference from missing attendance. */
fun prorateMonthlyPay(
    monthlyAmount: BigDecimal,
    totalUnits: BigDecimal,
    payableUnits: BigDecimal,
    rounding: EarningsRounding,
): Result<ProratedPay> {
    if (
        !validPayrollAmount(monthlyAmount) ||
            totalUnits.scale() !in 0..3 ||
            payableUnits.scale() !in 0..3 ||
            totalUnits.signum() <= 0 ||
            totalUnits > BigDecimal(31) ||
            payableUnits.signum() < 0 ||
            payableUnits > totalUnits
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_proration"))
    val amount =
        monthlyAmount
            .multiply(payableUnits)
            .divide(totalUnits, 0, RoundingMode.valueOf(rounding.name))
    return Result.Success(ProratedPay(monthlyAmount, totalUnits, payableUnits, amount, rounding))
}
