package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.policies.canReadCompanyPayroll
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun validatePayrollCostReportAccess(actor: Actor): Result<Unit> =
    actor.requirePermission("reports.read").flatMap {
        if (canReadCompanyPayroll(actor)) Result.Success(Unit)
        else Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
    }

fun validatePayrollCostReportRange(from: YearMonth, until: YearMonth): Result<Unit> =
    if (
        from.year in 2024..2100 &&
            until.year in 2024..2100 &&
            ChronoUnit.MONTHS.between(from, until) in 0..11
    )
        Result.Success(Unit)
    else
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_report_month_range",
                parameters =
                    mapOf("maximumMonths" to "12", "minimumYear" to "2024", "maximumYear" to "2100"),
            )
        )
