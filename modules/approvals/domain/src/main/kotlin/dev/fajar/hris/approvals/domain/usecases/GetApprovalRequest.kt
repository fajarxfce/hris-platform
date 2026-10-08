package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest
import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.util.UUID

class GetApprovalRequest(
    private val approvals: ApprovalRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<ApprovalRequest> =
        transactions.run(actor) {
            val company = requireNotNull(actor.companyId)
            val found = approvals.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "approval_not_found")
                    )
            if (
                "approvals.manage" in actor.permissions ||
                    actor.accountId == request.authorId ||
                    actor.accountId == request.requesterId
            )
                return@run Result.Success(request)
            val now = clock.instant()
            val delegated = approvals.delegations(company, actor.accountId, now)
            if (delegated is Result.Failed) return@run delegated
            val assigned = request.stages.flatMap { it.assignees }.toSet()
            val grants = members.candidates(company, assigned, emptySet(), assigned.size)
            if (grants is Result.Failed) return@run grants
            if (
                isAssignedApprover(
                    actor,
                    request,
                    (delegated as Result.Success).value,
                    (grants as Result.Success).value,
                    now,
                )
            )
                Result.Success(request)
            else Result.Failed(Failure(FailureKind.NOT_FOUND, "approval_not_found"))
        }
}
