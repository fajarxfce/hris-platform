package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.util.UUID

class SaveApprovalTemplate(
    private val approvals: ApprovalRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, operationId: UUID, request: TemplateChange): Result<MutationReceipt> {
        val access = actor.requirePermission("approvals.manage")
        if (access is Result.Failed) return access
        val change =
            request.copy(
                name = request.name.trim(),
                category = request.category?.trim()?.takeIf { it.isNotEmpty() },
            )
        val valid = validateApprovalTemplate(change)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "approvals.template_save",
                operationId,
                listOf(
                    change.id.toString(),
                    change.name,
                    change.kind.name,
                    change.active.toString(),
                    change.expectedVersion?.toString(),
                    change.effectiveFrom.toString(),
                    change.category,
                    change.minimumAmount.stripTrailingZeros().toPlainString(),
                    change.reason,
                ) +
                    change.stages.flatMap {
                        listOf(it.assignment.name, it.permission, it.accountIds.size.toString()) +
                            it.accountIds.map(UUID::toString).sorted()
                    },
            )
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val existing = approvals.findTemplate(company, change.id)
            if (existing is Result.Failed) return@run existing
            val current = (existing as Result.Success).value
            if (current != null && current.kind != change.kind)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_kind_immutable"))
            val named = change.stages.flatMap { it.accountIds }.toSet()
            val candidates = members.candidates(company, named, emptySet(), 201)
            if (candidates is Result.Failed) return@run candidates
            val eligible =
                (candidates as Result.Success)
                    .value
                    .filter {
                        it.accountActive &&
                            it.membershipActive &&
                            it.permissions.any { permission ->
                                permission in approvalPermissions(change.kind)
                            }
                    }
                    .map { it.id }
                    .toSet()
            if (change.active && !eligible.containsAll(named))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "approver_unavailable"))
            approvals.saveTemplate(actor, change).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "approval_template",
                                receipt.id,
                                "approvals.template_saved",
                                reason = change.reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
