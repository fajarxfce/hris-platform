package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.PayrollPayslipCursor
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

fun canReadPayrollPayslips(actor: Actor): Boolean =
    "payroll.read" in actor.permissions || "payroll.self.read" in actor.permissions

fun decodePayrollPayslipCursor(value: String?): Result<PayrollPayslipCursor?> {
    if (value == null) return Result.Success(null)
    if (
        !Regex(
                "^[0-9]{4}-(0[1-9]|1[0-2]):[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
            )
            .matches(value)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
    return Result.Success(
        PayrollPayslipCursor(
            YearMonth.parse(value.substringBefore(':')),
            UUID.fromString(value.substringAfter(':')),
        )
    )
}

fun validatePayrollPayslipRange(
    from: YearMonth,
    until: YearMonth,
    after: PayrollPayslipCursor?,
): Result<Unit> =
    if (
        from.year !in 2024..2100 ||
            until.year !in 2024..2100 ||
            from > until ||
            ChronoUnit.MONTHS.between(from, until) >= 36
    )
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_payroll_payslip_range",
                parameters = mapOf("maximumMonths" to "36"),
            )
        )
    else if (after != null && after.month !in from..until)
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
    else Result.Success(Unit)
