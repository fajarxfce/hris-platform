package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollPolicyReview
import dev.fajar.hris.payroll.domain.policies.canReadPayrollPolicy
import dev.fajar.hris.payroll.domain.repositories.PayrollPolicyRepository
import java.time.Clock
import java.time.YearMonth

class GetPayrollPolicyReview(
    private val policies: PayrollPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        month: YearMonth,
        historyAfter: Long?,
        historyLimit: Int,
    ): Result<PayrollPolicyReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollPolicy(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (month.year !in 2024..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_month"))
        if (historyLimit !in 1..200 || (historyAfter != null && historyAfter !in 0..999))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val access = (accessed as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if (!canReadPayrollPolicy((checked as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = policies.current(company)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            if (historyAfter != null && (current == null || historyAfter > current.version))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            val selected = policies.effective(company, month)
            if (selected is Result.Failed) return@run selected
            val effective =
                (selected as Result.Success).value?.takeIf {
                    month >= it.effectiveFrom && month <= it.effectiveUntil
                }
            policies.history(company, historyAfter, historyLimit).flatMap { history ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map {
                    PayrollPolicyReview(month, current, effective, history)
                }
            }
        }
    }
}
