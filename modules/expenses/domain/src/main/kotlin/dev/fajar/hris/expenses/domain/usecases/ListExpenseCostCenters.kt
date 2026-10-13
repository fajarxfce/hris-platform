package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseCostCenterSnapshot
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.OrganizationUnitSearch
import dev.fajar.hris.organization.domain.entities.UnitKind
import dev.fajar.hris.organization.domain.policies.validateOrganizationUnitSearch
import dev.fajar.hris.organization.domain.repositories.*
import java.time.Clock

class ListExpenseCostCenters(
    private val units: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<ExpenseCostCenterSnapshot>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permitted = setOf("expenses.self.manage", "expenses.manage")
        if (actor.permissions.none { it in permitted })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..50) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val validated =
            validateOrganizationUnitSearch(
                OrganizationUnitSearch(UnitKind.COST_CENTER, query, true, after, limit)
            )
        if (validated is Result.Failed) return validated
        return transactions.run(actor) {
            val structure = units.lockStructure(company, shared = true)
            if (structure is Result.Failed) return@run structure
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if ((checked as Result.Success).value.permissions.none { it in permitted })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            units.list(company, (validated as Result.Success).value).flatMap { page ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map {
                    Page(
                        page.items.map { unit ->
                            ExpenseCostCenterSnapshot(unit.id, unit.code, unit.name, unit.version)
                        },
                        page.nextCursor,
                    )
                }
            }
        }
    }
}
