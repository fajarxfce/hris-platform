package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class SubmitPayrollReview(
    private val reviews: PayrollReviewRepository,
    private val runs: PayrollRunRepository,
    private val policies: PayrollPolicyRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        runId: UUID,
        expectedRunVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("payroll.calculate")
        if (permission is Result.Failed) return permission
        if (expectedRunVersion !in 0..24 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_review"))
        val key =
            OperationKey(
                "payroll.review_submit",
                operationId,
                listOf(id.toString(), runId.toString(), expectedRunVersion.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
            val approvalGuard = approvals.lock(company)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val access =
                requirePayrollMutation(live, "payroll.calculate", clock.instant(), security)
            if (access is Result.Failed) return@run access
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = runs.find(company, runId)
            if (found is Result.Failed) return@run found
            val run =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_run_not_found")
                    )
            if (run.version != expectedRunVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val previous = reviews.latest(company, runId)
            if (previous is Result.Failed) return@run previous
            val latest = (previous as Result.Success).value
            val sum = reviews.totals(company, runId)
            if (sum is Result.Failed) return@run sum
            val totals = (sum as Result.Success).value
            val valid = validatePayrollReviewSubmission(run, latest, totals)
            if (valid is Result.Failed) return@run valid
            val now = clock.instant()
            val today = LocalDate.ofInstant(now, ZoneId.of(run.timezone))
            val context =
                ApprovalContext(
                    UUID.randomUUID(),
                    id,
                    ApprovalKind.PAYROLL,
                    actor.accountId,
                    null,
                    null,
                    today,
                    null,
                    totals.takeHome,
                    now,
                    excludedAccountIds = setOf(run.actorId),
                )
            val templates = approvals.templates(company, ApprovalKind.PAYROLL, today)
            if (templates is Result.Failed) return@run templates
            val selected = selectApprovalTemplate((templates as Result.Success).value, context)
            if (selected is Result.Failed) return@run selected
            val template = (selected as Result.Success).value
            if (template.stages.any { it.assignment == AssignmentKind.MANAGER })
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "payroll_approval_assignment_invalid")
                )
            val candidates =
                members.candidates(
                    company,
                    approvalCandidateIds(template, context),
                    approvalCandidatePermissions(template),
                    201,
                )
            if (candidates is Result.Failed) return@run candidates
            val snapshot = snapshotApproval(template, context, (candidates as Result.Success).value)
            if (snapshot is Result.Failed) return@run snapshot
            val approval = (snapshot as Result.Success).value
            val review =
                PayrollReview(
                    id,
                    runId,
                    (latest?.number ?: 0) + 1,
                    run.version,
                    approval.id,
                    today,
                    totals,
                    actor.accountId,
                    now,
                    reason,
                    PayrollReviewStatus.PENDING,
                    0,
                )
            approvals
                .create(company, approval)
                .flatMap { reviews.create(company, review) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_review",
                                id,
                                "payroll.review_submitted",
                                mapOf(
                                    "runId" to runId.toString(),
                                    "approvalId" to approval.id.toString(),
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .map { receipt }
                }
        }
    }
}
