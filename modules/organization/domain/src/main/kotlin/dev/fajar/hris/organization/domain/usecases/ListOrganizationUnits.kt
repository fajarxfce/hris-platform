package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.policies.validateOrganizationUnitSearch
import dev.fajar.hris.organization.domain.repositories.*
import java.time.Clock

class ListOrganizationUnits(
    private val units: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, search: OrganizationUnitSearch): Result<Page<OrganizationUnit>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("company.read")
        if (access is Result.Failed) return access
        val validated = validateOrganizationUnitSearch(search)
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
            val identityAccess = (authority as Result.Success).value
            val checked =
                validateCompanySessionActor(actor, identityAccess, clock.instant(), security)
                    .flatMap { it.requirePermission("company.read") }
            if (checked is Result.Failed) return@run checked
            units.list(company, (validated as Result.Success).value).flatMap { acquired ->
                validateCompanySessionActor(actor, identityAccess, clock.instant(), security).map {
                    acquired
                }
            }
        }
    }
}
