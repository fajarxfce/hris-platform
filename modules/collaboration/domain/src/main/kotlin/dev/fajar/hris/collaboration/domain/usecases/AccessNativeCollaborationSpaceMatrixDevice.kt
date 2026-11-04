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

/**
 * Fixed self and own-key operations under group ownership. Room/recipient authority is separate.
 */
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
    private val keys: CollaborationSpaceKeyRepository,
    private val providerKeys: CollaborationMatrixKeyRepository,
    private val journal: ChangeJournalRepository,
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

    fun uploadKeys(
        token: String,
        correlationId: UUID,
        request: CollaborationMatrixKeyUpload,
    ): Result<CollaborationMatrixKeyCounts> {
        val captured = snapshotCollaborationKeyUpload(request)
        if (captured is Result.Failed) return captured
        val upload = (captured as Result.Success).value
        return admitted(token, correlationId, true, { reserveKeys(it, upload) }) {
            exchange,
            reservation ->
            if (upload.deviceKeys == null && !upload.hasSignedKeys)
                return@admitted exchange.exchange { bearer, deadline ->
                    providerKeys.upload(upload, bearer, deadline)
                }
            val retained = reservation ?: return@admitted keysRequired()
            val existing = exchange.readKeys()
            if (existing is Result.Failed) return@admitted existing
            val prior = (existing as Result.Success).value
            if (prior != null) {
                val validated = validatePublicKeys(exchange.observation, retained, prior)
                if (validated is Result.Failed) return@admitted validated
            }
            val identity = upload.deviceKeys ?: prior ?: return@admitted keysRequired()
            val proof = validateCollaborationSignedKeys(upload, identity)
            if (proof is Result.Failed) return@admitted proof
            val response =
                exchange.exchange { bearer, deadline ->
                    providerKeys.upload(upload, bearer, deadline)
                }
            if (response is Result.Failed) return@admitted response
            val readback = exchange.readKeys()
            if (readback is Result.Failed) return@admitted readback
            val published = (readback as Result.Success).value ?: return@admitted invalidProvider()
            validatePublicKeys(exchange.observation, retained, published).flatMap {
                exchange.publishKeys(retained).flatMap { response }
            }
        }
    }

    private fun reserveKeys(
        observed: Observation,
        upload: CollaborationMatrixKeyUpload,
    ): Result<CollaborationSpaceKeyPublication?> {
        if (upload.deviceKeys == null && !upload.hasSignedKeys) return Result.Success(null)
        val device = observed.device
        val fingerprint =
            upload.deviceKeys?.let {
                validateCollaborationDeviceKeys(it, device.matrixUserId, device.matrixDeviceId)
            }
        if (fingerprint is Result.Failed) return fingerprint
        val candidateFingerprint = (fingerprint as? Result.Success)?.value
        if (
            candidateFingerprint != null &&
                device.legacyKeyFingerprint?.let { it != candidateFingerprint } == true
        )
            return keysChanged()
        val found = keys.find(device.spaceId, device.accountId, device.id)
        if (found is Result.Failed) return found
        val existing = (found as Result.Success).value
        val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
        if (existing != null) {
            if (
                !validPublication(existing, observed, at) ||
                    candidateFingerprint?.let { it != existing.fingerprint } == true
            )
                return keysChanged()
            if (existing.version == 0L && upload.deviceKeys == null && upload.hasSignedKeys)
                return keysRequired()
            return Result.Success(existing)
        }
        if (candidateFingerprint == null) return keysRequired()
        val value =
            CollaborationSpaceKeyPublication(
                device.spaceId,
                device.accountId,
                device.id,
                device.originNativeSessionId,
                device.matrixUserId,
                device.matrixDeviceId,
                candidateFingerprint,
                0,
                at,
            )
        return keys
            .reserve(value)
            .flatMap {
                journal.record(
                    observed.actor,
                    ChangeRecord(
                        "collaboration_space_device",
                        device.id,
                        "collaboration.space_device_keys_reserved",
                        mapOf("spaceId" to device.spaceId.toString(), "version" to "0"),
                    ),
                )
            }
            .flatMap { keys.find(device.spaceId, device.accountId, device.id) }
            .flatMap { if (it == value) Result.Success(value) else keysChanged() }
    }

    private fun validPublication(
        value: CollaborationSpaceKeyPublication,
        observed: Observation,
        at: Instant,
    ): Boolean {
        val d = observed.device
        return validCollaborationSpaceKey(value) &&
            value.spaceId == d.spaceId &&
            value.accountId == d.accountId &&
            value.deviceId == d.id &&
            value.nativeSessionId == d.originNativeSessionId &&
            value.matrixUserId == d.matrixUserId &&
            value.matrixDeviceId == d.matrixDeviceId &&
            value.reservedAt >= d.createdAt &&
            value.reservedAt <= at &&
            value.publishedAt?.let { it > at } != true &&
            d.legacyKeyFingerprint?.let { it != value.fingerprint } != true
    }

    private fun validatePublicKeys(
        observed: Observation,
        retained: CollaborationSpaceKeyPublication,
        value: CollaborationMatrixDeviceKeys,
    ): Result<Unit> =
        when (
            val proof =
                validateCollaborationDeviceKeys(
                    value,
                    observed.device.matrixUserId,
                    observed.device.matrixDeviceId,
                )
        ) {
            is Result.Failed -> invalidProvider()
            is Result.Success ->
                if (proof.value == retained.fingerprint) Result.Success(Unit) else keysChanged()
        }

    private inner class Exchange(
        private val context: Context,
        val observation: Observation,
        val lease: CollaborationSpaceRequestLease,
    ) {
        fun <T> exchange(call: (String, Instant) -> Result<T>): Result<T> {
            val before = transactions.run(context.scope) { confirm(context, observation, lease) }
            if (before is Result.Failed) return before
            val response = call(requireNotNull(observation.credential).accessToken, lease.expiresAt)
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException("Group key request interrupted")
            val after = transactions.run(context.scope) { confirm(context, observation, lease) }
            return if (after is Result.Failed) after else response
        }

        fun readKeys(): Result<CollaborationMatrixDeviceKeys?> = exchange { bearer, deadline ->
            providerKeys.ownDeviceKeys(
                observation.device.matrixUserId,
                observation.device.matrixDeviceId,
                bearer,
                deadline,
            )
        }

        fun publishKeys(retained: CollaborationSpaceKeyPublication): Result<Unit> =
            transactions.run(context.scope) {
                confirm(context, observation, lease)
                    .flatMap {
                        keys.find(context.spaceId, context.scope.accountId, context.deviceId)
                    }
                    .flatMap publish@{ current ->
                        val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
                        if (
                            current == null ||
                                !validPublication(current, observation, at) ||
                                current.version < retained.version ||
                                (retained.version == 1L && current != retained) ||
                                current.copy(
                                    version = retained.version,
                                    publishedAt = retained.publishedAt,
                                ) != retained
                        )
                            return@publish keysChanged()
                        if (current.version == 1L)
                            return@publish confirm(context, observation, lease)
                        val next = retained.copy(version = 1, publishedAt = at)
                        keys
                            .publish(current, at)
                            .flatMap {
                                journal.record(
                                    observation.actor,
                                    ChangeRecord(
                                        "collaboration_space_device",
                                        context.deviceId,
                                        "collaboration.space_device_keys_published",
                                        mapOf(
                                            "spaceId" to context.spaceId.toString(),
                                            "version" to "1",
                                        ),
                                    ),
                                )
                            }
                            .flatMap { confirm(context, observation, lease) }
                            .flatMap {
                                keys.find(
                                    context.spaceId,
                                    context.scope.accountId,
                                    context.deviceId,
                                )
                            }
                            .flatMap {
                                if (it == next) confirm(context, observation, lease)
                                else keysChanged()
                            }
                    }
            }
    }

    private fun keysChanged() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_device_keys_changed"))

    private fun keysRequired() =
        Result.Failed(Failure(FailureKind.VALIDATION, "collaboration_device_keys_required"))

    private fun invalidProvider() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"))

    // Only fixed public operations may enter these private runners. No public arbitrary provider
    // callback.
    private fun <T> admitted(
        token: String,
        correlationId: UUID,
        decrypt: Boolean,
        exchange: (Observation, Instant) -> Result<T>,
    ): Result<T> =
        admitted(token, correlationId, decrypt, { Result.Success(Unit) }) { request, _ ->
            exchange(request.observation, request.lease.expiresAt)
        }

    private fun <S, T> admitted(
        token: String,
        correlationId: UUID,
        decrypt: Boolean,
        prepare: (Observation) -> Result<S>,
        operation: (Exchange, S) -> Result<T>,
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
                        confirm(context, observed, lease).flatMap {
                            prepare(observed).flatMap { extra ->
                                confirm(context, observed, lease).map {
                                    Preparation(observed, lease, extra)
                                }
                            }
                        }
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
            val response = operation(Exchange(context, value.observation, value.lease), value.extra)
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

    private class Preparation<S>(
        val observation: Observation,
        val lease: CollaborationSpaceRequestLease,
        val extra: S,
    )
}
