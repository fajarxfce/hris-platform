package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class ListLifecycleCases(
    private val lifecycle: LifecycleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID?,
        status: LifecycleStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<LifecycleCaseDetails>> {
        val access = actor.requirePermission("people.lifecycle.read")
        if (access is Result.Failed) return access
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
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
            val permission = live.requirePermission("people.lifecycle.read")
            if (permission is Result.Failed) return@run permission
            lifecycle.cases(company, employeeId, status, after, limit)
        }
    }
}
