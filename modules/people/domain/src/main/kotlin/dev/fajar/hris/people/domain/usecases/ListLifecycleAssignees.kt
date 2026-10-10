package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.LifecycleAssignee
import java.time.Clock
import java.util.UUID

class ListLifecycleAssignees(
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<LifecycleAssignee>> {
        val initial = actor.requirePermission("people.lifecycle.manage")
        if (initial is Result.Failed) return initial
        if (limit !in 1..200 || query.length > 120)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("people.lifecycle.manage")
            if (permission is Result.Failed) return@run permission
            members
                .activeReferences(
                    company,
                    setOf("people.lifecycle.manage", "people.lifecycle.perform"),
                    query.trim(),
                    after,
                    limit,
                )
                .map { page ->
                    Page(
                        page.items.map { LifecycleAssignee(it.id, it.displayName) },
                        page.nextCursor,
                    )
                }
        }
    }
}
