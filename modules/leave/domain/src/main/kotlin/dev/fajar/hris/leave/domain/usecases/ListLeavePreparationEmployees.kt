package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.EmployeeVisibility
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ListLeavePreparationEmployees(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        query: String,
        after: String?,
        limit: Int,
    ): Result<LeavePreparationDirectory> {
        val permission = actor.requirePermission("leave.manage")
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
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
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val admitted = live.requirePermission("leave.manage")
            if (admitted is Result.Failed) return@run admitted
            val settings = companies.find(company)
            if (settings is Result.Failed) return@run settings
            val current =
                (settings as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = LocalDate.ofInstant(now, ZoneId.of(current.timezone))
            val acquired =
                people.list(
                    company,
                    live.accountId,
                    EmployeeVisibility.ALL,
                    today,
                    now,
                    query.trim(),
                    after,
                    limit,
                )
            if (acquired is Result.Failed) return@run acquired
            val page = (acquired as Result.Success).value
            val result =
                LeavePreparationDirectory(
                    today,
                    current.timezone,
                    Page(
                        page.items.map {
                            LeavePreparationEmployee(
                                it.id,
                                it.employeeNumber,
                                it.person.legalName,
                                it.terms.status,
                                it.version,
                            )
                        },
                        page.nextCursor,
                    ),
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { result }
        }
    }
}
