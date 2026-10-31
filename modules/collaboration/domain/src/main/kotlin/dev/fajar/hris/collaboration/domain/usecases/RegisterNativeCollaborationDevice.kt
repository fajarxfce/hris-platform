package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationRegistryRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Native session identity/version come from authenticated transport, never the request body. */
class RegisterNativeCollaborationDevice(
    private val registry: CollaborationRegistryRepository,
    private val sessions: NativeSessionRepository,
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
        sessionId: UUID,
        sessionVersion: Long,
        operationId: UUID,
        platform: CollaborationPlatform,
        label: String,
        clientVersion: ClientVersion?,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission(COLLABORATION_CHAT_PERMISSION)
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val name = label.trim()
        if (
            name.length !in 1..64 ||
                name.any { it.isISOControl() } ||
                platform == CollaborationPlatform.WEB ||
                (clientVersion != null && clientVersion.platform.name != platform.name)
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_device"))
        // Refresh rotates the parent's version, but must not invalidate a lost-response receipt.
        val key =
            OperationKey(
                "collaboration.native_device_register",
                operationId,
                listOf(sessionId.toString(), platform.name, name),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            // Serializes quota across this account's companies and native session mutations.
            val accountGuard = identities.lockAccount(actor.accountId)
            if (accountGuard is Result.Failed) return@run accountGuard
            val parent = sessions.lock(sessionId, actor.accountId)
            if (parent is Result.Failed) return@run parent
            val session = (parent as Result.Success).value
            if (session?.id != sessionId) return@run nativeAuthenticationRequired()
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            fun authorized(): Result<Unit> {
                val at = clock.instant()
                return collaborationParentDeadline(
                        actor,
                        access,
                        session,
                        sessionVersion,
                        at,
                        security,
                    )
                    .flatMap {
                        policies.effective(company, at).flatMap {
                            val checkedAt = clock.instant()
                            nativeCollaborationClientDeadline(it, clientVersion, checkedAt)
                                .flatMap {
                                    collaborationParentDeadline(
                                            actor,
                                            access,
                                            session,
                                            sessionVersion,
                                            checkedAt,
                                            security,
                                        )
                                        .map { Unit }
                                }
                        }
                    }
            }
            val allowed = authorized()
            if (allowed is Result.Failed) return@run allowed
            // Parent lock precedes the device read to protect its immutable binding from purge.
            val found = registry.findBound(company, actor.accountId, sessionId)
            if (found is Result.Failed) return@run found
            val device = (found as Result.Success).value
            if (
                device != null &&
                    (device.state == CollaborationDeviceState.REVOKED ||
                        device.revokedAt != null ||
                        device.companyId != company ||
                        device.accountId != actor.accountId ||
                        device.nativeSessionId != sessionId ||
                        device.createdAt.isAfter(clock.instant()))
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                )
            val previous = (replay as Result.Success).value
            if (previous != null) {
                if (device?.id != previous.id)
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                    )
                return@run authorized().map { previous }
            }
            if (device != null) {
                val receipt = MutationReceipt(device.id, device.version)
                return@run operations.record(actor, key, receipt).flatMap {
                    authorized().map { receipt }
                }
            }
            val count =
                registry.countActiveParentSessions(
                    actor.accountId,
                    clock.instant(),
                    excluding = sessionId,
                )
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 5)
                return@run Result.Failed(
                    Failure(
                        FailureKind.CONFLICT,
                        "collaboration_device_limit",
                        parameters = mapOf("maximum" to "5"),
                    )
                )
            val registration =
                CollaborationRegistration(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    sessionId,
                    platform,
                    name,
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                )
            val receipt = MutationReceipt(registration.deviceId, 0)
            registry
                .register(registration)
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_device",
                            receipt.id,
                            "collaboration.device_registered",
                            mapOf("platform" to platform.name, "version" to "0"),
                        ),
                    )
                }
                .flatMap { authorized().map { receipt } }
        }
    }
}
