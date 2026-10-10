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
import java.util.UUID

class GetOffboardingReview(
    private val lifecycle: LifecycleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<OffboardingReview> {
        val required =
            setOf(
                "people.lifecycle.read",
                "people.lifecycle.manage",
                "people.manage",
                "people.offboard",
            )
        if (actor.companyId == null || !actor.permissions.containsAll(required))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "offboarding_access_required"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val caseGuard = lifecycle.lockCase(company, id, shared = true)
            if (caseGuard is Result.Failed) return@run caseGuard
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
            if (!(checked as Result.Success).value.permissions.containsAll(required))
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "offboarding_access_required")
                )
            val found = lifecycle.caseDetails(company, id)
            if (found is Result.Failed) return@run found
            val details =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found")
                    )
            if (
                details.case.kind != LifecycleKind.OFFBOARDING ||
                    details.case.status != LifecycleStatus.OPEN
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open"))
            val current = people.currentVersion(company, details.case.employmentId)
            if (current is Result.Failed) return@run current
            val employmentVersion =
                (current as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val owner = companies.find(company)
            if (owner is Result.Failed) return@run owner
            val timezone =
                (owner as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            Result.Success(
                OffboardingReview(
                    details,
                    employmentVersion,
                    LocalDate.now(clock.withZone(ZoneId.of(timezone))),
                )
            )
        }
    }
}
