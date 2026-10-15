package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobKind
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.availableLeaveCarryExpiryActions
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetLeaveCarryExpiry(
    private val expiries: LeaveCarryExpiryRepository,
    private val jobs: JobRepository,
    private val batches: LeaveBatchRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID, after: Int?, limit: Int): Result<LeaveCarryExpiryDetails> {
        val permission = actor.requirePermission("leave.read")
        if (permission is Result.Failed) return permission
        if (limit !in 1..100 || (after != null && after !in 1..5000))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val observedResult = expiries.find(company, id)
            if (observedResult is Result.Failed) return@run observedResult
            val observed = (observedResult as Result.Success).value
            val jobResult =
                if (observed == null) Result.Success(null)
                else jobs.find(company, observed.jobId, lock = true)
            if (jobResult is Result.Failed) return@run jobResult
            val found = expiries.find(company, id, lock = true)
            if (found is Result.Failed) return@run found
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
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
            val live = (checked as Result.Success).value
            val allowed = live.requirePermission("leave.read")
            if (allowed is Result.Failed) return@run allowed
            val expiry =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_carry_expiry_not_found")
                    )
            if (expiry.jobId != observed?.jobId)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_carry_expiry_changed")
                )
            val job =
                (jobResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "leave_carry_expiry_job_obsolete")
                    )
            if (
                job.request.companyId != company ||
                    job.request.id != expiry.jobId ||
                    job.request.kind != JobKind.LEAVE_CARRY_EXPIRY ||
                    job.request.values != mapOf("expiryId" to id.toString())
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_carry_expiry_job_obsolete")
                )
            val overviewResult = expiries.overview(company, id)
            if (overviewResult is Result.Failed) return@run overviewResult
            val overview = (overviewResult as Result.Success).value
            if (overview == null || overview.expiry != expiry || overview.status != job.status)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_carry_expiry_changed")
                )
            val attempts = expiries.attempts(company, id)
            if (attempts is Result.Failed) return@run attempts
            val results = expiries.results(company, id, after, limit)
            if (results is Result.Failed) return@run results
            val outcomes = (results as Result.Success).value
            val employees = batches.employees(company, outcomes.items.map { it.employeeId }.toSet())
            if (employees is Result.Failed) return@run employees
            val details =
                LeaveCarryExpiryDetails(
                    overview,
                    (attempts as Result.Success).value,
                    job,
                    outcomes,
                    java.util.List.copyOf((employees as Result.Success).value),
                    availableLeaveCarryExpiryActions(live, overview),
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { details }
        }
    }
}
