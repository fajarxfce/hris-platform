package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import java.time.LocalDate
import java.util.UUID

fun validateHeadcountAccess(actor: Actor): Result<Unit> =
    actor.requirePermission("reports.read").flatMap { actor.requirePermission("people.read") }

fun validateHeadcountDate(asOf: LocalDate): Result<Unit> =
    if (asOf.year in 1900..2100) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_report_date"))

fun validateHeadcountCompanies(companies: List<UUID>): Result<List<UUID>> {
    if (companies.size !in 1..32 || companies.toSet().size != companies.size)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_report_companies"))
    return Result.Success(companies.sorted())
}
