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

class GetPayrollFinalization(
    private val finalizations: PayrollFinalizationRepository,
    private val policies: PayrollPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val jobs: JobRepository,
    private val runs: PayrollRunRepository,
    private val reviews: PayrollReviewRepository,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<PayrollFinalizationDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollFinalization(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        return transactions.run(actor) {
            val guard = policies.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
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
            if (!canReadPayrollFinalization((checked as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = finalizations.find(company, id)
            if (found is Result.Failed) return@run found
            val finalization =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_finalization_not_found")
                    )
            val jobResult = jobs.find(company, finalization.jobId)
            if (jobResult is Result.Failed) return@run jobResult
            val job = (jobResult as Result.Success).value
            if (
                job == null ||
                    job.request.companyId != company ||
                    job.request.kind != JobKind.PAYROLL_FINALIZE ||
                    job.request.values["finalizationId"] != id.toString() ||
                    job.request.values["runId"] != finalization.runId.toString()
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete")
                )
            val runResult = runs.find(company, finalization.runId)
            if (runResult is Result.Failed) return@run runResult
            val reviewResult = reviews.find(company, finalization.reviewId)
            if (reviewResult is Result.Failed) return@run reviewResult
            val run = (runResult as Result.Success).value
            val review = (reviewResult as Result.Success).value
            if (run == null || review == null || review.runId != run.id)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete")
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                PayrollFinalizationDetails(finalization, job, run, review.totals)
            }
        }
    }
}
