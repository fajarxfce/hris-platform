package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.EmployeeVisibility
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.canReadOwnWorkforceEmployments
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ListSelfWorkforceEmployments(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, after: String?, limit: Int): Result<SelfWorkforceDirectory> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadOwnWorkforceEmployments(actor.permissions))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..50 || (after != null && (after.isBlank() || after.length > 32)))
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
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadOwnWorkforceEmployments(live.permissions))
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
                    .flatMap { page ->
                        validateCompanySessionActor(actor, access, clock.instant(), security).map {
                            SelfWorkforceDirectory(
                                live.accountId,
                                company,
                                today,
                                current.timezone,
                                Page(
                                    page.items.map { employee ->
                                        WorkforceEmployment(
                                            employee.id,
                                            employee.employeeNumber,
                                            employee.terms.status,
                                            employee.version,
                                        )
                                    },
                                    page.nextCursor,
                                ),
                            )
                        }
                    }
            }
        }
    }
}
