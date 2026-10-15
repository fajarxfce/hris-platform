package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetOnboardingSchedule(
    private val schedules: OnboardingScheduleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, employmentId: UUID, id: UUID): Result<OnboardingScheduleDetails> {
        val permission = requireOnboardingAccess(actor, reading = true)
        if (permission is Result.Failed) return permission
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val access = (loadedAccess as Result.Success).value
            val checked =
                validateOnboardingSession(actor, access, clock.instant(), security, reading = true)
            if (checked is Result.Failed) return@run checked
            val parent = people.currentVersion(company, employmentId)
            if (parent is Result.Failed) return@run parent
            if ((parent as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val found = schedules.find(company, id)
            if (found is Result.Failed) return@run found
            val details =
                (found as Result.Success).value?.takeIf { it.schedule.employmentId == employmentId }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "onboarding_schedule_not_found")
                    )
            validateOnboardingSession(actor, access, clock.instant(), security, reading = true)
                .map { current ->
                    details.copy(actions = onboardingScheduleActions(current, details))
                }
        }
    }
}
