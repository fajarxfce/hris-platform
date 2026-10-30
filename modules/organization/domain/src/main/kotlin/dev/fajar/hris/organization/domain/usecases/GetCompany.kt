package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.*
import java.time.Clock

class GetCompany(
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor): Result<Company> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("company.read")
        if (access is Result.Failed) return access
        return transactions.run(actor) {
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
            companies
                .find(company)
                .flatMap {
                    if (it == null)
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
                    else Result.Success(it)
                }
                .flatMap { acquired ->
                    validateCompanySessionActor(actor, identityAccess, clock.instant(), security)
                        .map { acquired }
                }
        }
    }
}
