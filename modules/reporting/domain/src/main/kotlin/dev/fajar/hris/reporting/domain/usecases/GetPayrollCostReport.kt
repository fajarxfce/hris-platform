package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.PayrollCostReport
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.PayrollCostReportRepository
import java.time.Clock
import java.time.YearMonth

class GetPayrollCostReport(
    private val reports: PayrollCostReportRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, from: YearMonth, until: YearMonth): Result<PayrollCostReport> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validatePayrollCostReportAccess(actor)
        if (access is Result.Failed) return access
        val range = validatePayrollCostReportRange(from, until)
        if (range is Result.Failed) return range
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val current = identities.access(actor.accountId, company)
            if (current is Result.Failed) return@run current
            val snapshot = (current as Result.Success).value
            val admitted =
                validateCompanySessionActor(actor, snapshot, clock.instant(), security)
                    .flatMap(::validatePayrollCostReportAccess)
            if (admitted is Result.Failed) return@run admitted
            val evaluatedAt = clock.instant()
            reports.aggregate(company, from, until).flatMap { aggregate ->
                validateCompanySessionActor(actor, snapshot, clock.instant(), security).map {
                    PayrollCostReport(company, from, until, evaluatedAt, aggregate)
                }
            }
        }
    }
}
