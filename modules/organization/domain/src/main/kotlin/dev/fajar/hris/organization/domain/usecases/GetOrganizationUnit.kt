package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.OrganizationUnitDetails
import dev.fajar.hris.organization.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetOrganizationUnit(
    private val units: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<OrganizationUnitDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("company.read")
        if (access is Result.Failed) return access
        return transactions.run(actor) {
            val structure = units.lockStructure(company, shared = true)
            if (structure is Result.Failed) return@run structure
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("company.read") }
            if (checked is Result.Failed) return@run checked
            val found = units.find(company, id)
            if (found is Result.Failed) return@run found
            val unit =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "organization_unit_not_found")
                    )
            val parentId = unit.parentId
            if (parentId == null)
                return@run Result.Success(OrganizationUnitDetails(company, unit, null))
            units.find(company, parentId).flatMap { parent ->
                if (parent == null)
                    Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "organization_structure_unavailable")
                    )
                else Result.Success(OrganizationUnitDetails(company, unit, parent))
            }
        }
    }
}
