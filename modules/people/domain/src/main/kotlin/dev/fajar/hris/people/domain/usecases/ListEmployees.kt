package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.LocalDate

class ListEmployees(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        asOf: LocalDate,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<Employee>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val initial = employeeVisibility(actor.permissions)
        if (initial is Result.Failed) return initial
        if (limit !in 1..200 || query.length > 120 || (after?.length ?: 0) > 32)
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
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val visibility = employeeVisibility(live.permissions)
            if (visibility is Result.Failed) return@run visibility
            people.list(
                company,
                actor.accountId,
                (visibility as Result.Success).value,
                asOf,
                clock.instant(),
                query.trim(),
                after,
                limit,
            )
        }
    }
}
