package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class StartPayrollFinalization(
    private val finalizations: PayrollFinalizationRepository,
    private val runs: PayrollRunRepository,
    private val reviews: PayrollReviewRepository,
    private val policies: PayrollPolicyRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val operations: OperationRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        runId: UUID,
        reviewId: UUID,
        expectedRunVersion: Long,
        expectedReviewVersion: Long,
        expectedApprovalVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.finalize")
        if (access is Result.Failed) return access
        if (
            expectedRunVersion !in 0..23 ||
                expectedReviewVersion !in 1..8 ||
                expectedApprovalVersion < 1 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_finalization"))
        val key =
            OperationKey(
                "payroll.finalization_start",
                operationId,
                listOf(
                    id.toString(),
                    runId.toString(),
                    reviewId.toString(),
                    expectedRunVersion.toString(),
                    expectedReviewVersion.toString(),
                    expectedApprovalVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
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
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permitted =
                requirePayrollMutation(live, "payroll.finalize", clock.instant(), security)
            if (permitted is Result.Failed) return@run permitted
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
            val reviewResult = reviews.find(company, reviewId)
            if (reviewResult is Result.Failed) return@run reviewResult
            val review =
                (reviewResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_review_not_found")
                    )
            val approvalResult = approvals.find(company, review.approvalId)
            if (approvalResult is Result.Failed) return@run approvalResult
            val approval = (approvalResult as Result.Success).value
            val approved = requireApprovedPayroll(run, review, approval)
            if (approved is Result.Failed) return@run approved
            if (run.version != expectedRunVersion || review.version != expectedReviewVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (approval!!.version != expectedApprovalVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            val last = finalizations.latest(company, runId)
            if (last is Result.Failed) return@run last
            val previous = (last as Result.Success).value
            if (previous != null) {
                val lastJob = jobs.find(company, previous.jobId)
                if (lastJob is Result.Failed) return@run lastJob
                if (
                    (lastJob as Result.Success).value?.status !in
                        setOf(JobStatus.FAILED, JobStatus.CANCELLED)
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_finalization_active")
                    )
            }
            val number = (previous?.number ?: 0) + 1
            if (number > 8)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_finalization_capacity")
                )
            val readiness =
                finalizations.readiness(company, runId).flatMap(::requirePayrollPublicationSources)
            if (readiness is Result.Failed) return@run readiness
            val queue = jobs.lockQueue(company)
            if (queue is Result.Failed) return@run queue
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val issuer =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.PAYROLL_FINALIZE,
                    operationId,
                    mapOf("finalizationId" to id.toString(), "runId" to runId.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    1,
                )
            val finalization =
                PayrollFinalization(
                    id,
                    runId,
                    reviewId,
                    run.version,
                    review.version,
                    approval.version,
                    number,
                    job.id,
                    actor.accountId,
                    now,
                    reason,
                    issuer.code,
                    issuer.name,
                )
            val receipt = MutationReceipt(id, 0)
            jobs
                .create(job)
                .flatMap { finalizations.create(company, finalization) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "payroll_finalization",
                            id,
                            "payroll.finalization_started",
                            mapOf(
                                "runId" to runId.toString(),
                                "reviewId" to reviewId.toString(),
                                "jobId" to job.id.toString(),
                            ),
                            reason,
                        ),
                    )
                }
                .flatMap { operations.record(actor, key, receipt) }
                .map { receipt }
        }
    }
}
