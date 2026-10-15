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

class GetOnboardingScheduleReview(
    private val schedules: OnboardingScheduleRepository,
    private val lifecycle: LifecycleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employmentId: UUID,
        selection: OnboardingScheduleSelection,
    ): Result<OnboardingScheduleReview> {
        val permission = requireOnboardingAccess(actor, reading = true)
        if (permission is Result.Failed) return permission
        val valid = validateOnboardingSelection(selection)
        if (valid is Result.Failed) return valid
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val templateGuard = lifecycle.lockTemplates(company, shared = true)
            if (templateGuard is Result.Failed) return@run templateGuard
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
            val loadedEmployee = people.find(company, employmentId, selection.targetDate)
            if (loadedEmployee is Result.Failed) return@run loadedEmployee
            val employee =
                (loadedEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val loadedTemplate = lifecycle.template(company, selection.templateId)
            if (loadedTemplate is Result.Failed) return@run loadedTemplate
            val template =
                (loadedTemplate as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_template_not_found")
                    )
            val loadedCompany = companies.find(company)
            if (loadedCompany is Result.Failed) return@run loadedCompany
            val timezone =
                (loadedCompany as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val loadedAttempts = schedules.forEmployee(company, employmentId)
            if (loadedAttempts is Result.Failed) return@run loadedAttempts
            val attempts = (loadedAttempts as Result.Success).value
            val at = clock.instant()
            val window = onboardingWindow(selection, timezone, at)
            val blockers =
                onboardingScheduleBlockers(employee, template, selection, window, at, attempts)
                    .toMutableList()
            val previousClosure = lifecycle.completedOffboardingDate(company, employmentId)
            if (previousClosure is Result.Failed) return@run previousClosure
            if ((previousClosure as Result.Success).value != null)
                blockers += "employment_offboarded"
            val completedOnboarding =
                lifecycle.hasCompletedCase(company, employmentId, LifecycleKind.ONBOARDING)
            if (completedOnboarding is Result.Failed) return@run completedOnboarding
            if ((completedOnboarding as Result.Success).value)
                blockers += "onboarding_already_completed"
            val otherCases = lifecycle.hasOpenCases(company, employmentId)
            if (otherCases is Result.Failed) return@run otherCases
            if ((otherCases as Result.Success).value) blockers += "other_lifecycle_cases_pending"
            validateOnboardingSession(actor, access, clock.instant(), security, reading = true)
                .map {
                    OnboardingScheduleReview(
                        LifecycleEmployeeReference(
                            employee.id,
                            employee.employeeNumber,
                            employee.person.legalName,
                        ),
                        employee.version,
                        employee.appliedRevision,
                        selection,
                        template,
                        window,
                        onboardingBasisRevision(employee, template, selection, window),
                        attempts.size,
                        blockers.toList(),
                    )
                }
        }
    }
}
