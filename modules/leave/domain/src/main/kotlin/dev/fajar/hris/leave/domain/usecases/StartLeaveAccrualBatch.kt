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

class StartLeaveAccrualBatch(
    private val batches: LeaveBatchRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        typeId: UUID,
        month: YearMonth,
        expectedPolicyVersion: Long,
        employeeIds: Set<UUID>?,
        reason: String,
    ): Result<MutationReceipt> {
        val kind = LeaveBatchKind.ACCRUAL
        val permission = actor.requirePermission(kind.permission)
        if (permission is Result.Failed) return permission
        val period = month
        val valid = validateLeaveBatchInput(period, expectedPolicyVersion, employeeIds, reason)
        if (valid is Result.Failed) return valid
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.batch_accrual",
                operationId,
                listOf(
                    id.toString(),
                    typeId.toString(),
                    period.toString(),
                    expectedPolicyVersion.toString(),
                    reason,
                    employeeIds?.map { it.toString() }?.sorted()?.joinToString(","),
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val batchLock = batches.lock(company)
            if (batchLock is Result.Failed) return@run batchLock
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock
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
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val settingsResult = companies.find(company)
            if (settingsResult is Result.Failed) return@run settingsResult
            val settings =
                (settingsResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val typeResult = policies.effective(company, typeId, period.atDay(1))
            if (typeResult is Result.Failed) return@run typeResult
            val type =
                (typeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                    )
            if (type.version != expectedPolicyVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_policy_version"))
            val accrual =
                type.policy.accrual
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_accrual_not_configured")
                    )
            if (!type.active)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
            val validPolicy = validateLeaveAccrualPolicy(accrual, type.policy.allowPartialDays)
            if (validPolicy is Result.Failed) return@run validPolicy
            val due = validateLeaveAccrualPeriod(period, today, accrual.frequency)
            if (due is Result.Failed) return@run due
            val active = batches.active(company, typeId, kind, period)
            if (active is Result.Failed) return@run active
            if ((active as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_active"))
            val selected =
                if (employeeIds == null) people.employeeIds(company, 5001)
                else people.existingEmployeeIds(company, employeeIds).map { it.toList() }
            if (selected is Result.Failed) return@run selected
            val ids = (selected as Result.Success).value
            if (employeeIds != null && ids.toSet() != employeeIds)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "leave_batch_employee_unavailable")
                )
            if (ids.size !in 1..5000)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "leave_batch_employee_limit")
                )
            val queueLock = jobs.lockQueue(company)
            if (queueLock is Result.Failed) return@run queueLock
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val request =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    kind.jobKind,
                    operationId,
                    mapOf("batchId" to id.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    ids.size + 1,
                )
            val batch =
                LeaveBatch(
                    id,
                    kind,
                    typeId,
                    period,
                    LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                    type.version,
                    settings.timezone,
                    actor.accountId,
                    now,
                    reason,
                    ids.size,
                    request.id,
                    LeaveBatchStatus.RUNNING,
                    0,
                )
            val targets =
                ids.sortedBy { it.toString() }
                    .mapIndexed { ordinal, employeeId -> LeaveBatchTarget(ordinal + 1, employeeId) }
            val attempt = LeaveBatchAttempt(request.id, 1, 0, now, reason)
            val receipt = MutationReceipt(id, 0)
            jobs
                .create(request)
                .flatMap { batches.create(company, batch, targets, attempt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "leave_batch",
                            id,
                            "leave.batch_started",
                            mapOf(
                                "kind" to kind.name,
                                "period" to period.toString(),
                                "employees" to ids.size.toString(),
                                "jobId" to request.id.toString(),
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
