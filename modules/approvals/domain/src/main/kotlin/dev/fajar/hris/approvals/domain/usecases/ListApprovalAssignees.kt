package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.ApprovalAssignee
import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import dev.fajar.hris.approvals.domain.policies.approvalPermissions
import dev.fajar.hris.approvals.domain.policies.canSelectApprovalAssignees
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.util.UUID

class ListApprovalAssignees(
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        kind: ApprovalKind,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<ApprovalAssignee>> {
        if (!canSelectApprovalAssignees(actor, kind))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..200 || query.length > 120)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val companyGuard = identities.lockCompany(company, shared = true)
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
            if (!canSelectApprovalAssignees((checked as Result.Success).value, kind))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            members
                .activeReferences(company, approvalPermissions(kind), query.trim(), after, limit)
                .map { page ->
                    Page(
                        page.items.map { ApprovalAssignee(it.id, it.displayName) },
                        page.nextCursor,
                    )
                }
        }
    }
}
