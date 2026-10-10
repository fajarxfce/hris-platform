package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.EmploymentRevisionDetails
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class GetEmploymentRevision(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID, revision: Long): Result<EmploymentRevisionDetails> {
        val permission = actor.requirePermission("people.read")
        if (permission is Result.Failed) return permission
        if (revision < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employment_revision"))
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
            val livePermission = live.requirePermission("people.read")
            if (livePermission is Result.Failed) return@run livePermission
            val current = people.currentVersion(company, id)
            if (current is Result.Failed) return@run current
            val version =
                (current as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val found = people.findRevision(company, id, revision)
            if (found is Result.Failed) return@run found
            val selected =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employment_revision_not_found")
                    )
            val owner = companies.find(company)
            if (owner is Result.Failed) return@run owner
            val timezone =
                (owner as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.now(clock.withZone(ZoneId.of(timezone)))
            Result.Success(
                EmploymentRevisionDetails(
                    id,
                    version,
                    today,
                    selected,
                    live.requirePermission("people.manage") is Result.Success &&
                        revision > 0 &&
                        selected.cancellation == null &&
                        selected.terms.effectiveFrom.isAfter(today),
                )
            )
        }
    }
}
