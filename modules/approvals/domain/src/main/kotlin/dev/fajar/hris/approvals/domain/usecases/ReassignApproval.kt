package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.approvalPermissions
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.util.UUID

class ReassignApproval(
    private val approvals: ApprovalRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        assignees: Set<UUID>,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("approvals.manage")
        if (access is Result.Failed) return access
        if (version < 0 || assignees.size !in 1..25 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_approval_reassignment"))
        val key =
            OperationKey(
                "approvals.reassign",
                operationId,
                listOf(id.toString(), version.toString(), reason) +
                    assignees.map(UUID::toString).sorted(),
            )
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = approvals.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "approval_not_found")
                    )
            if (
                request.version != version ||
                    request.status !in setOf(ApprovalStatus.PENDING, ApprovalStatus.BLOCKED)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            val candidates = members.candidates(company, assignees, emptySet(), 26)
            if (candidates is Result.Failed) return@run candidates
            val eligible =
                (candidates as Result.Success)
                    .value
                    .filter {
                        it.accountActive &&
                            it.membershipActive &&
                            it.id != request.authorId &&
                            it.id != request.requesterId &&
                            it.permissions.any { p -> p in approvalPermissions(request.kind) }
                    }
                    .map { it.id }
                    .toSet()
            if (eligible != assignees)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "approver_unavailable"))
            approvals.reassign(actor, request, assignees, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "approval_request",
                                id,
                                "approvals.reassigned",
                                mapOf(
                                    "step" to request.currentStep.toString(),
                                    "assignees" to
                                        assignees.map(UUID::toString).sorted().joinToString(","),
                                ),
                                reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
