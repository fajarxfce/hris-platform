package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class ResumeLeaveBatch(
    private val batches: LeaveBatchRepository,
    private val policies: LeavePolicyRepository,
    private val jobs: JobRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
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
        if (LeaveBatchKind.entries.none { it.permission in actor.permissions })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_batch"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "leave.batch_resume",
                operationId,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val original = batches.find(company, id)
            if (original is Result.Failed) return@run original
            val observed =
                (original as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_batch_not_found")
                    )
            val jobResult = jobs.find(company, observed.jobId, lock = true)
            if (jobResult is Result.Failed) return@run jobResult
            val previous =
                (jobResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_batch_job_obsolete")
                    )
            val found = batches.find(company, id, lock = true)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_batch_not_found")
                    )
            val batchLock = batches.lock(company)
            if (batchLock is Result.Failed) return@run batchLock
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock
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
            val permission = live.requirePermission(batch.kind.permission)
            if (permission is Result.Failed) return@run permission
            if (batch.actorId != actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "leave_batch_resume_owner_required")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (batch.version != version || batch.jobId != observed.jobId)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                batch.status == LeaveBatchStatus.COMPLETED ||
                    previous.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_not_stopped"))
            if (
                previous.request.kind != batch.kind.jobKind ||
                    previous.request.actorId != batch.actorId ||
                    previous.request.values["batchId"] != batch.id.toString()
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_job_obsolete"))
            val policy = policies.find(company, batch.typeId)
            if (policy is Result.Failed) return@run policy
            if ((policy as Result.Success).value?.version != batch.policyVersion)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_batch_policy_changed")
                )
            val active = batches.active(company, batch.typeId, batch.kind, batch.period)
            if (active is Result.Failed) return@run active
            if ((active as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_active"))
            val attemptResult = batches.attempts(company, id)
            if (attemptResult is Result.Failed) return@run attemptResult
            val attempts = (attemptResult as Result.Success).value
            if (attempts.size >= 8)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_attempt_limit"))
            val countResult = batches.counts(company, id)
            if (countResult is Result.Failed) return@run countResult
            val counts = (countResult as Result.Success).value
            if (counts.completed !in 0..batch.totalEmployees)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_batch_checkpoint_inconsistent")
                )
            val queueLock = jobs.lockQueue(company)
            if (queueLock is Result.Failed) return@run queueLock
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val now = clock.instant()
            val remaining = batch.totalEmployees - counts.completed
            val request =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    batch.kind.jobKind,
                    operationId,
                    mapOf("batchId" to batch.id.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    remaining + 1,
                )
            val attempt =
                LeaveBatchAttempt(request.id, attempts.size + 1, counts.completed, now, reason)
            jobs
                .create(request)
                .flatMap { batches.transition(company, batch, LeaveBatchStatus.RUNNING, attempt) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "leave_batch",
                                id,
                                "leave.batch_resumed",
                                mapOf(
                                    "jobId" to request.id.toString(),
                                    "remainingEmployees" to remaining.toString(),
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
