package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun validateAttendanceReportAccess(actor: Actor): Result<Unit> =
    actor.requirePermission("reports.read").flatMap { actor.requirePermission("workforce.read") }

fun validateAttendanceReportRange(from: LocalDate, until: LocalDate): Result<Unit> =
    if (
        from.year in 1900..2100 &&
            until.year in 1900..2100 &&
            ChronoUnit.DAYS.between(from, until) in 0..30
    )
        Result.Success(Unit)
    else
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_report_range",
                parameters = mapOf("maximumDays" to "31"),
            )
        )
