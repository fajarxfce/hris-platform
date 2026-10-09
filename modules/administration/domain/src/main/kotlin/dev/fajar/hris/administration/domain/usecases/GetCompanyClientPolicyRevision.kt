package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.CompanyClientPolicyRevision
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository

class GetCompanyClientPolicyRevision(
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, revision: Long? = null): Result<CompanyClientPolicyRevision?> {
        val allowed = actor.requirePermission("settings.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (revision != null && revision !in 0..9999)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_revision"))
        return transactions.run(actor) {
            val guard = policies.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanyCommandActor(actor, it) }
                    .flatMap { it.requirePermission("settings.manage") }
            if (authorized is Result.Failed) return@run authorized
            policies.find(company, revision).flatMap {
                if (revision != null && it == null)
                    Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "client_policy_revision_not_found")
                    )
                else Result.Success(it)
            }
        }
    }
}
