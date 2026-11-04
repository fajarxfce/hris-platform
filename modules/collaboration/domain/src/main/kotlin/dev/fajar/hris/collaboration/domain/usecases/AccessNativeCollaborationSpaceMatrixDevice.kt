package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Fixed self operations under group ownership. This grants no room, key or content access. */
class AccessNativeCollaborationSpaceMatrixDevice(
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val requests: CollaborationSpaceRequestRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val matrix: CollaborationMatrixRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun identity(token: String, correlationId: UUID): Result<CollaborationMatrixIdentity> =
        admitted(token, correlationId, decrypt = true) { observed, until ->
            matrix.whoAmI(requireNotNull(observed.credential).accessToken, until).flatMap {
                if (
                    it.userId != observed.device.matrixUserId ||
                        it.deviceId != observed.device.matrixDeviceId ||
                        it.isGuest
                )
                    Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response")
                    )
                else Result.Success(it)
            }
        }

    fun capabilities(token: String, correlationId: UUID): Result<Unit> =
        admitted(token, correlationId, decrypt = false) { _, _ -> Result.Success(Unit) }

    // Only the two fixed public operations can use this runner; no caller-controlled
    // provider path, token, request body or network callback crosses its public API.
    private fun <T> admitted(
        token: String,
        correlationId: UUID,
        decrypt: Boolean,
        exchange: (Observation, Instant) -> Result<T>,
    ): Result<T> {
        if (
            !policy.enabled ||
                !provider.enabled ||
                !validCollaborationSpaceServer(provider.serverName)
        )
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (!validCollaborationSpaceGatewayAccess(token)) return authenticationRequired()
        val started = clock.instant()
        val lookup = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        val resolved = transactions.run(lookup) { gateway.findAccess(token) }
        if (resolved is Result.Failed) return resolved
        val initial = (resolved as Result.Success).value ?: return authenticationRequired()
        val context = Context(lookup.copy(accountId = initial.accountId), initial, started)
        val prepared =
            transactions.run(context.scope) {
                observe(context, decrypt).flatMap { observed ->
                    val until =
                        minOf(
                                started.plusSeconds(30),
                                observed.admission.expiresAt,
                                initial.accessExpiresAt,
                                initial.expiresAt,
                                observed.state.expiresAt,
                            )
                            .truncatedTo(ChronoUnit.MICROS)
                    requests.claim(position(initial), until).flatMap leaseClaim@{ lease ->
                        if (lease == null)
                            return@leaseClaim Result.Failed(
                                Failure(
                                    FailureKind.UNAVAILABLE,
                                    "collaboration_space_gateway_busy",
                                    parameters = mapOf("retryAfterSeconds" to "1"),
                                )
                            )
                        confirm(context, observed, lease).map { Preparation(observed, lease) }
                    }
                }
            }
        if (prepared is Result.Failed) return prepared
        val value = (prepared as Result.Success).value
        var originalFailure: Throwable? = null
        try {
            val before =
                transactions.run(context.scope) { confirm(context, value.observation, value.lease) }
            if (before is Result.Failed) return before
            val response = exchange(value.observation, value.lease.expiresAt)
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException("Group request interrupted")
            val after =
                transactions.run(context.scope) { confirm(context, value.observation, value.lease) }
            return if (after is Result.Failed) after else response
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            if (!Thread.currentThread().isInterrupted) {
                try {
                    transactions.run(context.scope) { requests.release(value.lease) }
                } catch (cleanup: Throwable) {
                    val original = originalFailure
                    if (original == null) throw cleanup
                    if (original !== cleanup) original.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun confirm(
        context: Context,
        original: Observation,
        lease: CollaborationSpaceRequestLease,
    ): Result<Unit> =
        observe(context, decrypt = false).flatMap { current ->
            if (
                current.device != original.device ||
                    current.identity != original.identity ||
                    current.state != original.state ||
                    current.actor != original.actor ||
                    current.admission.nativeSessionVersion !=
                        original.admission.nativeSessionVersion ||
                    current.admission.credentialVersion != original.admission.credentialVersion
            )
                return@flatMap changed()
            requests.find(position(context.initial).owner, lease.slot, lock = true).flatMap { actual
                ->
                val at = clock.instant()
                if (
                    actual != lease ||
                        lease.position != position(context.initial) ||
                        lease.slot !in 1..7 ||
                        lease.token == UUID(0, 0) ||
                        lease.startedAt > at ||
                        lease.expiresAt >
                            minOf(
                                context.started.plusSeconds(30),
                                original.admission.expiresAt,
                                context.initial.accessExpiresAt,
                                context.initial.expiresAt,
                                original.state.expiresAt,
                            ) ||
                        at < original.admission.admittedAt ||
                        at < current.admission.admittedAt ||
                        at >= minOf(lease.expiresAt, current.admission.expiresAt)
                )
                    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_request_lost"))
                else Result.Success(Unit)
            }
        }

    private fun position(head: CollaborationSpaceGatewaySession) =
        CollaborationSpaceRequestPosition(
            CollaborationSpaceRequestOwner(
                head.spaceId,
                head.accountId,
                head.deviceId,
                head.nativeSessionId,
            ),
            CollaborationSpaceRequestKind.GATEWAY,
            head.id,
            head.version,
        )

    /** Account -> native -> device -> gateway -> request; provider I/O is always outside. */
    private fun observe(context: Context, decrypt: Boolean): Result<Observation> {
        val guarded = identities.lockAccount(context.scope.accountId, shared = true)
        if (guarded is Result.Failed) return guarded
        val locked = sessions.lock(context.parentId, context.scope.accountId)
        if (locked is Result.Failed) return locked
        val parent = (locked as Result.Success).value ?: return nativeAuthenticationRequired()
        val actor =
            context.scope.copy(
                authenticatedAt = parent.authenticatedAt,
                mfaVerifiedAt = parent.mfaVerifiedAt,
                credentialVersion = parent.credentialVersion,
            )

        val found =
            registry.metadata(context.spaceId, actor.accountId, context.deviceId, lock = true)
        if (found is Result.Failed) return found
        val device = (found as Result.Success).value ?: return denied()
        val bound = registry.findBound(context.spaceId, actor.accountId, context.parentId)
        if (bound is Result.Failed) return bound
        if (
            (bound as Result.Success).value != device ||
                device.id != context.deviceId ||
                device.spaceId != context.spaceId ||
                device.accountId != actor.accountId ||
                device.originNativeSessionId != context.parentId ||
                device.platform.name != context.client.platform.name ||
                device.state != CollaborationDeviceState.ACTIVE ||
                device.revokedAt != null
        )
            return denied()
        val head = gateway.find(context.spaceId, actor.accountId, context.deviceId, lock = true)
        if (head is Result.Failed) return head
        if ((head as Result.Success).value != context.initial) return authenticationRequired()
        if (context.initial.expiresAt > parent.expiresAt) return authenticationRequired()
        fun admission(): Result<CollaborationSpaceAdmission> {
            val access = identities.access(actor.accountId, null)
            if (access is Result.Failed) return access
            val current = spaces.current()
            if (current is Result.Failed) return current
            val now = clock.instant()
            if (now < context.started || now >= context.started.plusSeconds(30)) return changed()
            return admitCollaborationSpaceSession(
                    actor,
                    (access as Result.Success).value,
                    parent,
                    context.parentId,
                    parent.version,
                    (current as Result.Success).value,
                    context.client,
                    policy,
                    security,
                    now,
                )
                .flatMap { if (it.spaceId == context.spaceId) Result.Success(it) else denied() }
        }
        val admitted = admission()
        if (admitted is Result.Failed) return admitted
        val scope = (admitted as Result.Success).value
        val gatewayValid =
            validateCollaborationSpaceGatewaySession(
                context.initial,
                scope,
                device,
                clock.instant(),
            )
        if (gatewayValid is Result.Failed) return gatewayValid
        if (clock.instant() >= context.initial.accessExpiresAt) return authenticationRequired()

        val input = selections.selection(context.spaceId, actor.accountId, provider.serverName)
        if (input is Result.Failed) return input
        val selection = (input as Result.Success).value ?: return denied()
        if (selection.selected == null) return denied()
        val selected =
            selectCollaborationSpaceIdentity(
                context.spaceId,
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
                context.parentId,
                device.platform,
                device.origin,
                device.legacy,
                device.legacyKeyFingerprint,
                clock.instant(),
            )
        if (valid is Result.Failed) return valid
        val read = providers.metadata(context.spaceId, actor.accountId, context.deviceId)
        if (read is Result.Failed) return read
        val state = (read as Result.Success).value ?: return denied()
        val live = validateCollaborationSpaceGatewayProvider(state, device, scope, clock.instant())
        if (live is Result.Failed) return live
        if (device.origin == CollaborationSpaceDeviceOrigin.LEGACY) {
            val legacy = device.legacy ?: return denied()
            val source =
                legacyParents.find(context.spaceId, actor.accountId, context.parentId, legacy)
            if (source is Result.Failed) return source
            val completed =
                completions.find(
                    CollaborationLegacyRuntimeOwner(
                        legacy.companyId,
                        legacy.deviceId,
                        actor.accountId,
                        context.parentId,
                    )
                )
            if (completed is Result.Failed) return completed
            val done = (completed as Result.Success).value ?: return denied()
            val queue = provisioning.metadata(context.spaceId, actor.accountId, context.deviceId)
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
                state.generation < done.targetProviderGeneration ||
                    state.createdAt > done.completedAt
            )
                return denied()
        }
        val credential =
            if (decrypt) {
                val acquired = providers.credential(state)
                if (acquired is Result.Failed) return acquired
                val value = (acquired as Result.Success).value
                if (
                    value.state != state ||
                        value.accessToken.length !in 1..2048 ||
                        value.accessToken.any { it.code !in 33..126 }
                )
                    return authenticationRequired()
                value
            } else null
        return admission().flatMap { final ->
            val now = clock.instant()
            if (now >= context.initial.accessExpiresAt || now >= state.expiresAt)
                authenticationRequired()
            else
                validateCollaborationSpaceGatewaySession(context.initial, final, device, now).map {
                    Observation(actor, device, identity, state, credential, final)
                }
        }
    }

    private fun authenticationRequired() = collaborationSpaceGatewayAuthenticationRequired()

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private class Context(
        val scope: Actor,
        val initial: CollaborationSpaceGatewaySession,
        val started: Instant,
    ) {
        val spaceId
            get() = initial.spaceId

        val parentId
            get() = initial.nativeSessionId

        val deviceId
            get() = initial.deviceId

        val client =
            ClientVersion(ClientPlatform.valueOf(initial.platform.name), initial.clientBuild)
    }

    private class Observation(
        val actor: Actor,
        val device: CollaborationSpaceDevice,
        val identity: CollaborationSpaceIdentity,
        val state: CollaborationSpaceProviderState,
        val credential: CollaborationSpaceProviderSession?,
        val admission: CollaborationSpaceAdmission,
    )

    private class Preparation(
        val observation: Observation,
        val lease: CollaborationSpaceRequestLease,
    )
}
