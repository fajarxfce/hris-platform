package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.approvalPermissions
import dev.fajar.hris.approvals.domain.policies.isApprovalInboxRecipient
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.util.UUID

class ListApprovalInbox(
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<ApprovalRequest>> {
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val approvalGuard = approvals.lock(company, shared = true)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access = identities.access(actor.accountId, company)
            if (access is Result.Failed) return@run access
            val resolved = (access as Result.Success).value
            val checked = validateCompanySessionActor(actor, resolved, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val found =
                live.requirePermission("approvals.read").flatMap {
                    approvals.inbox(
                        company,
                        actor.accountId,
                        "approvals.manage" in live.permissions,
                        ApprovalKind.entries
                            .filter { kind ->
                                approvalPermissions(kind).any { it in live.permissions }
                            }
                            .toSet(),
                        clock.instant(),
                        after,
                        limit,
                    )
                }
            if (found is Result.Failed) return@run found
            val page = (found as Result.Success).value
            val needsDelegations =
                page.items.any {
                    !(it.status == ApprovalStatus.BLOCKED &&
                        "approvals.manage" in live.permissions) &&
                        actor.accountId !in it.stages[it.currentStep].assignees
                }
            val delegated =
                if (needsDelegations)
                    approvals.delegations(company, actor.accountId, clock.instant())
                else Result.Success(emptyList<Delegation>())
            if (delegated is Result.Failed) return@run delegated
            val delegations = (delegated as Result.Success).value
            val delegatorIds = delegations.map { it.fromAccount }.toSet()
            val grants =
                if (delegatorIds.isEmpty())
                    Result.Success(
                        emptyList<dev.fajar.hris.identity.domain.entities.MemberAccount>()
                    )
                else members.candidates(company, delegatorIds, emptySet(), delegatorIds.size)
            if (grants is Result.Failed) return@run grants
            val now = clock.instant()
            val finalAccess = validateCompanySessionActor(actor, resolved, now, security)
            if (finalAccess is Result.Failed) return@run finalAccess
            val finalActor = (finalAccess as Result.Success).value
            val candidates = (grants as Result.Success).value
            if (
                page.items.any {
                    !isApprovalInboxRecipient(finalActor, it, delegations, candidates, now)
                }
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            Result.Success(page)
        }
    }
}
