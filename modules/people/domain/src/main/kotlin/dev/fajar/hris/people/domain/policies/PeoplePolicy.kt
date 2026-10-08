package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.LocalDate
import java.util.Locale

fun validatePerson(person: PersonProfile, today: LocalDate): Result<Unit> {
    val fields = buildMap {
        if (person.legalName.isBlank() || person.legalName.length > 200)
            put("legalName", "invalid_name")
        if (person.nationality !in Locale.getISOCountries()) put("nationality", "invalid_country")
        if (person.birthDate?.isAfter(today) == true) put("birthDate", "invalid_birth_date")
        if (person.email != null && (person.email.length > 254 || !person.email.contains("@")))
            put("email", "invalid_email")
    }
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_person", fields))
}

fun validateEmployment(terms: EmploymentTerms, reason: String): Result<Unit> {
    if (reason.isBlank() || reason.length > 1000)
        return Result.Failed(Failure(FailureKind.VALIDATION, "reason_required"))
    if (
        terms.effectiveFrom.isBefore(terms.startDate) ||
            terms.endDate?.isBefore(terms.startDate) == true
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employment_dates"))
    if (
        terms.contract == ContractKind.FIXED_TERM &&
            (terms.endDate == null || terms.status == EmploymentStatus.PROBATION)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_fixed_term_contract"))
    if (terms.status == EmploymentStatus.ENDED && terms.endDate == null)
        return Result.Failed(Failure(FailureKind.VALIDATION, "end_date_required"))
    return Result.Success(Unit)
}

fun canReadEmployee(actor: Actor, employee: Employee, current: Employee?): Boolean =
    "people.read" in actor.permissions ||
        ("people.self.read" in actor.permissions && employee.person.accountId == actor.accountId) ||
        ("people.team.read" in actor.permissions &&
            current?.managerAccountId == actor.accountId &&
            current.terms.status != EmploymentStatus.ENDED)
