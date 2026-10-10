package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetPersonProfile(
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, employeeId: UUID): Result<ManagedPersonProfile> {
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
            profiles.findForEmployee(company, employeeId).flatMap { profile ->
                if (
                    profile == null ||
                        !("people.profile.read" in live.permissions ||
                            ("people.self.read" in live.permissions &&
                                profile.profile.accountId == live.accountId))
                )
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "person_profile_not_found"))
                else Result.Success(profile)
            }
        }
    }
}
