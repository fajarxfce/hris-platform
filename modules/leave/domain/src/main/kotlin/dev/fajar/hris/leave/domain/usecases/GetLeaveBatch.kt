package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.availableLeaveBatchActions
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetLeaveBatch(
    private val batches: LeaveBatchRepository,
    private val jobs: JobRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID, after: Int?, limit: Int): Result<LeaveBatchDetails> {
        val access = actor.requirePermission("leave.read")
        if (access is Result.Failed) return access
        if ((after ?: 0) !in 0..5000 || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val original = batches.find(company, id)
            if (original is Result.Failed) return@run original
            val observed =
                (original as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_batch_not_found")
                    )
            val jobResult = jobs.find(company, observed.jobId, lock = true)
            if (jobResult is Result.Failed) return@run jobResult
            val job =
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
            val policyLock = policies.lock(company, shared = true)
            if (policyLock is Result.Failed) return@run policyLock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = live.requirePermission("leave.read")
            if (allowed is Result.Failed) return@run allowed
            if (batch.jobId != observed.jobId)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_changed"))
            val countResult = batches.counts(company, id)
            if (countResult is Result.Failed) return@run countResult
            val attemptResult = batches.attempts(company, id)
            if (attemptResult is Result.Failed) return@run attemptResult
            val acquired = batches.results(company, id, after, limit)
            if (acquired is Result.Failed) return@run acquired
            val results = (acquired as Result.Success).value
            val employees = batches.employees(company, results.items.map { it.employeeId }.toSet())
            if (employees is Result.Failed) return@run employees
            val policy = policies.find(company, batch.typeId)
            if (policy is Result.Failed) return@run policy
            val active = batches.active(company, batch.typeId, batch.kind, batch.period)
            if (active is Result.Failed) return@run active
            val attempts = (attemptResult as Result.Success).value
            val details =
                LeaveBatchDetails(
                    batch,
                    (countResult as Result.Success).value,
                    attempts,
                    job,
                    results,
                    (employees as Result.Success).value,
                    availableLeaveBatchActions(
                        live,
                        batch,
                        job,
                        attempts,
                        (policy as Result.Success).value?.version,
                        (active as Result.Success).value,
                    ),
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { details }
        }
    }
}
