package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollEmployee
import dev.fajar.hris.payroll.domain.policies.canReadCompensation
import dev.fajar.hris.payroll.domain.repositories.CompensationRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock

class ListCompensationEmployees(
    private val compensations: CompensationRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<PayrollEmployee>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadCompensation(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (
            limit !in 1..50 ||
                query.length > 120 ||
                (after != null && (after.isBlank() || after.length > 32))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val found = identities.access(actor.accountId, company)
            if (found is Result.Failed) return@run found
            val access = (found as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if (!canReadCompensation((checked as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            compensations.employees(company, query.trim(), after, limit).flatMap { page ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map { page }
            }
        }
    }
}
