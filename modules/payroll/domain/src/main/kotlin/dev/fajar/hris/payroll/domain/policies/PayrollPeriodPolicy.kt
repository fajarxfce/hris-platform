package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID

fun validatePayrollPeriod(
    month: YearMonth,
    paymentDate: LocalDate,
    employees: Set<UUID>,
    reason: String,
): Result<Unit> {
    if (month.year !in 2024..2100)
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_payroll_period",
                fields = mapOf("earningsMonth" to "out_of_range"),
            )
        )
    val fields = linkedMapOf<String, String>()
    if (
        paymentDate.year !in 2024..2100 ||
            paymentDate < month.atDay(1) ||
            paymentDate > month.atEndOfMonth().plusDays(62)
    )
        fields["plannedPaymentDate"] = "out_of_range"
    if (employees.size !in 1..5000) fields["employeeIds"] = "out_of_range"
    if (reason.isBlank() || reason.length > 1000) fields["reason"] = "invalid"
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_period", fields = fields))
}

fun canReadPayrollInput(actor: Actor): Boolean =
    actor.permissions.any { it in setOf("payroll.read", "payroll.calculate", "payroll.review") }
