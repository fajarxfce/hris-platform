package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Issues group gateway credentials after current native/device admission, without provider I/O. */
class BootstrapNativeCollaborationSpaceGateway(
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        parentId: UUID,
        parentVersion: Long,
        spaceId: UUID,
        operationId: UUID,
        deviceId: UUID,
        expectedDeviceVersion: Long,
        expectedGatewayVersion: Long?,
        client: ClientVersion?,
    ): Result<CollaborationSpaceGatewayBootstrap> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val enabled = validateCollaborationSpaceClient(client, policy)
        if (enabled is Result.Failed) return enabled
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (
            expectedDeviceVersion !in 0..999999 ||
                (expectedGatewayVersion != null && expectedGatewayVersion !in 0..999998)
        )
            return changed()
        val currentClient = requireNotNull(client)
        val key =
            OperationKey(
                "collaboration.space_gateway_bootstrap",
                operationId,
                listOf(
                    spaceId.toString(),
                    parentId.toString(),
                    deviceId.toString(),
                    expectedDeviceVersion.toString(),
                    expectedGatewayVersion?.toString() ?: "initial",
                    currentClient.platform.name,
                    currentClient.build.toString(),
                ),
            )
        val started = clock.instant()
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guarded = identities.lockAccount(actor.accountId, shared = true)
            if (guarded is Result.Failed) return@run guarded
            val locked = sessions.lock(parentId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent =
                (locked as Result.Success).value ?: return@run nativeAuthenticationRequired()
            val found = registry.metadata(spaceId, actor.accountId, deviceId, lock = true)
            if (found is Result.Failed) return@run found
            val device = (found as Result.Success).value ?: return@run denied()
            val bound = registry.findBound(spaceId, actor.accountId, parentId)
            if (bound is Result.Failed) return@run bound
            if (
                (bound as Result.Success).value != device ||
                    device.id != deviceId ||
                    device.spaceId != spaceId ||
                    device.accountId != actor.accountId ||
                    device.originNativeSessionId != parentId ||
                    device.platform.name != currentClient.platform.name ||
                    device.state != CollaborationDeviceState.ACTIVE ||
                    device.revokedAt != null
            )
                return@run denied()

            fun response(tokens: CollaborationSpaceGatewayTokens) =
                CollaborationSpaceGatewayBootstrap(
                    device.spaceId,
                    device.accountId,
                    device.id,
                    device.originNativeSessionId,
                    device.matrixUserId,
                    device.matrixDeviceId,
                    tokens,
                )

            fun admission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val at = clock.instant()
                if (at < started || at >= started.plusSeconds(30)) return observationChanged()
                val admitted =
                    admitCollaborationSpaceSession(
                        actor,
                        (acquired as Result.Success).value,
                        parent,
                        parentId,
                        parentVersion,
                        (current as Result.Success).value,
                        currentClient,
                        policy,
                        security,
                        at,
                    )
                if (admitted is Result.Failed) return admitted
                val scope = (admitted as Result.Success).value
                if (scope.spaceId != spaceId) return denied()
                val input = selections.selection(spaceId, actor.accountId, provider.serverName)
                if (input is Result.Failed) return input
                val selection = (input as Result.Success).value ?: return denied()
                if (selection.selected == null) return denied()
                val selected =
                    selectCollaborationSpaceIdentity(
                        spaceId,
                        actor.accountId,
                        provider.serverName,
                        selection,
                        clock.instant(),
                    )
                if (selected is Result.Failed) return selected
                val identity = (selected as Result.Success).value
                val valid =
                    validateExistingCollaborationSpaceDevice(
                        identity,
                        device,
                        parentId,
                        device.platform,
                        device.origin,
                        device.legacy,
                        device.legacyKeyFingerprint,
                        clock.instant(),
                    )
                if (valid is Result.Failed) return valid
                val read = providers.metadata(spaceId, actor.accountId, deviceId)
                if (read is Result.Failed) return read
                val state = (read as Result.Success).value
                val live =
                    validateCollaborationSpaceGatewayProvider(state, device, scope, clock.instant())
                if (live is Result.Failed) return live
                if (device.origin == CollaborationSpaceDeviceOrigin.LEGACY) {
                    val legacy = device.legacy ?: return denied()
                    val source = legacyParents.find(spaceId, actor.accountId, parentId, legacy)
                    if (source is Result.Failed) return source
                    val completed =
                        completions.find(
                            CollaborationLegacyRuntimeOwner(
                                legacy.companyId,
                                legacy.deviceId,
                                actor.accountId,
                                parentId,
                            )
                        )
                    if (completed is Result.Failed) return completed
                    val done = (completed as Result.Success).value ?: return denied()
                    val queue = provisioning.metadata(spaceId, actor.accountId, deviceId)
                    if (queue is Result.Failed) return queue
                    val continuity =
                        validateCompletedCollaborationLegacyDevice(
                            identity,
                            device,
                            (source as Result.Success).value,
                            done,
                            (queue as Result.Success).value,
                            scope,
                            clock.instant(),
                        )
                    if (continuity is Result.Failed) return continuity
                    if (
                        requireNotNull(state).generation < done.targetProviderGeneration ||
                            state.createdAt > done.completedAt
                    )
                        return denied()
                }
                val finished = clock.instant()
                if (
                    finished < at ||
                        finished >= started.plusSeconds(30) ||
                        finished >= scope.expiresAt ||
                        finished >= requireNotNull(state).expiresAt
                )
                    return observationChanged()
                return Result.Success(scope)
            }

            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val read = gateway.find(spaceId, actor.accountId, deviceId, lock = true)
            if (read is Result.Failed) return@run read
            val old = (read as Result.Success).value
            if (old != null) {
                if (old.expiresAt > parent.expiresAt)
                    return@run collaborationSpaceGatewayAuthenticationRequired()
                val valid =
                    validateCollaborationSpaceGatewaySession(
                        old,
                        (admitted as Result.Success).value,
                        device,
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
                    validateCollaborationSpaceGatewayResponse(
                            old,
                            tokens,
                            it,
                            device,
                            clock.instant(),
                        )
                        .map { response(tokens) }
                }
            }
            if (device.version != expectedDeviceVersion || old?.version != expectedGatewayVersion)
                return@run changed()
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (old != null) {
                val allowed = collaborationSpaceGatewayRotationAllowed(old, now)
                if (allowed is Result.Failed) return@run allowed
            }
            val hard = old?.expiresAt ?: minOf(parent.expiresAt, now.plusSeconds(31 * 86400L))
            val next =
                CollaborationSpaceGatewaySession(
                    old?.id ?: UUID.randomUUID(),
                    spaceId,
                    actor.accountId,
                    deviceId,
                    parentId,
                    device.platform,
                    currentClient.build,
                    (old?.version ?: -1) + 1,
                    old?.createdAt ?: now,
                    now,
                    hard,
                    minOf(now.plusSeconds(COLLABORATION_SPACE_GATEWAY_ACCESS_SECONDS), hard),
                )
            val until =
                minOf(
                    now.plusSeconds(COLLABORATION_SPACE_GATEWAY_REPLAY_SECONDS),
                    next.accessExpiresAt,
                )
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
                                "collaboration_space_device",
                                deviceId,
                                "collaboration.space_gateway_bootstrapped",
                                mapOf(
                                    "spaceId" to spaceId.toString(),
                                    "gatewayVersion" to next.version.toString(),
                                ),
                            ),
                        )
                    }
                    .flatMap { admission() }
                    .flatMap {
                        validateCollaborationSpaceGatewayResponse(
                            next,
                            tokens,
                            it,
                            device,
                            clock.instant(),
                        )
                    }
                    .map { response(tokens) }
            }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_gateway_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun observationChanged() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))
}
