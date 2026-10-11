package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.LocalDate

fun validateTransferAccess(actor: Actor): Result<Unit> {
    val company =
        actor.companyId ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
    if (!actor.permissions.containsAll(setOf("people.manage", "people.transfer")))
        return Result.Failed(
            Failure(
                FailureKind.FORBIDDEN,
                "transfer_access_required",
                parameters = mapOf("companyId" to company.toString()),
            )
        )
    return Result.Success(Unit)
}

fun validateImmediateTransfer(
    source: Employee,
    command: EmploymentTransferCommand,
    sourceToday: LocalDate,
    targetToday: LocalDate,
): Result<Unit> {
    val eligible = validateTransferSource(source, sourceToday, targetToday)
    if (eligible is Result.Failed) return eligible
    if (command.terms.startDate != sourceToday || command.terms.effectiveFrom != sourceToday)
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "transfer_requires_current_company_date")
        )
    if (
        command.terms.status !in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION) ||
            command.terms.managerId == command.targetEmploymentId
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_transfer_terms"))
    return Result.Success(Unit)
}

fun validateTransferSource(
    source: Employee,
    sourceToday: LocalDate,
    targetToday: LocalDate,
): Result<Unit> {
    if (sourceToday != targetToday)
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "transfer_requires_current_company_date")
        )
    if (!source.terms.isWorkingOn(sourceToday) || !source.terms.startDate.isBefore(sourceToday))
        return Result.Failed(Failure(FailureKind.CONFLICT, "source_employment_not_transferable"))
    return Result.Success(Unit)
}
