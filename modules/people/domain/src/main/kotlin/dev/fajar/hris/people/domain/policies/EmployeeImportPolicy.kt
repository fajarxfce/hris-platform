package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.requireRecentAuthentication
import dev.fajar.hris.identity.domain.policies.requireRecentMfa
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant
import java.time.LocalDate

val employeeImportPermissions =
    setOf("people.import", "people.manage", "people.profile.read", "people.profile.manage")

fun requireEmployeeImportAccess(actor: Actor): Result<Unit> =
    if (actor.companyId != null && actor.permissions.containsAll(employeeImportPermissions))
        Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.FORBIDDEN, "employee_import_access_required"))

fun validateEmployeeDraft(
    draft: EmployeeDraft,
    today: LocalDate,
    reason: String,
): Map<String, String> = buildMap {
    val number = validateEmployeeNumber(draft.employeeNumber)
    if (number is Result.Failed) put("employeeNumber", number.failure.code)
    val profile = validatePerson(draft.person, today)
    if (profile is Result.Failed) putAll(profile.failure.fields)
    if (
        draft.terms.startDate.year !in 1900..2200 ||
            (draft.terms.endDate != null && draft.terms.endDate.year !in 1900..2200)
    )
        put("terms", "employment_date_outside_import_range")
    val employment = validateEmployment(draft.terms, reason)
    if (employment is Result.Failed) put("terms", employment.failure.code)
    if (draft.terms.effectiveFrom != draft.terms.startDate)
        put("effectiveFrom", "initial_effective_date_must_match_start")
}

fun parseEmployeeImportCursor(values: Map<String, String>): Result<Int> {
    val raw = values["afterRow"] ?: return Result.Success(0)
    val value = raw.toIntOrNull()
    return if (value == null || value !in 0..5000)
        Result.Failed(Failure(FailureKind.CONFLICT, "invalid_import_checkpoint"))
    else Result.Success(value)
}

fun annotateEmployeeImportDuplicates(rows: List<EmployeeImportRow>): List<EmployeeImportRow> {
    val counts = rows.groupingBy { it.employeeNumber }.eachCount()
    return rows.map { row ->
        if (row.employeeNumber.isNotBlank() && counts.getValue(row.employeeNumber) > 1)
            row.copy(
                issues =
                    row.issues + mapOf("employeeNumber" to "duplicate_employee_number_in_import")
            )
        else row
    }
}

fun validateEmployeeImportActor(actor: Actor, access: AccountAccess?): Result<Unit> {
    if (
        access == null ||
            !access.account.active ||
            access.account.id != actor.accountId ||
            (actor.credentialVersion != null &&
                actor.credentialVersion != access.account.securityVersion)
    )
        return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
    if (
        !access.membershipActive ||
            !access.companyActive ||
            !access.permissions.containsAll(employeeImportPermissions)
    )
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "employee_import_access_required"))
    return Result.Success(Unit)
}

fun requireEmployeeImportAssurance(
    actor: Actor,
    security: IdentitySecurityPolicy,
    now: Instant,
): Result<Unit> =
    if (security.enforceMfa) requireRecentMfa(actor, now, security.recentAuthenticationAge)
    else requireRecentAuthentication(actor, now, security.recentAuthenticationAge)

/** Interactive reads also recheck MFA after waiting; workers retain their execution policy. */
fun validateEmployeeImportSessionActor(
    actor: Actor,
    access: AccountAccess?,
    now: Instant,
    security: IdentitySecurityPolicy,
): Result<Unit> =
    validateEmployeeImportActor(actor, access).flatMap {
        validateCompanySessionActor(actor, access, now, security).map { Unit }
    }
