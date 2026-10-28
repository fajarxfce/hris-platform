package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class ListOvertimeRequests(
    private val overtime: OvertimeRepository,
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
        employeeId: UUID?,
        from: LocalDate,
        until: LocalDate,
        status: OvertimeStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<OvertimeRequest>> {
        if (
            limit !in 1..200 ||
                from.year !in 2000..2100 ||
                until.year !in 2000..2100 ||
                until < from ||
                java.time.temporal.ChronoUnit.DAYS.between(from, until) >= 62
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadCompanyOvertime(live)) {
                if (employeeId == null)
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "employee_scope_required")
                    )
                val employeeResult = people.findAtInstant(company, employeeId, clock.instant())
                if (employeeResult is Result.Failed) return@run employeeResult
                if (!canReadOvertime(live, (employeeResult as Result.Success).value))
                    return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            }
            overtime.list(company, employeeId, from, until, status, after, limit).flatMap { page ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map { page }
            }
        }
    }
}
