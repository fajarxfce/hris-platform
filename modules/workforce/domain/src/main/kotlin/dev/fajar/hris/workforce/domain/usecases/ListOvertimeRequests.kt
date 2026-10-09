package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
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
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
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
            if (
                live.permissions.none {
                    it in setOf("overtime.read", "overtime.manage", "workforce.read")
                }
            ) {
                if (employeeId == null)
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "employee_scope_required")
                    )
                val employeeResult = people.findAtInstant(company, employeeId, clock.instant())
                if (employeeResult is Result.Failed) return@run employeeResult
                if (!canReadOvertime(live, (employeeResult as Result.Success).value))
                    return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            }
            overtime.list(company, employeeId, from, until, status, after, limit)
        }
    }
}
