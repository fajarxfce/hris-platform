package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.PersonProfile
import dev.fajar.hris.people.domain.policies.validatePerson
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class SavePersonProfile(
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employeeId: UUID,
        expectedVersion: Long,
        legalName: String,
        birthDate: LocalDate?,
        nationality: String,
        email: String?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.profile.manage")
        if (access is Result.Failed) return access
        if (expectedVersion < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_profile_change"))
        val key =
            OperationKey(
                "people.profile_save",
                operationId,
                listOf(
                    employeeId.toString(),
                    expectedVersion.toString(),
                    legalName.trim(),
                    birthDate?.toString(),
                    nationality.uppercase(),
                    email?.trim()?.lowercase(),
                    reason,
                ),
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val peopleGuard = people.lockReportingLines(company)
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
            val permission = live.requirePermission("people.profile.manage")
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = profiles.findForEmployee(company, employeeId)
            if (found is Result.Failed) return@run found
            val existing =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "person_profile_not_found")
                    )
            if (existing.ownerCompanyId != actor.companyId)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "profile_owner_required"))
            if (existing.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val updated =
                PersonProfile(
                    existing.profile.id,
                    existing.profile.accountId,
                    legalName.trim(),
                    birthDate,
                    nationality.uppercase(),
                    email?.trim()?.lowercase(),
                )
            val valid = validatePerson(updated, LocalDate.now(clock))
            if (valid is Result.Failed) return@run valid
            profiles.save(actor, updated, expectedVersion, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "person",
                                updated.id,
                                "people.profile_saved",
                                mapOf("version" to receipt.version.toString()),
                                reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
