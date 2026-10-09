package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.AuditRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock

class SearchAuditEvents(
    private val audits: AuditRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, input: AuditSearch): Result<AuditPage> {
        val allowed = actor.requirePermission("audit.read")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val prepared = prepareAuditQuery(input, clock.instant())
        if (prepared is Result.Failed) return prepared
        val query = (prepared as Result.Success).value
        return transactions.run(actor) {
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
                    it.requirePermission("audit.read")
                }
            if (authorized is Result.Failed) return@run authorized
            val now = clock.instant()
            val current = requireNotNull(access)
            val assurance =
                validateSessionAssurance(
                    current.account,
                    current.securityPermissions,
                    actor.mfaVerifiedAt,
                    now,
                    security,
                )
            if (assurance is Result.Failed) return@run assurance
            val scoped =
                if (input.cursor == null) Result.Success(query)
                else audits.find(company, input.cursor).flatMap { validateAuditCursor(query, it) }
            if (scoped is Result.Failed) return@run scoped
            audits.search(company, (scoped as Result.Success).value).map { page ->
                AuditPage(company, query.from, query.until, now, page.items, page.nextCursor)
            }
        }
    }
}
