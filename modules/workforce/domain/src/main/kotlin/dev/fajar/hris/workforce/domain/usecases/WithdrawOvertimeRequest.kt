package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class WithdrawOvertimeRequest(
    private val overtime: OvertimeRepository,
    private val periods: WorkPeriodRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val approvals: ApprovalRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        if (actor.permissions.none { it in setOf("overtime.manage", "overtime.self.manage") })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version !in 0..998 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_withdrawal"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "overtime.withdraw",
                operationId,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val originalResult = overtime.find(company, id)
            if (originalResult is Result.Failed) return@run originalResult
            val original =
                (originalResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                    )
            val employeeId = original.employeeId
            val periodResult = periods.lockMonth(company, YearMonth.from(original.workDate), false)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            val lock = overtime.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (live.permissions.none { it in setOf("overtime.manage", "overtime.self.manage") })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val now = clock.instant()
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val currentResult = people.find(company, employeeId, today)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (!canManageOvertime(live, current, today))
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = overtime.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                    )
            if (request.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val mutable = requireMutablePeriod(period)
            if (mutable is Result.Failed) return@run mutable
            if (request.status !in setOf(OvertimeStatus.PLANNED, OvertimeStatus.PENDING))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "overtime_not_pending"))
            if (request.approvalId != null) {
                val approvalResult = approvals.find(company, request.approvalId)
                if (approvalResult is Result.Failed) return@run approvalResult
                val approval =
                    (approvalResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "approval_unavailable")
                        )
                if (approval.status !in setOf(ApprovalStatus.PENDING, ApprovalStatus.BLOCKED))
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
                val cancelled = approvals.cancel(company, approval.id, approval.version)
                if (cancelled is Result.Failed) return@run cancelled
            }
            overtime
                .update(
                    actor,
                    request.copy(status = OvertimeStatus.WITHDRAWN, decidedAt = now),
                    OvertimeChangeKind.WITHDRAWN,
                    reason,
                    now,
                )
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "overtime_request",
                                id,
                                "overtime.withdrawn",
                                emptyMap(),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .map { receipt }
                }
        }
    }
}
