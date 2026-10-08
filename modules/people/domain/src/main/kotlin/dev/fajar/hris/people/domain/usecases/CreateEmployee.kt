package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.organization.domain.entities.UnitKind
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class CreateEmployee(
    private val people: PeopleRepository,
    private val units: OrganizationRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        number: String,
        profile: PersonProfile,
        terms: EmploymentTerms,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.manage")
        if (access is Result.Failed) return access
        val employeeNumber = number.trim().uppercase()
        if (!employeeNumber.matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}")))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employee_number"))
        val person =
            profile.copy(
                legalName = profile.legalName.trim(),
                nationality = profile.nationality.uppercase(),
                email = profile.email?.trim()?.lowercase(),
            )
        val profileValidation = validatePerson(person, LocalDate.now(clock))
        if (profileValidation is Result.Failed) return profileValidation
        val validation = validateEmployment(terms, reason)
        if (validation is Result.Failed) return validation
        if (terms.effectiveFrom != terms.startDate)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "initial_effective_date_must_match_start")
            )
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.create",
                operationId,
                listOf(
                    id.toString(),
                    employeeNumber,
                    person.id.toString(),
                    person.accountId?.toString(),
                    person.legalName,
                    person.birthDate?.toString(),
                    person.nationality,
                    person.email,
                    terms.effectiveFrom.toString(),
                    terms.contract.name,
                    terms.startDate.toString(),
                    terms.endDate?.toString(),
                    terms.status.name,
                    terms.branchId?.toString(),
                    terms.departmentId?.toString(),
                    terms.positionId?.toString(),
                    terms.costCenterId?.toString(),
                    terms.managerId?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val lock = people.lockReportingLines(company)
            if (lock is Result.Failed) return@run lock
            val unitLock = units.lockStructure(company)
            if (unitLock is Result.Failed) return@run unitLock
            for ((unitId, kind) in
                listOf(
                    terms.branchId to UnitKind.BRANCH,
                    terms.departmentId to UnitKind.DEPARTMENT,
                    terms.positionId to UnitKind.POSITION,
                    terms.costCenterId to UnitKind.COST_CENTER,
                )) {
                if (unitId == null) continue
                val result = units.find(company, unitId)
                if (result is Result.Failed) return@run result
                val unit = (result as Result.Success).value
                if (unit == null || !unit.active || unit.kind != kind)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "organization_assignment_unavailable")
                    )
            }
            if (person.accountId != null) {
                val result = identities.access(person.accountId, company)
                if (result is Result.Failed) return@run result
                val member = (result as Result.Success).value
                if (member == null || !member.account.active || !member.membershipActive)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "account_membership_required")
                    )
            }
            if (terms.managerId != null) {
                val manager = people.find(company, terms.managerId, terms.effectiveFrom)
                if (manager is Result.Failed) return@run manager
                if (
                    (manager as Result.Success).value?.terms?.isWorkingOn(terms.effectiveFrom) !=
                        true
                )
                    return@run Result.Failed(Failure(FailureKind.VALIDATION, "manager_unavailable"))
            }
            people.create(actor, id, employeeNumber, person, terms, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "employment",
                                id,
                                "people.employee_created",
                                reason = reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
