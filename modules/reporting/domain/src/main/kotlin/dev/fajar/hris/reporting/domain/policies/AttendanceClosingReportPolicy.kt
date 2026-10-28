package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun validateAttendanceClosingReportRange(from: YearMonth, until: YearMonth): Result<Unit> =
    if (
        from.year in 2000..2100 &&
            until.year in 2000..2100 &&
            ChronoUnit.MONTHS.between(from, until) in 0..11
    )
        Result.Success(Unit)
    else
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_report_month_range",
                parameters =
                    mapOf("maximumMonths" to "12", "minimumYear" to "2000", "maximumYear" to "2100"),
            )
        )
