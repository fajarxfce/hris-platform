package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class RequestLeaveCancellation(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
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
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_cancellation"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.cancellation_request",
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
            if (request.status != LeaveStatus.APPROVED)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_not_approved"))
            val now = clock.instant()
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val employeeResult = people.find(company, employeeId, today)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee = (employeeResult as Result.Success).value
            val context =
                ApprovalContext(
                    UUID.randomUUID(),
                    id,
                    ApprovalKind.LEAVE_CANCELLATION,
                    actor.accountId,
                    request.ownerAccountId,
                    if (employee?.terms?.isWorkingOn(today) == true) employee.managerAccountId
                    else null,
                    today,
                    request.policy.code,
                    java.math.BigDecimal.ZERO,
                    now,
                )
            val templateResult =
                approvals.templates(company, ApprovalKind.LEAVE_CANCELLATION, today).flatMap {
                    selectApprovalTemplate(it, context)
                }
            if (templateResult is Result.Failed) return@run templateResult
            val template = (templateResult as Result.Success).value
            val candidateResult =
                members.candidates(
                    company,
                    approvalCandidateIds(template, context),
                    approvalCandidatePermissions(template),
                    201,
                )
            if (candidateResult is Result.Failed) return@run candidateResult
            val snapshotResult =
                snapshotApproval(template, context, (candidateResult as Result.Success).value)
            if (snapshotResult is Result.Failed) return@run snapshotResult
            val snapshot = (snapshotResult as Result.Success).value
            approvals
                .create(company, snapshot)
                .flatMap {
                    requests.update(
                        actor,
                        id,
                        version,
                        LeaveStatus.CANCELLATION_PENDING,
                        snapshot.id,
                        LeaveChangeKind.CANCELLATION_REQUESTED,
                        reason,
                        now,
                    )
                }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "leave_request",
                                    id,
                                    "leave.cancellation_requested",
                                    mapOf("approvalId" to snapshot.id.toString()),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
