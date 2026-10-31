package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Issues only gateway credentials. It never restores a client or exchanges a provider token. */
class BootstrapNativeCollaborationGateway(
    private val gateway: CollaborationGatewaySessionRepository,
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
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        parentId: UUID,
        parentVersion: Long,
        operationId: UUID,
        deviceId: UUID,
        expectedDeviceVersion: Long,
        expectedGatewayVersion: Long?,
        client: ClientVersion?,
    ): Result<CollaborationGatewayTokens> {
        val permission = actor.requirePermission(COLLABORATION_CHAT_PERMISSION)
        if (permission is Result.Failed) return permission
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            client == null ||
                client.platform !in setOf(ClientPlatform.ANDROID, ClientPlatform.IOS) ||
                client.build <= 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_client_version"))
        if (
            expectedDeviceVersion !in 0..999999 ||
                (expectedGatewayVersion != null && expectedGatewayVersion !in 0..999998)
        )
            return changed()
        val key =
            OperationKey(
                "collaboration.native_gateway_bootstrap",
                operationId,
                listOf(
                    parentId.toString(),
                    deviceId.toString(),
                    expectedDeviceVersion.toString(),
                    expectedGatewayVersion?.toString() ?: "initial",
                    client.platform.name,
                    client.build.toString(),
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val pg = policies.lock(company, shared = true)
            if (pg is Result.Failed) return@run pg
            val cg = companies.lock(company, shared = true)
            if (cg is Result.Failed) return@run cg
            val mg = members.lock(company, shared = true)
            if (mg is Result.Failed) return@run mg
            val ag = identities.lockAccount(actor.accountId, shared = true)
            if (ag is Result.Failed) return@run ag
            val locked = sessions.lock(parentId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent =
                (locked as Result.Success).value ?: return@run nativeAuthenticationRequired()
            if (parent.id != parentId) return@run nativeAuthenticationRequired()
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val found = registry.findBound(company, actor.accountId, parentId)
            if (found is Result.Failed) return@run found
            val device = (found as Result.Success).value
            if (
                device == null ||
                    device.id != deviceId ||
                    device.platform.name != client.platform.name
            )
                return@run denied()
            fun admission(): Result<CollaborationAdmission> {
                val effective = policies.effective(company, clock.instant())
                if (effective is Result.Failed) return effective
                val at = clock.instant()
                return nativeCollaborationClientDeadline(
                        (effective as Result.Success).value,
                        client,
                        at,
                    )
                    .flatMap { deadline ->
                        admitCollaborationDevice(
                                actor,
                                access,
                                parent,
                                parentVersion,
                                device,
                                at,
                                security,
                            )
                            .map { it.copy(expiresAt = minOf(it.expiresAt, deadline)) }
                    }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val read = gateway.find(company, actor.accountId, deviceId, lock = true)
            if (read is Result.Failed) return@run read
            val old = (read as Result.Success).value
            if (old != null) {
                val valid =
                    validateCollaborationGatewaySession(
                        old,
                        (admitted as Result.Success).value,
                        device.platform,
                        clock.instant(),
                    )
                if (valid is Result.Failed) return@run valid
            }
            val receipt = (replay as Result.Success).value
            if (receipt != null) {
                if (old == null || receipt.id != old.id || receipt.version != old.version)
                    return@run changed()
                val recovered = gateway.replayBootstrap(old, operationId)
                if (recovered is Result.Failed) return@run recovered
                val tokens = (recovered as Result.Success).value ?: return@run changed()
                return@run admission().flatMap {
                    validateCollaborationGatewayResponse(
                            old,
                            tokens,
                            it,
                            device.platform,
                            clock.instant(),
                        )
                        .map { tokens }
                }
            }
            if (device.version != expectedDeviceVersion || old?.version != expectedGatewayVersion)
                return@run changed()
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (old != null) {
                val allowed = collaborationGatewayRotationAllowed(old, now)
                if (allowed is Result.Failed) return@run allowed
            }
            val hardDeadline =
                old?.expiresAt ?: minOf(parent.expiresAt, now.plusSeconds(31 * 86400))
            val next =
                CollaborationGatewaySession(
                    old?.id ?: UUID.randomUUID(),
                    company,
                    actor.accountId,
                    deviceId,
                    parentId,
                    device.platform,
                    client.build,
                    (old?.version ?: -1) + 1,
                    old?.createdAt ?: now,
                    now,
                    hardDeadline,
                    minOf(now.plusSeconds(COLLABORATION_GATEWAY_ACCESS_SECONDS), hardDeadline),
                )
            val until =
                minOf(now.plusSeconds(COLLABORATION_GATEWAY_REPLAY_SECONDS), next.accessExpiresAt)
            val issued =
                if (old == null) gateway.create(next, operationId, until)
                else gateway.rotateBootstrap(next, operationId, until)
            issued.flatMap { tokens ->
                operations
                    .record(actor, key, MutationReceipt(next.id, next.version))
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "collaboration_device",
                                deviceId,
                                "collaboration.gateway_bootstrapped",
                                mapOf("gatewayVersion" to next.version.toString()),
                            ),
                        )
                    }
                    .flatMap { admission() }
                    .flatMap {
                        validateCollaborationGatewayResponse(
                            next,
                            tokens,
                            it,
                            device.platform,
                            clock.instant(),
                        )
                    }
                    .map { tokens }
            }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_gateway_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
}
