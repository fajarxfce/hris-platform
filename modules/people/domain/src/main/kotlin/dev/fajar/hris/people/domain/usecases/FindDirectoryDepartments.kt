package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class FindDirectoryDepartments(
    private val directory: CompanyDirectoryRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, query: String, limit: Int): Result<DirectoryDepartments> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (actor.permissions.none { it == "people.directory.read" || it == "people.read" })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..50 || query.length > 120)
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
            val identityAccess = (authority as Result.Success).value
            val checked =
                validateCompanySessionActor(actor, identityAccess, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (live.permissions.none { it == "people.directory.read" || it == "people.read" })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            companies
                .find(company)
                .flatMap { current ->
                    if (current == null)
                        return@flatMap Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "company_not_found")
                        )
                    val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(current.timezone))
                    directory.departments(
                        company,
                        today,
                        setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION),
                        query.trim(),
                        limit,
                    )
                }
                .flatMap { acquired ->
                    validateCompanySessionActor(actor, identityAccess, clock.instant(), security)
                        .map { acquired }
                }
        }
    }
}
