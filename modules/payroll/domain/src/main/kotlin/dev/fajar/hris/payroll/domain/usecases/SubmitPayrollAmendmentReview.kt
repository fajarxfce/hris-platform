package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class SubmitPayrollAmendmentReview(
    private val amendments: PayrollAmendmentRepository,
    private val reviews: PayrollAmendmentReviewRepository,
    private val sources: PayrollAmendmentSourceRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        amendmentId: UUID,
        id: UUID,
        expectedVersion: Long,
        calculationId: UUID,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollMutation(actor, "payroll.amend", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val valid = validatePayrollAmendmentCommand(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "payroll.amendment_review_submit",
                operationId,
                listOf(
                    amendmentId.toString(),
                    id.toString(),
                    expectedVersion.toString(),
                    calculationId.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val payrollGuard = policies.lock(company)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val approvalGuard = approvals.lock(company)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = requirePayrollMutation(live, "payroll.amend", clock.instant(), security)
            if (allowed is Result.Failed) return@run allowed
            val found = amendments.find(company, amendmentId)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_not_found")
                    )
            val owner = people.accountForEmployee(company, current.summary.employee.id)
            if (owner is Result.Failed) return@run owner
            val beneficiary = (owner as Result.Success).value
            if (beneficiary == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .flatMap {
                        requirePayrollMutation(it, "payroll.amend", clock.instant(), security).map {
                            receipt
                        }
                    }
            }
            if (current.summary.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val foundCalculation = amendments.calculation(company, amendmentId, calculationId)
            if (foundCalculation is Result.Failed) return@run foundCalculation
            val calculation =
                (foundCalculation as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_calculation_not_found")
                    )
            val foundPrevious = reviews.latest(company, amendmentId)
            if (foundPrevious is Result.Failed) return@run foundPrevious
            val previous = (foundPrevious as Result.Success).value
            val submission =
                validatePayrollAmendmentSubmission(current.summary, calculation.summary, previous)
            if (submission is Result.Failed) return@run submission
            val preview =
                calculation.preview
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_amendment_calculation_required")
                    )
            val acquired = sources.sources(company, current.summary.firstAssessmentId)
            if (acquired is Result.Failed) return@run acquired
            val basis =
                preparePayrollAmendmentCorrections(
                    current.summary.firstAssessmentId,
                    (acquired as Result.Success).value,
                    current.terms.sourceVersions,
                    current.terms.corrections,
                )
            if (basis is Result.Failed) return@run basis
            val preparers = amendments.preparers(company, amendmentId)
            if (preparers is Result.Failed) return@run preparers
            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val companyRecord =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = LocalDate.ofInstant(now, ZoneId.of(companyRecord.timezone))
            val exposure = payrollAmendmentReviewExposure(preview)
            val context =
                ApprovalContext(
                    UUID.randomUUID(),
                    id,
                    ApprovalKind.PAYROLL_AMENDMENT,
                    actor.accountId,
                    beneficiary,
                    null,
                    today,
                    null,
                    exposure,
                    now,
                    (preparers as Result.Success).value,
                )
            val templates = approvals.templates(company, ApprovalKind.PAYROLL_AMENDMENT, today)
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
                PayrollAmendmentReview(
                    id,
                    amendmentId,
                    (previous?.number ?: 0) + 1,
                    expectedVersion,
                    calculationId,
                    approval.id,
                    today,
                    companyRecord.timezone,
                    exposure,
                    actor.accountId,
                    now,
                    reason,
                    PayrollAmendmentReviewStatus.PENDING,
                    0,
                )
            val submitted =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = expectedVersion + 1,
                            status = PayrollAmendmentStatus.IN_REVIEW,
                            action = PayrollAmendmentAction.SUBMITTED,
                            latestReviewId = id,
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = reason,
                        )
                )
            approvals
                .create(company, approval)
                .flatMap { reviews.create(company, review) }
                .flatMap { amendments.save(company, submitted, expectedVersion) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                amendmentId,
                                "payroll.amendment_review_submitted",
                                mapOf(
                                    "reviewId" to id.toString(),
                                    "calculationId" to calculationId.toString(),
                                    "approvalId" to approval.id.toString(),
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                        }
                        .flatMap {
                            requirePayrollMutation(it, "payroll.amend", clock.instant(), security)
                                .map { receipt }
                        }
                }
        }
    }
}
