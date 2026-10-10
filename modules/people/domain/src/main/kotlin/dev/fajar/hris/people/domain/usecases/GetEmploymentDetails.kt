package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.canReadOwnEmployment
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** Authorized employment detail with current reference labels under a common read scope. */
class GetEmploymentDetails(
    private val people: PeopleRepository,
    private val units: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID, asOf: LocalDate): Result<EmploymentDetails> {
        if (!canReadOwnEmployment(actor.permissions))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val structureGuard = units.lockStructure(company, shared = true)
            if (structureGuard is Result.Failed) return@run structureGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadOwnEmployment(live.permissions))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = people.find(company, id, asOf)
            if (found is Result.Failed) return@run found
            val employee =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if ("people.read" !in live.permissions && employee.person.accountId != live.accountId)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val references = mutableMapOf<UUID, EmploymentUnitReference?>()
            for (unitId in
                listOfNotNull(
                        employee.terms.branchId,
                        employee.terms.departmentId,
                        employee.terms.positionId,
                        employee.terms.costCenterId,
                    )
                    .distinct()) {
                val unit = units.find(company, unitId)
                if (unit is Result.Failed) return@run unit
                references[unitId] =
                    (unit as Result.Success).value?.let {
                        EmploymentUnitReference(it.id, it.code, it.name, it.active)
                    }
            }
            val manager =
                employee.terms.managerId?.let { managerId ->
                    people.find(company, managerId, asOf).map { value ->
                        value?.let {
                            EmploymentManagerReference(
                                it.id,
                                it.employeeNumber,
                                it.person.legalName,
                                it.terms.isWorkingOn(asOf),
                            )
                        }
                    }
                } ?: Result.Success(null)
            if (manager is Result.Failed) return@run manager
            Result.Success(
                EmploymentDetails(
                    asOf,
                    employee,
                    references[employee.terms.branchId],
                    references[employee.terms.departmentId],
                    references[employee.terms.positionId],
                    references[employee.terms.costCenterId],
                    (manager as Result.Success).value,
                )
            )
        }
    }
}
