package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class AdvanceLeaveYearCloseBatch(
    private val batches: LeaveBatchRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val ledger: LeaveLedgerRepository,
    private val entitlements: LeaveEntitlementRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val requests: LeaveRequestRepository,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val kind = LeaveBatchKind.YEAR_CLOSE
        val scope = validateLeaveBatchJob(actor, lease, kind)
        if (scope is Result.Failed) return scope
        val request = lease.job.request
        val company = request.companyId
        return transactions.run(actor) {
            val locked = jobs.lockLease(lease)
            if (locked is Result.Failed) return@run locked
            val job =
                (locked as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )
            val found = batches.forJob(company, request.id, lock = true)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_batch_job_obsolete")
                    )
            if (batch.kind != kind || batch.status != LeaveBatchStatus.RUNNING)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_job_obsolete"))
            val countResult = batches.counts(company, batch.id)
            if (countResult is Result.Failed) return@run countResult
            val attempts = batches.attempts(company, batch.id)
            if (attempts is Result.Failed) return@run attempts
            val attempt =
                (attempts as Result.Success).value.singleOrNull { it.jobId == request.id }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_batch_job_obsolete")
                    )
            val counts = (countResult as Result.Success).value
            val consistent = validateLeaveBatchProgress(batch, job, counts, attempt)
            if (consistent is Result.Failed) return@run consistent
            val targetResult = batches.next(company, batch.id)
            if (targetResult is Result.Failed) return@run targetResult
            val target = (targetResult as Result.Success).value
            if (target != null) {
                val employeeLock = ledger.lock(company, target.employeeId)
                if (employeeLock is Result.Failed) return@run employeeLock
                val policyLock = policies.lock(company)
                if (policyLock is Result.Failed) return@run policyLock
            }
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
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
            val allowed = (checked as Result.Success).value.requirePermission(kind.permission)
            if (allowed is Result.Failed) return@run allowed
            if (target == null) {
                if (
                    counts.completed != batch.totalEmployees ||
                        job.completedItems != request.totalItems - 1
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_batch_checkpoint_inconsistent")
                    )
                return@run jobs
                    .checkpoint(lease, JobProgress(request.totalItems, emptyMap()))
                    .flatMap { moved ->
                        if (!moved)
                            return@flatMap Result.Failed(
                                Failure(FailureKind.CONFLICT, "job_lease_lost")
                            )
                        batches
                            .transition(company, batch, LeaveBatchStatus.COMPLETED)
                            .flatMap { jobs.complete(lease, JobStatus.SUCCEEDED) }
                            .flatMap { finished ->
                                if (!finished)
                                    Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                                else
                                    journal.record(
                                        actor,
                                        ChangeRecord(
                                            "leave_batch",
                                            batch.id,
                                            "leave.batch_completed",
                                            mapOf(
                                                "jobId" to request.id.toString(),
                                                "applied" to counts.applied.toString(),
                                                "unchanged" to counts.unchanged.toString(),
                                                "skipped" to counts.skipped.toString(),
                                                "failed" to counts.failed.toString(),
                                            ),
                                        ),
                                    )
                            }
                            .map { JobStep(request.totalItems, true) }
                    }
            }
            val currentPolicy = policies.find(company, batch.typeId)
            if (currentPolicy is Result.Failed) return@run currentPolicy
            if ((currentPolicy as Result.Success).value?.version != batch.policyVersion)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_batch_policy_changed")
                )
            val ownerResult = people.accountForEmployee(company, target.employeeId)
            if (ownerResult is Result.Failed) return@run ownerResult
            val versionResult = people.currentVersion(company, target.employeeId)
            if (versionResult is Result.Failed) return@run versionResult
            val employeeVersion = (versionResult as Result.Success).value
            val owner = (ownerResult as Result.Success).value
            val now = clock.instant()
            val closedResult =
                entitlements.closing(company, target.employeeId, batch.typeId, batch.period.year)
            if (closedResult is Result.Failed) return@run closedResult
            val closed = (closedResult as Result.Success).value
            val outcome: Result<LeaveBatchOutcome> =
                when {
                    owner == actor.accountId ->
                        Result.Success(
                            LeaveBatchOutcome(
                                LeaveBatchResultStatus.SKIPPED,
                                failure = Failure(FailureKind.FORBIDDEN, "self_adjustment_denied"),
                            )
                        )
                    employeeVersion == null ->
                        Result.Success(
                            LeaveBatchOutcome(
                                LeaveBatchResultStatus.FAILED,
                                failure = Failure(FailureKind.NOT_FOUND, "employee_not_found"),
                            )
                        )
                    closed != null ->
                        Result.Success(
                            LeaveBatchOutcome(LeaveBatchResultStatus.UNCHANGED, closed.id)
                        )
                    else -> {
                        val balanceResult =
                            ledger.balance(
                                company,
                                target.employeeId,
                                batch.typeId,
                                batch.period.year,
                            )
                        if (balanceResult is Result.Failed) return@run balanceResult
                        val balance = (balanceResult as Result.Success).value
                        val destinationResult =
                            ledger.balance(
                                company,
                                target.employeeId,
                                batch.typeId,
                                batch.period.year + 1,
                            )
                        if (destinationResult is Result.Failed) return@run destinationResult
                        val destination = (destinationResult as Result.Success).value
                        val pending =
                            requests.unresolvedYear(
                                company,
                                target.employeeId,
                                batch.typeId,
                                batch.period.year,
                            )
                        if (pending is Result.Failed) return@run pending
                        val rolloverResult =
                            calculateLeaveYearRollover(
                                balance,
                                batch.policy.policy.accrual?.carryLimitHalfDays ?: 0,
                                destination,
                                (pending as Result.Success).value,
                            )
                        if (rolloverResult is Result.Failed)
                            Result.Success(
                                LeaveBatchOutcome(
                                    LeaveBatchResultStatus.FAILED,
                                    failure = rolloverResult.failure,
                                )
                            )
                        else {
                            val rollover = (rolloverResult as Result.Success).value
                            val id = UUID.randomUUID()
                            val closing =
                                LeaveYearClosing(
                                    id,
                                    target.employeeId,
                                    batch.typeId,
                                    batch.period.year,
                                    balance.version,
                                    balance.availableHalfDays,
                                    balance.consumedHalfDays,
                                    destination.version,
                                    rollover,
                                    batch.policy,
                                    actor.accountId,
                                    now,
                                    batch.reason,
                                )
                            val entries = mutableListOf<LeaveLedgerEntry>()
                            if (rollover.carryHalfDays > 0) {
                                entries +=
                                    LeaveLedgerEntry(
                                        UUID.randomUUID(),
                                        target.employeeId,
                                        batch.typeId,
                                        batch.period.year,
                                        LeaveLedgerKind.CARRY_OUT,
                                        id,
                                        null,
                                        -rollover.carryHalfDays,
                                        0,
                                        0,
                                        actor.accountId,
                                        now,
                                        batch.reason,
                                    )
                                entries +=
                                    LeaveLedgerEntry(
                                        UUID.randomUUID(),
                                        target.employeeId,
                                        batch.typeId,
                                        batch.period.year + 1,
                                        LeaveLedgerKind.CARRY_IN,
                                        id,
                                        null,
                                        rollover.carryHalfDays,
                                        0,
                                        0,
                                        actor.accountId,
                                        now,
                                        batch.reason,
                                    )
                            }
                            if (rollover.expireHalfDays > 0)
                                entries +=
                                    LeaveLedgerEntry(
                                        UUID.randomUUID(),
                                        target.employeeId,
                                        batch.typeId,
                                        batch.period.year,
                                        LeaveLedgerKind.EXPIRE,
                                        id,
                                        null,
                                        -rollover.expireHalfDays,
                                        0,
                                        0,
                                        actor.accountId,
                                        now,
                                        batch.reason,
                                    )
                            val sourceMovements = entries.count { it.year == batch.period.year }
                            entitlements
                                .createClosing(company, closing)
                                .flatMap {
                                    if (entries.isEmpty()) Result.Success(Unit)
                                    else ledger.append(company, entries)
                                }
                                .flatMap {
                                    entitlements.closeAccount(
                                        company,
                                        target.employeeId,
                                        batch.typeId,
                                        batch.period.year,
                                        balance.version + sourceMovements,
                                        id,
                                    )
                                }
                                .flatMap {
                                    journal.record(
                                        actor,
                                        ChangeRecord(
                                            "leave_year_closing",
                                            id,
                                            "leave.year_closed",
                                            mapOf(
                                                "batchId" to batch.id.toString(),
                                                "employeeId" to target.employeeId.toString(),
                                                "typeId" to batch.typeId.toString(),
                                                "year" to batch.period.year.toString(),
                                            ),
                                            batch.reason,
                                        ),
                                    )
                                }
                                .map { LeaveBatchOutcome(LeaveBatchResultStatus.APPLIED, id) }
                        }
                    }
                }

            outcome
                .flatMap { result ->
                    batches.outcome(
                        company,
                        batch,
                        LeaveBatchResult(
                            target.ordinal,
                            target.employeeId,
                            request.id,
                            result.status,
                            result.resourceId,
                            result.failure?.code,
                            result.failure?.parameters ?: emptyMap(),
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
