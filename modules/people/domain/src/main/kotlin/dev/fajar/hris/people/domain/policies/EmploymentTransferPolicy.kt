package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.people.domain.entities.*
import java.time.LocalDate

fun validateTransferActor(
    actor: Actor,
    source: AccountAccess?,
    target: AccountAccess?,
): Result<Unit> {
    if (
        source == null ||
            target == null ||
            !source.account.active ||
            source.account.id != actor.accountId ||
            target.account.id != actor.accountId ||
            (actor.credentialVersion != null &&
                actor.credentialVersion != source.account.securityVersion)
    )
        return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
    if (
        !source.membershipActive ||
            !target.membershipActive ||
            !source.companyActive ||
            !target.companyActive ||
            !source.permissions.containsAll(setOf("people.manage", "people.transfer")) ||
            !target.permissions.containsAll(setOf("people.manage", "people.transfer"))
    )
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "transfer_access_required"))
    return Result.Success(Unit)
}

fun validateImmediateTransfer(
    source: Employee,
    command: EmploymentTransferCommand,
    sourceToday: LocalDate,
    targetToday: LocalDate,
): Result<Unit> {
    if (
        sourceToday != targetToday ||
            command.terms.startDate != sourceToday ||
            command.terms.effectiveFrom != sourceToday
    )
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "transfer_requires_current_company_date")
        )
    if (!source.terms.isWorkingOn(sourceToday) || !source.terms.startDate.isBefore(sourceToday))
        return Result.Failed(Failure(FailureKind.CONFLICT, "source_employment_not_transferable"))
    if (
        command.terms.status !in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION) ||
            command.terms.managerId == command.targetEmploymentId
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_transfer_terms"))
    return Result.Success(Unit)
}
