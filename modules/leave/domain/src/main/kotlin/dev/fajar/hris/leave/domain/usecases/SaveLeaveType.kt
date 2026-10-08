package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveType
import dev.fajar.hris.leave.domain.policies.validateLeaveType
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository

class SaveLeaveType(
    private val policies: LeavePolicyRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        operationId: java.util.UUID,
        type: LeaveType,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("leave.manage")
        if (access is Result.Failed) return access
        val valid = validateLeaveType(type, reason)
        if (valid is Result.Failed) return valid
        if ((expectedVersion ?: 0) < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "leave.type_save",
                operationId,
                listOf(
                    type.id.toString(),
                    type.code,
                    type.effectiveFrom.toString(),
                    type.policy.name,
                    type.policy.paid.toString(),
                    type.policy.allowPartialDays.toString(),
                    type.policy.minServiceMonths.toString(),
                    type.policy.allowedContracts.map { it.name }.sorted().joinToString(","),
                    type.policy.maxRequestDays.toString(),
                    type.active.toString(),
                    expectedVersion?.toString(),
                    reason,
                ),
            )
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val lock = policies.lock(company)
            if (lock is Result.Failed) return@run lock
            val existing = policies.find(company, type.id)
            if (existing is Result.Failed) return@run existing
            val current = (existing as Result.Success).value
            if (current?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current != null && current.code != type.code)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "leave_type_code_immutable")
                )
            policies.save(actor, type, expectedVersion, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "leave_type",
                                type.id,
                                "leave.policy_saved",
                                reason = reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
