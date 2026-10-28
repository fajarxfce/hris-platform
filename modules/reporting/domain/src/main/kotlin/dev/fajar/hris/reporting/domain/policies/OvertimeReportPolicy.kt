package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.policies.canReadCompanyOvertime
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun validateOvertimeReportAccess(actor: Actor): Result<Unit> =
    actor.requirePermission("reports.read").flatMap {
        if (canReadCompanyOvertime(actor)) Result.Success(Unit)
        else Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
    }

fun validateOvertimeReportRange(from: LocalDate, until: LocalDate): Result<Unit> =
    if (
        from.year in 2000..2100 &&
            until.year in 2000..2100 &&
            ChronoUnit.DAYS.between(from, until) in 0..365
    )
        Result.Success(Unit)
    else
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_report_range",
                parameters =
                    mapOf("maximumDays" to "366", "minimumYear" to "2000", "maximumYear" to "2100"),
            )
        )
