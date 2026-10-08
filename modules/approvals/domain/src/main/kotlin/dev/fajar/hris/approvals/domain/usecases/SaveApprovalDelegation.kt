package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.approvalPermissions
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.time.Duration
import java.util.UUID

class SaveApprovalDelegation(
    private val approvals: ApprovalRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        delegation: Delegation,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("approvals.read")
        if (access is Result.Failed) return access
        if (delegation.fromAccount != actor.accountId && "approvals.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (
            delegation.fromAccount == delegation.toAccount ||
                !delegation.validUntil.isAfter(delegation.validFrom) ||
                Duration.between(delegation.validFrom, delegation.validUntil) >
                    Duration.ofDays(90) ||
                (delegation.active && !delegation.validUntil.isAfter(clock.instant())) ||
                (expectedVersion ?: 0) < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_delegation"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "approvals.delegation_save",
                operationId,
                listOf(
                    delegation.id.toString(),
                    delegation.kind.name,
                    delegation.fromAccount.toString(),
                    delegation.toAccount.toString(),
                    delegation.validFrom.toString(),
                    delegation.validUntil.toString(),
                    delegation.active.toString(),
                    expectedVersion?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val existing = approvals.findDelegation(company, delegation.id)
            if (existing is Result.Failed) return@run existing
            val previous = (existing as Result.Success).value
            if (previous != null && previous.fromAccount != delegation.fromAccount)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "delegator_immutable"))
            val candidates =
                members.candidates(
                    company,
                    setOf(delegation.fromAccount, delegation.toAccount),
                    emptySet(),
                    3,
                )
            if (candidates is Result.Failed) return@run candidates
            if (
                delegation.active &&
                    (candidates as Result.Success).value.count {
                        it.accountActive &&
                            it.membershipActive &&
                            it.permissions.any { p -> p in approvalPermissions(delegation.kind) }
                    } != 2
            )
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "approver_unavailable"))
            approvals.saveDelegation(company, delegation, expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "approval_delegation",
                                receipt.id,
                                "approvals.delegation_saved",
                                mapOf(
                                    "from" to delegation.fromAccount.toString(),
                                    "to" to delegation.toAccount.toString(),
                                    "active" to delegation.active.toString(),
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
