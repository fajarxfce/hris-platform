package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class SaveCompanyClientPolicy(
    private val policies: CompanyClientPolicyRepository,
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
        request: SaveCompanyClientPolicyCommand,
    ): Result<MutationReceipt> {
        val allowed = actor.requirePermission("settings.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val input =
            request.copy(
                activateAt = request.activateAt?.truncatedTo(ChronoUnit.MICROS),
                reason = request.reason.trim(),
                policy =
                    ClientPolicy(
                        request.policy.disabledModules,
                        request.policy.minimumBuilds,
                        request.policy.maintenance?.let {
                            MaintenanceWindow(
                                it.startsAt.truncatedTo(ChronoUnit.MICROS),
                                it.endsAt.truncatedTo(ChronoUnit.MICROS),
                            )
                        },
                    ),
            )
        val valid = validateClientPolicyCommand(input)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "administration.client_policy_save",
                operationId,
                listOf(
                    input.expectedVersion?.toString(),
                    input.activateAt?.toString(),
                    input.policy.minimumBuilds.android.toString(),
                    input.policy.minimumBuilds.ios.toString(),
                    input.policy.minimumBuilds.web.toString(),
                    input.policy.maintenance?.startsAt?.toString(),
                    input.policy.maintenance?.endsAt?.toString(),
                    input.reason,
                ) + input.policy.disabledModules.map { it.name }.sorted(),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanyCommandActor(actor, it) }
                    .flatMap { live ->
                        live.requirePermission("settings.manage").flatMap {
                            if (security.enforceMfa)
                                requireRecentMfa(
                                    live,
                                    clock.instant(),
                                    security.recentAuthenticationAge,
                                )
                            else
                                requireRecentAuthentication(
                                    live,
                                    clock.instant(),
                                    security.recentAuthenticationAge,
                                )
                        }
                    }
            if (authorized is Result.Failed) return@run authorized
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = policies.find(company)
            if (found is Result.Failed) return@run found
            val previous = (found as Result.Success).value
            if (previous?.version != input.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous != null && previous.version >= 9999)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "client_policy_revision_limit")
                )
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val activation = validateClientPolicyActivation(input.activateAt, now)
            if (activation is Result.Failed) return@run activation
            if (previous != null && now.isBefore(previous.recordedAt))
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "client_policy_clock_regressed")
                )
            val snapshot =
                CompanyClientPolicyRevision(
                    (previous?.version ?: -1) + 1,
                    input.activateAt ?: now,
                    input.policy,
                    now,
                    actor.accountId,
                    input.reason,
                )
            policies.save(company, snapshot, input.expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "client_policy",
                                company,
                                "administration.client_policy_saved",
                                mapOf(
                                    "version" to receipt.version.toString(),
                                    "activateAt" to snapshot.activateAt.toString(),
                                ),
                                input.reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
