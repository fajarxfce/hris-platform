package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.canReadLeaveAccount
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetLeaveAccount(
    private val ledger: LeaveLedgerRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<LeaveAccount> {
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val found = ledger.account(company, id)
            if (found is Result.Failed) return@run found
            val employeeId =
                (found as Result.Success).value?.employeeId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_account_not_found")
                    )
            val lock = ledger.lock(company, employeeId, shared = true)
            if (lock is Result.Failed) return@run lock
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
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val currentResult = people.findAtInstant(company, employeeId, clock.instant())
            if (currentResult is Result.Failed) return@run currentResult
            val ownerResult = people.accountForEmployee(company, employeeId)
            if (ownerResult is Result.Failed) return@run ownerResult
            if (
                !canReadLeaveAccount(
                    live,
                    (currentResult as Result.Success).value,
                    (ownerResult as Result.Success).value,
                )
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_account_not_found"))
            ledger.account(company, id).flatMap { value ->
                value?.let { Result.Success(it) }
                    ?: Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_account_not_found"))
            }
        }
    }
}
