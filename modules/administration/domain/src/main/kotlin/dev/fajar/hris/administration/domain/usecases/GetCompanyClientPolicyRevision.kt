package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.CompanyClientPolicyRevision
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.policies.validateSessionAssurance
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock

class GetCompanyClientPolicyRevision(
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
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
            val found = identities.access(actor.accountId, company)
            if (found is Result.Failed) return@run found
            val access = (found as Result.Success).value
            val authorized =
                validateCompanyCommandActor(actor, access).flatMap {
                    it.requirePermission("settings.manage")
                }
            if (authorized is Result.Failed) return@run authorized
            val current = requireNotNull(access)
            fun assurance(): Result<Unit> =
                validateSessionAssurance(
                    current.account,
                    current.securityPermissions,
                    actor.mfaVerifiedAt,
                    clock.instant(),
                    security,
                )
            val checked = assurance()
            if (checked is Result.Failed) return@run checked
            policies.find(company, revision).flatMap { value ->
                assurance().flatMap {
                    if (revision != null && value == null)
                        Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "client_policy_revision_not_found")
                        )
                    else Result.Success(value)
                }
            }
        }
    }
}
