package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class GetPayrollTaxOpening(
    private val openings: PayrollTaxOpeningRepository,
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
        employee: UUID,
        year: Int,
        revision: Long? = null,
    ): Result<PayrollTaxOpening> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollTaxOpening(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (year !in 2024..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_tax_opening"))
        if (revision != null && revision !in 0..999)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val policyLock = policies.lock(company, shared = true)
            if (policyLock is Result.Failed) return@run policyLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val accessResult = identities.access(actor.accountId, company)
            if (accessResult is Result.Failed) return@run accessResult
            val access = (accessResult as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if (!canReadPayrollTaxOpening((checked as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found =
                if (revision == null) openings.find(company, employee, year)
                else openings.revision(company, employee, year, revision)
            found
                .flatMap {
                    if (it == null)
                        Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "payroll_tax_opening_not_found")
                        )
                    else Result.Success(it)
                }
                .flatMap { opening ->
                    validateCompanySessionActor(actor, access, clock.instant(), security).map {
                        opening
                    }
                }
        }
    }
}
