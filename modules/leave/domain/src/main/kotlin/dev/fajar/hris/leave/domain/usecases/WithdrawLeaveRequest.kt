package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import java.time.*
import java.util.UUID

class WithdrawLeaveRequest(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val cutoffs: PayrollCutoffRepository,
    private val security: IdentitySecurityPolicy,
    private val carry: LeaveCarryRepository,
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
            val original = requests.find(company, id)
            if (original is Result.Failed) return@run original
            val employeeId =
                (original as Result.Success).value?.employeeId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            val lock = ledger.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val cutoffLock = cutoffs.lock(company)
            if (cutoffLock is Result.Failed) return@run cutoffLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checkedActor = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checkedActor is Result.Failed) return@run checkedActor
            val live = (checkedActor as Result.Success).value
            if ("leave.manage" !in live.permissions && "leave.self.manage" !in live.permissions)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = requests.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            if (!canManageLeave(live, request))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_request_not_found"))
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .map { receipt }
            }

            val frozen =
                cutoffs.frozenMonths(
                    company,
                    employeeId,
                    request.days.map { java.time.YearMonth.from(it.workDate) }.toSet(),
                )
            if (frozen is Result.Failed) return@run frozen
            if ((frozen as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_frozen"))

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
                movements
                    .map {
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
                    .toMutableList()
            val carryPostings = mutableListOf<LeaveCarryPosting>()
            val effect = if (cancellation) null else LeaveBalanceEffect.RELEASE
            if (effect != null && request.carryAllocationCount != 0) {
                val acquiredFunding = carry.allocations(company, id)
                if (acquiredFunding is Result.Failed) return@run acquiredFunding
                val allocations = (acquiredFunding as Result.Success).value
                if (allocations.size != request.carryAllocationCount || allocations.isEmpty())
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
                val acquiredCarry =
                    carry.buckets(
                        company,
                        employeeId,
                        request.policy.typeId,
                        allocations.map { it.year }.toSet(),
                    )
                if (acquiredCarry is Result.Failed) return@run acquiredCarry
                val plannedCarry =
                    planLeaveCarryRequestCompletion(
                        request.carryAllocationCount,
                        allocations,
                        (acquiredCarry as Result.Success).value,
                        effect,
                        now,
                    )
                if (plannedCarry is Result.Failed) return@run plannedCarry
                for (plan in (plannedCarry as Result.Success).value) {
                    val movementId = UUID.randomUUID()
                    val entry =
                        if (plan.movement.effect == LeaveCarryEffect.EXPIRE) {
                            LeaveLedgerEntry(
                                    UUID.randomUUID(),
                                    employeeId,
                                    request.policy.typeId,
                                    plan.year,
                                    LeaveLedgerKind.EXPIRE,
                                    movementId,
                                    null,
                                    -plan.movement.halfDays,
                                    0,
                                    0,
                                    actor.accountId,
                                    now,
                                    reason.ifBlank { "Carry validity ended" },
                                    movementId,
                                )
                                .also { entries += it }
                        } else entries.single { it.year == plan.year && it.kind == effect.kind }
                    carryPostings +=
                        LeaveCarryPosting(
                            movementId,
                            plan.movement,
                            plan.expectedVersion,
                            entry.id,
                            if (plan.movement.effect == LeaveCarryEffect.EXPIRE) null else id,
                        )
                }
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
                    carry
                        .append(company, carryPostings)
                        .flatMap { ledger.append(company, entries) }
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
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                                .map { receipt }
                        }
                }
        }
    }
}
