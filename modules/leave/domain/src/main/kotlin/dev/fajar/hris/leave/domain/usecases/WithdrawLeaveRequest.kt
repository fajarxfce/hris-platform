package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import java.time.*
import java.util.UUID

class WithdrawLeaveRequest(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val approvals: ApprovalRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        if ("leave.manage" !in actor.permissions && "leave.self.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_withdrawal"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.request_withdraw",
                operationId,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val original = requests.find(company, id)
            if (original is Result.Failed) return@run original
            val employeeId =
                (original as Result.Success).value?.employeeId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            val lock = ledger.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val found = requests.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            if (!canManageLeave(actor, request))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_request_not_found"))
            if (request.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val cancellation = request.status == LeaveStatus.CANCELLATION_PENDING
            if (request.status != LeaveStatus.PENDING && !cancellation)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_not_pending"))
            val approvalId =
                if (cancellation) requireNotNull(request.cancellationApprovalId)
                else request.approvalId
            val approvalResult = approvals.find(company, approvalId)
            if (approvalResult is Result.Failed) return@run approvalResult
            val approval =
                (approvalResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            if (approval.status !in setOf(ApprovalStatus.PENDING, ApprovalStatus.BLOCKED))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            val movements =
                if (cancellation) emptyList()
                else leaveLedgerMovements(request.days, LeaveBalanceEffect.RELEASE)
            for (movement in movements) {
                val balance =
                    ledger.balance(company, employeeId, request.policy.typeId, movement.year)
                if (balance is Result.Failed) return@run balance
                val valid = validateLeaveMovement((balance as Result.Success).value, movement)
                if (valid is Result.Failed) return@run valid
            }
            val now = clock.instant()
            val entries =
                movements.map {
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        employeeId,
                        request.policy.typeId,
                        it.year,
                        it.kind,
                        id,
                        id,
                        it.availableDelta,
                        it.reservedDelta,
                        it.consumedDelta,
                        actor.accountId,
                        now,
                        reason,
                    )
                }
            approvals
                .cancel(company, approvalId, approval.version)
                .flatMap {
                    requests.update(
                        actor,
                        id,
                        version,
                        if (cancellation) LeaveStatus.APPROVED else LeaveStatus.CANCELLED,
                        request.cancellationApprovalId,
                        LeaveChangeKind.WITHDRAWN,
                        reason,
                        now,
                    )
                }
                .flatMap { receipt ->
                    ledger
                        .append(company, entries)
                        .flatMap {
                            if (cancellation) Result.Success(Unit)
                            else
                                requests.releaseOccupancy(
                                    company,
                                    id,
                                    request.days.sumOf { it.portion.halfDays },
                                )
                        }
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "leave_request",
                                    id,
                                    "leave.request_withdrawn",
                                    mapOf("approvalId" to approvalId.toString()),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
