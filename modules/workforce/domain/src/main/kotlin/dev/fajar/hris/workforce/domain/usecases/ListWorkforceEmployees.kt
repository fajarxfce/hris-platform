package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.EmployeeVisibility
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.WorkforceEmployeeReference
import java.time.Clock
import java.time.LocalDate

class ListWorkforceEmployees(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        asOf: LocalDate,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<WorkforceEmployeeReference>> {
        val access = actor.requirePermission("workforce.read")
        if (access is Result.Failed) return access
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            limit !in 1..50 ||
                query.length > 120 ||
                (after != null && (after.isBlank() || after.length > 32))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val accountAccess = (authority as Result.Success).value
            val authorized =
                validateCompanySessionActor(actor, accountAccess, clock.instant(), security)
            if (authorized is Result.Failed) return@run authorized
            val permission =
                (authorized as Result.Success).value.requirePermission("workforce.read")
            if (permission is Result.Failed) return@run permission
            people
                .list(
                    company,
                    actor.accountId,
                    EmployeeVisibility.ALL,
                    asOf,
                    clock.instant(),
                    query.trim(),
                    after,
                    limit,
                )
                .map { page ->
                    Page(
                        page.items.map { employee ->
                            WorkforceEmployeeReference(
                                employee.id,
                                employee.employeeNumber,
                                employee.person.legalName,
                                employee.terms.status,
                                employee.version,
                            )
                        },
                        page.nextCursor,
                    )
                }
                .flatMap { value ->
                    validateCompanySessionActor(actor, accountAccess, clock.instant(), security)
                        .map { value }
                }
        }
    }
}
