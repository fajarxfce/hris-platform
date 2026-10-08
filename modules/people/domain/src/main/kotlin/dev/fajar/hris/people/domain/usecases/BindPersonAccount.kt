package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.requireRecentAuthentication
import dev.fajar.hris.identity.domain.policies.requireRecentMfa
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class BindPersonAccount(
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val accounts: AccountAdministrationRepository,
    private val members: MembershipRepository,
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
        accountId: UUID,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val required = setOf("people.account.link", "people.profile.manage", "identity.manage")
        if (actor.companyId == null || !actor.permissions.containsAll(required))
            return Result.Failed(
                Failure(FailureKind.FORBIDDEN, "person_account_link_access_required")
            )
        if (accountId == actor.accountId)
            return Result.Failed(
                Failure(FailureKind.FORBIDDEN, "independent_account_binding_required")
            )
        if (expectedVersion < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_account_binding"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.account_bind",
                operationId,
                listOf(
                    employeeId.toString(),
                    accountId.toString(),
                    expectedVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            // The same administration lock precedes multi-account locks in invitation/access
            // changes.
            val administration = accounts.lockAdministration()
            if (administration is Result.Failed) return@run administration
            val structure = people.lockReportingLines(company)
            if (structure is Result.Failed) return@run structure
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val membershipLock = members.lock(company)
            if (membershipLock is Result.Failed) return@run membershipLock
            for (id in setOf(actor.accountId, accountId).sorted()) {
                val locked = identities.lockAccount(id)
                if (locked is Result.Failed) return@run locked
            }
            val author = identities.access(actor.accountId, company)
            if (author is Result.Failed) return@run author
            val access = (author as Result.Success).value
            if (
                access == null ||
                    !access.account.active ||
                    (actor.credentialVersion != null &&
                        actor.credentialVersion != access.account.securityVersion)
            )
                return@run Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
            if (
                !access.companyActive ||
                    !access.membershipActive ||
                    !access.permissions.containsAll(required)
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "person_account_link_access_required")
                )
            val recent =
                if (security.enforceMfa)
                    requireRecentMfa(actor, clock.instant(), security.recentAuthenticationAge)
                else
                    requireRecentAuthentication(
                        actor,
                        clock.instant(),
                        security.recentAuthenticationAge,
                    )
            if (recent is Result.Failed) return@run recent
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = profiles.findForEmployee(company, employeeId)
            if (found is Result.Failed) return@run found
            val person =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "person_profile_not_found")
                    )
            if (person.ownerCompanyId != company)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "profile_owner_required"))
            if (person.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (person.profile.accountId != null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "person_account_already_bound")
                )
            val target = identities.access(accountId, company)
            if (target is Result.Failed) return@run target
            val member = (target as Result.Success).value
            if (member == null || !member.account.active || !member.membershipActive)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "account_membership_required")
                )
            profiles
                .bindAccount(actor, person.profile.id, accountId, expectedVersion, reason)
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "person",
                                    person.profile.id,
                                    "people.account_bound",
                                    mapOf(
                                        "accountId" to accountId.toString(),
                                        "version" to receipt.version.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
