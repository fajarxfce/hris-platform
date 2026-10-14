package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetPayrollReviewPreparation(
    private val reviews: PayrollReviewRepository,
    private val runs: PayrollRunRepository,
    private val policies: PayrollPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, runId: UUID): Result<PayrollReviewPreparation> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollInput(actor))
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
            val live = (checked as Result.Success).value
            if (!canReadPayrollInput(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = runs.find(company, runId)
            if (found is Result.Failed) return@run found
            val run =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_run_not_found")
                    )
            val latest = reviews.latest(company, runId)
            if (latest is Result.Failed) return@run latest
            val sum = reviews.totals(company, runId)
            if (sum is Result.Failed) return@run sum
            val totals = (sum as Result.Success).value
            val previous = (latest as Result.Success).value
            val valid = validatePayrollReviewSubmission(run, previous, totals)
            val issues = if (valid is Result.Failed) listOf(valid.failure) else emptyList()
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                PayrollReviewPreparation(
                    run,
                    totals,
                    previous,
                    issues,
                    if ("payroll.calculate" in live.permissions && issues.isEmpty())
                        setOf(PayrollReviewAvailableAction.SUBMIT)
                    else emptySet(),
                )
            }
        }
    }
}
