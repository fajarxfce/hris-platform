package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class ListAudienceGroups(
    private val groups: AudienceGroupRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        after: UUID? = null,
        limit: Int = 50,
    ): Result<Page<AudienceGroupSummary>> {
        val allowed = actor.requirePermission("announcements.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (limit !in 1..200)
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_page_size",
                    parameters = mapOf("maximum" to "200"),
                )
            )
        return transactions.run(actor) {
            val guard = groups.lock(company, shared = true)
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
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("announcements.manage") }
            if (authorized is Result.Failed) return@run authorized
            groups.list(company, after, limit)
        }
    }
}
