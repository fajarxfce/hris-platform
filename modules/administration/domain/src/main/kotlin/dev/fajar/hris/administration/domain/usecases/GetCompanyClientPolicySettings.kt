package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.CompanyClientPolicySettings
import dev.fajar.hris.administration.domain.policies.clientPolicyStatus
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.policies.validateSessionAssurance
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock

class GetCompanyClientPolicySettings(
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor): Result<CompanyClientPolicySettings> {
        val allowed = actor.requirePermission("settings.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
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
            val now = clock.instant()
            val assurance =
                validateSessionAssurance(
                    current.account,
                    current.securityPermissions,
                    actor.mfaVerifiedAt,
                    now,
                    security,
                )
            if (assurance is Result.Failed) return@run assurance
            policies.find(company).flatMap { latest ->
                policies.effective(company, now).map { effective ->
                    CompanyClientPolicySettings(latest, clientPolicyStatus(effective, now))
                }
            }
        }
    }
}
