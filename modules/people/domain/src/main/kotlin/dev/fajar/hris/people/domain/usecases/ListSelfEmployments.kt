package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.canReadOwnEmployment
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Always selects the signed-in person's employments, including for company administrators. */
class ListSelfEmployments(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, after: String?, limit: Int): Result<SelfEmploymentDirectory> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadOwnEmployment(actor.permissions))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..50 || (after?.length ?: 0) > 32)
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
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadOwnEmployment(live.permissions))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            companies.find(company).flatMap { current ->
                if (current == null)
                    return@flatMap Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "company_not_found")
                    )
                val now = clock.instant()
                val today = LocalDate.ofInstant(now, ZoneId.of(current.timezone))
                people
                    .list(
                        company,
                        live.accountId,
                        EmployeeVisibility.SELF,
                        today,
                        now,
                        "",
                        after,
                        limit,
                    )
                    .map { SelfEmploymentDirectory(today, current.timezone, it) }
            }
        }
    }
}
