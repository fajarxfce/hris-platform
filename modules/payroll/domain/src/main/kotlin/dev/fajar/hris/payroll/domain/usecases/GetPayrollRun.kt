package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class GetPayrollRun(
    private val runs: PayrollRunRepository,
    private val policies: PayrollPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val jobs: JobRepository,
    private val periods: PayrollPeriodRepository,
    private val reviews: PayrollReviewRepository,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID, after: Int?, limit: Int): Result<PayrollRunDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollInput(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if ((after ?: 0) !in 0..5000 || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val guard = policies.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard

            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadPayrollInput(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))

            val found = runs.find(company, id)
            if (found is Result.Failed) return@run found
            val run =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_run_not_found")
                    )
            val jobResult = jobs.find(company, run.jobId)
            if (jobResult is Result.Failed) return@run jobResult
            val job =
                (jobResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_run_job_obsolete")
                    )
            val attempts = runs.attempts(company, id)
            if (attempts is Result.Failed) return@run attempts
            val periodResult = periods.find(company, run.periodId)
            if (periodResult is Result.Failed) return@run periodResult
            val period =
                (periodResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_period_not_found")
                    )
            val reviewResult = reviews.latest(company, id)
            if (reviewResult is Result.Failed) return@run reviewResult
            val history = (attempts as Result.Success).value
            runs.results(company, id, after, limit).flatMap { results ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map {
                    PayrollRunDetails(
                        run,
                        history,
                        job,
                        results,
                        period,
                        availablePayrollCalculationActions(
                            live,
                            run,
                            period,
                            job,
                            history.size,
                            (reviewResult as Result.Success).value,
                        ),
                    )
                }
            }
        }
    }
}
