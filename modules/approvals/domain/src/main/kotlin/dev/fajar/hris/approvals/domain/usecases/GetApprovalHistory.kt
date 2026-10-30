package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetApprovalHistory(
    private val approvals: ApprovalRepository,
    private val history: ApprovalHistoryRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Long?,
        limit: Int,
        expectedVersion: Long?,
    ): Result<ApprovalHistory> {
        if (
            limit !in 1..200 ||
                (after != null && (after !in 1..9007199254740991L || expectedVersion == null)) ||
                (expectedVersion != null && expectedVersion !in 0..9007199254740991L)
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_approval_history_query"))
        return transactions.run(actor) {
            val company =
                actor.companyId
                    ?: return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
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
            val found = approvals.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "approval_not_found")
                    )
            val owner =
                "approvals.manage" in live.permissions ||
                    actor.accountId == request.authorId ||
                    actor.accountId == request.requesterId
            val delegated =
                if (owner) Result.Success(emptyList<Delegation>())
                else approvals.delegations(company, actor.accountId, clock.instant())
            if (delegated is Result.Failed) return@run delegated
            val assigned = request.stages.flatMap { it.assignees }.toSet()
            val grants =
                if (owner)
                    Result.Success(
                        emptyList<dev.fajar.hris.identity.domain.entities.MemberAccount>()
                    )
                else members.candidates(company, assigned, emptySet(), assigned.size)
            if (grants is Result.Failed) return@run grants
            val delegations = (delegated as Result.Success).value
            val candidates = (grants as Result.Success).value
            if (
                !owner &&
                    !isAssignedApprover(live, request, delegations, candidates, clock.instant())
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "approval_not_found"))
            if (
                (expectedVersion != null && request.version != expectedVersion) ||
                    (after != null && after > request.version)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            val decisions = history.decisions(company, id, 9)
            if (decisions is Result.Failed) return@run decisions
            val changes = history.reassignments(company, id, after, limit + 1)
            if (changes is Result.Failed) return@run changes
            val entries = (decisions as Result.Success).value
            val overrides = (changes as Result.Success).value
            if (
                entries.size > 8 ||
                    entries.any { it.step !in request.stages.indices } ||
                    entries.zipWithNext().any { (a, b) -> a.step >= b.step } ||
                    overrides.size > limit + 1 ||
                    overrides.any {
                        it.step !in request.stages.indices ||
                            it.revision <= (after ?: 0) ||
                            it.revision > request.version
                    } ||
                    overrides.zipWithNext().any { (a, b) -> a.revision >= b.revision }
            )
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "approval_history_unavailable")
                )
            val now = clock.instant()
            val finalAccess = validateCompanySessionActor(actor, resolved, now, security)
            if (finalAccess is Result.Failed) return@run finalAccess
            if (
                !owner &&
                    !isAssignedApprover(
                        (finalAccess as Result.Success).value,
                        request,
                        delegations,
                        candidates,
                        now,
                    )
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "approval_not_found"))
            Result.Success(
                ApprovalHistory(
                    request,
                    entries,
                    Page(
                        overrides.take(limit),
                        if (overrides.size > limit) overrides[limit - 1].revision.toString()
                        else null,
                    ),
                )
            )
        }
    }
}
