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
import java.math.BigDecimal
import java.time.*
import java.util.UUID

class SubmitOvertimeActual(
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
        actual: OvertimeInterval,
        reason: String,
    ): Result<MutationReceipt> {
        if (actor.permissions.none { it in setOf("overtime.manage", "overtime.self.manage") })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version !in 0..998 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_actual"))
        val valid = validateOvertimeInterval(actual, "actual")
        if (valid is Result.Failed) return valid
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "overtime.submit_actual",
                operationId,
                listOf(
                    id.toString(),
                    version.toString(),
                    actual.startsAt.toString(),
                    actual.endsAt.toString(),
                    actual.breakMinutes.toString(),
                    reason,
                ),
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
            if (request.status != OvertimeStatus.PLANNED)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "overtime_not_planned"))
            val timing = validateOvertimeActual(request, actual, now)
            if (timing is Result.Failed) return@run timing
            val beneficiaryResult = people.accountForEmployee(company, employeeId)
            if (beneficiaryResult is Result.Failed) return@run beneficiaryResult
            val beneficiary = (beneficiaryResult as Result.Success).value
            val context =
                ApprovalContext(
                    UUID.randomUUID(),
                    id,
                    ApprovalKind.OVERTIME,
                    actor.accountId,
                    beneficiary,
                    current?.managerAccountId,
                    today,
                    null,
                    BigDecimal.ZERO,
                    now,
                )
            val selected =
                approvals.templates(company, ApprovalKind.OVERTIME, today).flatMap {
                    selectApprovalTemplate(it, context)
                }
            if (selected is Result.Failed) return@run selected
            val template = (selected as Result.Success).value
            val candidatesResult =
                members.candidates(
                    company,
                    approvalCandidateIds(template, context),
                    approvalCandidatePermissions(template),
                    201,
                )
            if (candidatesResult is Result.Failed) return@run candidatesResult
            val candidates = (candidatesResult as Result.Success).value
            if (candidates.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "approval_group_too_large")
                )
            val snapshotResult =
                snapshotApproval(
                    template,
                    context,
                    candidates.filter {
                        it.id !in setOfNotNull(request.authorId, request.requesterAccountId)
                    },
                )
            if (snapshotResult is Result.Failed) return@run snapshotResult
            val snapshot = (snapshotResult as Result.Success).value
            val submitted =
                request.copy(
                    status = OvertimeStatus.PENDING,
                    actual = actual,
                    submittedBy = actor.accountId,
                    submittedAt = now,
                    approvalId = snapshot.id,
                )
            approvals
                .create(company, snapshot)
                .flatMap {
                    overtime.update(
                        actor,
                        submitted,
                        OvertimeChangeKind.ACTUAL_SUBMITTED,
                        reason,
                        now,
                    )
                }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "overtime_request",
                                id,
                                "overtime.actual_submitted",
                                mapOf("approvalId" to snapshot.id.toString()),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .map { receipt }
                }
        }
    }
}
