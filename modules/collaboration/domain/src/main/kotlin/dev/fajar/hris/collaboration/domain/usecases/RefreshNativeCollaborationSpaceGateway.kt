package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Resolves an opaque refresh, then reacquires current native/group authority in its owning
 * transaction.
 */
class RefreshNativeCollaborationSpaceGateway(
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
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(token: String, correlationId: UUID): Result<CollaborationSpaceGatewayTokens> {
        if (
            !policy.enabled ||
                !provider.enabled ||
                !validCollaborationSpaceServer(provider.serverName)
        )
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (!validCollaborationSpaceGatewayRefresh(token))
            return collaborationSpaceGatewayAuthenticationRequired()
        val started = clock.instant()
        val anonymous = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        val resolved = transactions.run(anonymous) { gateway.findRefresh(token) }
        if (resolved is Result.Failed) return resolved
        val lookup =
            (resolved as Result.Success).value
                ?: return collaborationSpaceGatewayAuthenticationRequired()
        val initial = lookup.session
        val scope = anonymous.copy(accountId = initial.accountId)
        val spaceId = initial.spaceId
        val deviceId = initial.deviceId
        val parentId = initial.nativeSessionId
        return transactions.run(scope) {
            val guarded = identities.lockAccount(scope.accountId, shared = true)
            if (guarded is Result.Failed) return@run guarded
            val locked = sessions.lock(parentId, scope.accountId)
            if (locked is Result.Failed) return@run locked
            val parent =
                (locked as Result.Success).value
                    ?: return@run collaborationSpaceGatewayAuthenticationRequired()
            if (parent.id != parentId || parent.accountId != scope.accountId)
                return@run collaborationSpaceGatewayAuthenticationRequired()
            val parentVersion = parent.version
            val actor =
                scope.copy(
                    authenticatedAt = parent.authenticatedAt,
                    mfaVerifiedAt = parent.mfaVerifiedAt,
                    credentialVersion = parent.credentialVersion,
                )
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
                    device.platform != initial.platform ||
                    device.state != CollaborationDeviceState.ACTIVE ||
                    device.revokedAt != null
            )
                return@run denied()
            val read = gateway.find(spaceId, actor.accountId, deviceId, lock = true)
            if (read is Result.Failed) return@run read
            val head =
                (read as Result.Success).value
                    ?: return@run collaborationSpaceGatewayAuthenticationRequired()
            if (
                head.id != initial.id ||
                    head.nativeSessionId != parentId ||
                    head.expiresAt > parent.expiresAt
            )
                return@run collaborationSpaceGatewayAuthenticationRequired()
            val currentClient =
                ClientVersion(
                    if (head.platform == CollaborationPlatform.ANDROID) ClientPlatform.ANDROID
                    else ClientPlatform.IOS,
                    head.clientBuild,
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
            val valid =
                validateCollaborationSpaceGatewaySession(
                    head,
                    (admitted as Result.Success).value,
                    device,
                    clock.instant(),
                )
            if (valid is Result.Failed) return@run valid
            // Lookup is only a hint. Recover the winner under its actual locked head.
            val recovered = gateway.replayRefresh(head, token)
            if (recovered is Result.Failed) return@run recovered
            val replay = (recovered as Result.Success).value
            if (replay != null)
                return@run admission().flatMap {
                    validateCollaborationSpaceGatewayResponse(
                            head,
                            replay,
                            it,
                            device,
                            clock.instant(),
                        )
                        .map { replay }
                }
            if (lookup.previous || head.version != initial.version)
                return@run collaborationSpaceGatewayAuthenticationRequired()
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val allowed = collaborationSpaceGatewayRotationAllowed(head, now)
            if (allowed is Result.Failed) return@run allowed
            val next =
                head.copy(
                    version = head.version + 1,
                    rotatedAt = now,
                    accessExpiresAt =
                        minOf(
                            now.plusSeconds(COLLABORATION_SPACE_GATEWAY_ACCESS_SECONDS),
                            head.expiresAt,
                        ),
                )
            val until =
                minOf(
                    now.plusSeconds(COLLABORATION_SPACE_GATEWAY_REPLAY_SECONDS),
                    next.accessExpiresAt,
                )
            gateway.rotateRefresh(next, token, until).flatMap { tokens ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "collaboration_space_device",
                            deviceId,
                            "collaboration.space_gateway_refreshed",
                            mapOf(
                                "spaceId" to spaceId.toString(),
                                "gatewayVersion" to next.version.toString(),
                            ),
                        ),
                    )
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
                    .map { tokens }
            }
        }
    }

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun observationChanged() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))
}
