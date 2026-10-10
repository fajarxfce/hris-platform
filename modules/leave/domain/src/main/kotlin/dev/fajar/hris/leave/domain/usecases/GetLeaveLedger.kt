package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.LeaveLedger
import dev.fajar.hris.leave.domain.policies.canReadLeaveAccount
import dev.fajar.hris.leave.domain.repositories.LeaveLedgerRepository
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class GetLeaveLedger(
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        after: UUID?,
        limit: Int,
    ): Result<LeaveLedger> {
        if (year !in 1900..2200 || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
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
            val type = policies.find(company, typeId)
            if (type is Result.Failed) return@run type
            if ((type as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_type_not_found"))
            val ownerResult = people.accountForEmployee(company, employeeId)
            if (ownerResult is Result.Failed) return@run ownerResult
            val currentResult = people.findAtInstant(company, employeeId, clock.instant())
            if (currentResult is Result.Failed) return@run currentResult
            if (
                !canReadLeaveAccount(
                    live,
                    (currentResult as Result.Success).value,
                    (ownerResult as Result.Success).value,
                )
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val exists = people.currentVersion(company, employeeId)
            if (exists is Result.Failed) return@run exists
            if ((exists as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            ledger.balance(company, employeeId, typeId, year).flatMap { balance ->
                ledger.entries(company, employeeId, typeId, year, after, limit).map {
                    LeaveLedger(balance, it)
                }
            }
        }
    }
}
