package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.LeaveCarryExpiryRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

/** Recovery replaces only the failed job; original awards, targets and deadlines remain fixed. */
class ResumeLeaveCarryExpiry(
    private val expiries: LeaveCarryExpiryRepository,
    private val jobs: JobRepository,
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
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission("leave.year.close")
        if (permission is Result.Failed) return permission
        if (version !in 0..7 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_leave_carry_expiry_recovery")
            )
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.carry_expiry_resume",
                operationId,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val observedResult = expiries.find(company, id)
            if (observedResult is Result.Failed) return@run observedResult
            val observed = (observedResult as Result.Success).value
            val previousResult =
                if (observed == null) Result.Success(null)
                else jobs.find(company, observed.jobId, lock = true)
            if (previousResult is Result.Failed) return@run previousResult
            val previous = (previousResult as Result.Success).value
            val found = expiries.find(company, id, lock = true)
            if (found is Result.Failed) return@run found
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val allowed = (checked as Result.Success).value.requirePermission("leave.year.close")
            if (allowed is Result.Failed) return@run allowed
            val expiry =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_carry_expiry_not_found")
                    )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (expiry.version != version || expiry.jobId != observed?.jobId)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous == null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_carry_expiry_job_obsolete")
                )
            if (previous.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED))
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_carry_expiry_not_stopped")
                )
            if (expiry.version >= 7)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_carry_expiry_attempt_limit")
                )
            val attemptsResult = expiries.attempts(company, id)
            if (attemptsResult is Result.Failed) return@run attemptsResult
            val attempt =
                (attemptsResult as Result.Success).value.singleOrNull {
                    it.jobId == previous.request.id
                }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_carry_expiry_job_obsolete")
                    )
            val completedResult = expiries.completed(company, id)
            if (completedResult is Result.Failed) return@run completedResult
            val completed = (completedResult as Result.Success).value
            val checkpoint =
                validateLeaveCarryExpiryCheckpoint(expiry, previous, attempt, completed)
            if (checkpoint is Result.Failed) return@run checkpoint
            val queueGuard = jobs.lockQueue(company)
            if (queueGuard is Result.Failed) return@run queueGuard
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val assured = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (assured is Result.Failed) return@run assured
            val now = clock.instant()
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.LEAVE_CARRY_EXPIRY,
                    operationId,
                    mapOf("expiryId" to id.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    expiry.totalEmployees - completed + 1,
                    scheduledFor = maxOf(expiry.validity.expiresAt, now),
                )
            val recovery =
                LeaveCarryExpiryAttempt(job.id, expiry.version.toInt() + 2, completed, now, reason)
            jobs
                .create(job)
                .flatMap { expiries.resume(company, expiry, recovery) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "leave_carry_expiry",
                                id,
                                "leave.carry_expiry_resumed",
                                mapOf(
                                    "jobId" to job.id.toString(),
                                    "remainingEmployees" to
                                        (expiry.totalEmployees - completed).toString(),
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                                .map { receipt }
                        }
                }
        }
    }
}
