package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class GetEmployeeCompensation(
    private val policies: PayrollPolicyRepository,
    private val compensations: CompensationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, employeeId: UUID, month: YearMonth): Result<EmployeeCompensation> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (month.year !in 2024..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_month"))
        return transactions.run(actor) {
            val policyLock = policies.lock(company, shared = true)
            if (policyLock is Result.Failed) return@run policyLock

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
            val live = (checked as Result.Success).value

            if (!canReadCompensation(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            compensations.effective(company, employeeId, month).flatMap { value ->
                if (value == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "compensation_not_found"))
                else Result.Success(value)
            }
        }
    }
}
