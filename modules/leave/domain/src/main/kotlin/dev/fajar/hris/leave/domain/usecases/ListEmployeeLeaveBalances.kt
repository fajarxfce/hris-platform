package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.EmployeeLeaveBalances
import dev.fajar.hris.leave.domain.entities.LeaveEmployeeReference
import dev.fajar.hris.leave.domain.policies.canReadLeaveAccount
import dev.fajar.hris.leave.domain.repositories.LeaveLedgerRepository
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class ListEmployeeLeaveBalances(
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
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
        employeeId: UUID,
        year: Int,
        after: String?,
        limit: Int,
    ): Result<EmployeeLeaveBalances> {
        if (
            year !in 1900..2200 ||
                limit !in 1..200 ||
                (after != null && !after.matches(Regex("[A-Z][A-Z0-9_-]{0,31}")))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val lock = ledger.lock(company, employeeId, shared = true)
            if (lock is Result.Failed) return@run lock
            val policyLock = policies.lock(company, shared = true)
            if (policyLock is Result.Failed) return@run policyLock
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
            val owner = people.accountForEmployee(company, employeeId)
            if (owner is Result.Failed) return@run owner
            val currentResult = people.findAtInstant(company, employeeId, clock.instant())
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (!canReadLeaveAccount(live, current, (owner as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val exists = people.currentVersion(company, employeeId)
            if (exists is Result.Failed) return@run exists
            if ((exists as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            ledger.list(company, employeeId, year, after, limit).map {
                EmployeeLeaveBalances(
                    LeaveEmployeeReference(
                        employeeId,
                        current?.employeeNumber,
                        current?.person?.legalName,
                    ),
                    year,
                    it,
                )
            }
        }
    }
}
