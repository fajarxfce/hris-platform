package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import java.time.Clock
import java.util.UUID

/**
 * Settles an already fixed award; a verified lease authorizes only its original deadline/cohort.
 */
class AdvanceLeaveCarryExpiry(
    private val expiries: LeaveCarryExpiryRepository,
    private val jobs: JobRepository,
    private val carry: LeaveCarryRepository,
    private val ledger: LeaveLedgerRepository,
    private val entitlements: LeaveEntitlementRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        if (request.kind != JobKind.LEAVE_CARRY_EXPIRY)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val company = request.companyId
        val actor =
            Actor(
                request.actorId,
                company,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
            )
        return transactions.run(actor) {
            val locked = jobs.lockLease(lease)
            if (locked is Result.Failed) return@run locked
            val job =
                (locked as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.request != request || job.cancellationRequested)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
            val found = expiries.forJob(company, request.id, lock = true)
            if (found is Result.Failed) return@run found
            val expiry =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_carry_expiry_job_obsolete")
                    )
            val attempts = expiries.attempts(company, expiry.id)
            if (attempts is Result.Failed) return@run attempts
            val attempt =
                (attempts as Result.Success).value.singleOrNull { it.jobId == request.id }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_carry_expiry_job_obsolete")
                    )
            val counted = expiries.completed(company, expiry.id)
            if (counted is Result.Failed) return@run counted
            val completed = (counted as Result.Success).value
            val valid =
                validateLeaveCarryExpiryProgress(expiry, job, attempt, completed, clock.instant())
            if (valid is Result.Failed) return@run valid
            val selected = expiries.next(company, expiry.id, completed)
            if (selected is Result.Failed) return@run selected
            val target = (selected as Result.Success).value
            if (target == null) {
                if (completed != expiry.totalEmployees)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_carry_expiry_checkpoint_inconsistent")
                    )
                return@run jobs
                    .checkpoint(lease, JobProgress(request.totalItems, emptyMap()))
                    .flatMap { moved ->
                        if (!moved)
                            return@flatMap Result.Failed(
                                Failure(FailureKind.CONFLICT, "job_lease_lost")
                            )
                        jobs
                            .complete(lease, JobStatus.SUCCEEDED)
                            .flatMap { finished ->
                                if (!finished)
                                    Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                                else
                                    journal.record(
                                        actor,
                                        ChangeRecord(
                                            "leave_carry_expiry",
                                            expiry.id,
                                            "leave.carry_expiry_completed",
                                            mapOf(
                                                "jobId" to request.id.toString(),
                                                "employees" to expiry.totalEmployees.toString(),
                                            ),
                                        ),
                                    )
                            }
                            .map { JobStep(request.totalItems, true) }
                    }
            }
            val guard = ledger.lock(company, target.employeeId)
            if (guard is Result.Failed) return@run guard
            val acquiredClosing =
                entitlements.closing(company, target.employeeId, expiry.typeId, expiry.year - 1)
            if (acquiredClosing is Result.Failed) return@run acquiredClosing
            val closing = (acquiredClosing as Result.Success).value
            val acquiredCarry =
                carry.buckets(company, target.employeeId, expiry.typeId, setOf(expiry.year))
            if (acquiredCarry is Result.Failed) return@run acquiredCarry
            val bucket = (acquiredCarry as Result.Success).value.singleOrNull()
            val now = clock.instant()
            val due = validateLeaveCarryExpiryProgress(expiry, job, attempt, completed, now)
            if (due is Result.Failed) return@run due
            val owned = belongsToLeaveCarryExpiry(expiry, target.employeeId, closing, bucket)
            val planned =
                if (owned) planLeaveCarryExpiry(requireNotNull(bucket), now)
                else Result.Success(null)
            if (planned is Result.Failed) return@run planned
            val movement = (planned as Result.Success).value
            if (movement != null) {
                val applied = applyLeaveCarryMovement(requireNotNull(bucket), movement)
                if (applied is Result.Failed) return@run applied
            }
            val posting =
                movement?.let {
                    LeaveCarryPosting(
                        UUID.randomUUID(),
                        it,
                        requireNotNull(bucket).version,
                        UUID.randomUUID(),
                    )
                }
            val entry =
                posting?.let {
                    LeaveLedgerEntry(
                        it.ledgerEntryId,
                        target.employeeId,
                        expiry.typeId,
                        expiry.year,
                        LeaveLedgerKind.EXPIRE,
                        it.id,
                        null,
                        -it.movement.halfDays,
                        0,
                        0,
                        actor.accountId,
                        now,
                        "Carry validity ended",
                        it.id,
                    )
                }
            val effect =
                if (posting == null) Result.Success(Unit)
                else
                    carry.append(company, listOf(posting)).flatMap {
                        ledger.append(company, listOf(requireNotNull(entry)))
                    }
            effect
                .flatMap {
                    expiries.outcome(
                        company,
                        expiry.id,
                        LeaveCarryExpiryOutcome(
                            target.ordinal,
                            target.employeeId,
                            request.id,
                            if (!owned) LeaveCarryExpiryOutcomeStatus.NO_AWARD
                            else if (movement == null) LeaveCarryExpiryOutcomeStatus.UNCHANGED
                            else LeaveCarryExpiryOutcomeStatus.EXPIRED,
                            movement?.halfDays ?: 0,
                            entry?.id,
                            now,
                        ),
                    )
                }
                .flatMap {
                    val progress = JobProgress(job.completedItems + 1, emptyMap())
                    jobs.checkpoint(lease, progress).flatMap { changed ->
                        if (changed) Result.Success(JobStep(progress.completedItems, false))
                        else Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                    }
                }
        }
    }
}
