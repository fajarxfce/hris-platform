package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.NativeSession
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Advances an already committed queue claim. Provider I/O holds no SQL locks. Failure leaves the
 * lease for a separate bounded outcome command; it never logs out a losing Matrix candidate.
 */
class AdvanceCollaborationSpaceProvisioning(
    private val registry: CollaborationSpaceDeviceRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val credentials: CollaborationSpaceProviderSessionRepository,
    private val matrix: CollaborationSpaceMatrixRepository,
    private val keys: CollaborationMatrixKeyRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val runtimes: CollaborationLegacyRuntimeRepository,
    private val gateways: CollaborationLegacyGatewayRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val publications: CollaborationSpaceKeyRepository,
) {
    fun execute(lease: CollaborationSpaceProvisioningLease): Result<Unit> {
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val available = validateCollaborationSpaceClient(lease.request.client, policy)
        if (available is Result.Failed) return available
        val request = lease.request
        val actor =
            Actor(
                request.accountId,
                null,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
                mfaVerifiedAt = request.mfaVerifiedAt,
                credentialVersion = request.credentialVersion,
            )
        val started = clock.instant()
        val preparation = transactions.run(actor) { observe(actor, lease, started, decrypt = true) }
        if (preparation is Result.Failed) return preparation
        val prepared = (preparation as Result.Success).value
        val reusable = prepared.credential
        val candidate =
            if (reusable == null)
                matrix.login(
                    CollaborationSpaceMatrixLogin(
                        request.spaceId,
                        request.accountId,
                        request.deviceId,
                        prepared.identity.legacy?.companyId,
                        prepared.device.legacy,
                        prepared.admission.admittedAt,
                        prepared.deadline,
                    )
                )
            else
                Result.Success(
                    CollaborationMatrixCredential(
                        reusable.state.matrixUserId,
                        reusable.state.matrixDeviceId,
                        reusable.accessToken,
                        reusable.state.expiresAt,
                    )
                )
        checkInterrupted()
        val afterLogin = reobserve(actor, lease, started, prepared)
        if (afterLogin is Result.Failed) return afterLogin
        if (candidate is Result.Failed) return candidate
        val value = (candidate as Result.Success).value
        val valid = candidateMatches(value, prepared.device, clock.instant())
        if (valid is Result.Failed) return valid
        val verified =
            if (prepared.retained == null) null
            else {
                val read =
                    keys.ownDeviceKeys(
                        value.userId,
                        value.deviceId,
                        value.accessToken,
                        prepared.deadline,
                    )
                val observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
                checkInterrupted()
                val afterKeys = reobserve(actor, lease, started, prepared)
                if (afterKeys is Result.Failed) return afterKeys
                if (read is Result.Failed) return read
                val deviceKeys = (read as Result.Success).value ?: return invalidKeys()
                val validated =
                    validateCollaborationDeviceKeys(deviceKeys, value.userId, value.deviceId)
                if (validated is Result.Failed) return invalidKeys()
                val fingerprint = (validated as Result.Success).value
                if (fingerprint != prepared.device.legacyKeyFingerprint) return invalidKeys()
                VerifiedKeys(fingerprint, observedAt)
            }
        // Synapse logout removes all tokens and E2EE keys for this device. A losing candidate
        // must expire within the provider's bounded lifetime, without cleanup logout.
        return transactions.run(actor) {
            observe(actor, lease, started, decrypt = false).flatMap { current ->
                val consistent = sameObservation(prepared, current)
                if (consistent is Result.Failed) return@flatMap consistent
                val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
                val window = window(prepared, current, clock.instant())
                if (window is Result.Failed) return@flatMap window
                val stillValid = candidateMatches(value, current.device, clock.instant())
                if (stillValid is Result.Failed) return@flatMap stillValid
                val prior = current.state
                val generation =
                    if (reusable != null) reusable.state.generation
                    else (prior?.generation ?: -1) + 1
                val stored =
                    if (reusable != null) Result.Success(Unit)
                    else {
                        if (prior != null && prior.generation >= 999999) return@flatMap changed()
                        val session =
                            CollaborationSpaceProviderSession(
                                CollaborationSpaceProviderState(
                                    request.spaceId,
                                    request.accountId,
                                    request.deviceId,
                                    request.nativeSessionId,
                                    value.userId,
                                    value.deviceId,
                                    generation,
                                    prior?.createdAt ?: at,
                                    at,
                                    value.expiresAt.truncatedTo(ChronoUnit.MICROS),
                                ),
                                value.accessToken,
                            )
                        if (prior == null) credentials.create(session)
                        else credentials.rotate(session, prior.generation)
                    }
                stored
                    .flatMap {
                        registry.replace(
                            current.device.copy(
                                state = CollaborationDeviceState.ACTIVE,
                                version = current.device.version + 1,
                                updatedAt = at,
                            ),
                            current.device.version,
                        )
                    }
                    .flatMap { provisioning.finish(lease) }
                    .flatMap { if (it) Result.Success(Unit) else lostLease() }
                    .flatMap { completeRetained(actor, current, lease, generation, verified) }
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "collaboration_space_device",
                                request.deviceId,
                                "collaboration.space_device_activated",
                                mapOf(
                                    "spaceId" to request.spaceId.toString(),
                                    "version" to (current.device.version + 1).toString(),
                                    "provisioningRevision" to request.revision.toString(),
                                ),
                            ),
                        )
                    }
                    .flatMap {
                        admit(actor, current.parent, lease, started).flatMap {
                            window(prepared, current, clock.instant())
                        }
                    }
                    .flatMap { candidateMatches(value, current.device, clock.instant()) }
            }
        }
    }

    /** Account -> native -> target -> queue -> credential -> original runtime; no provider I/O. */
    private fun observe(
        actor: Actor,
        lease: CollaborationSpaceProvisioningLease,
        started: Instant,
        decrypt: Boolean,
    ): Result<Observation> {
        val request = lease.request
        val guarded = identities.lockAccount(actor.accountId, shared = false)
        if (guarded is Result.Failed) return guarded
        val locked = sessions.lock(request.nativeSessionId, actor.accountId)
        if (locked is Result.Failed) return locked
        val parent = (locked as Result.Success).value
        val allowed = admit(actor, parent, lease, started)
        if (allowed is Result.Failed) return allowed
        val found =
            registry.metadata(request.spaceId, actor.accountId, request.deviceId, lock = true)
        if (found is Result.Failed) return found
        val device = (found as Result.Success).value ?: return denied()
        val bound = registry.findBound(request.spaceId, actor.accountId, request.nativeSessionId)
        if (bound is Result.Failed) return bound
        if (
            (bound as Result.Success).value != device ||
                device.state != CollaborationDeviceState.PROVISIONING ||
                device.version !in 0..999998 ||
                device.platform.name != request.client.platform.name
        )
            return denied()
        val readIdentity =
            selections.selection(request.spaceId, actor.accountId, provider.serverName)
        if (readIdentity is Result.Failed) return readIdentity
        val input = (readIdentity as Result.Success).value ?: return denied()
        if (input.selected == null) return changed()
        val selected =
            selectCollaborationSpaceIdentity(
                request.spaceId,
                actor.accountId,
                provider.serverName,
                input,
                clock.instant(),
            )
        if (selected is Result.Failed) return selected
        val identity = (selected as Result.Success).value
        val validDevice =
            validateExistingCollaborationSpaceDevice(
                identity,
                device,
                request.nativeSessionId,
                device.platform,
                device.origin,
                device.legacy,
                device.legacyKeyFingerprint,
                clock.instant(),
            )
        if (validDevice is Result.Failed) return validDevice
        val readHead =
            provisioning.metadata(request.spaceId, actor.accountId, device.id, lock = true)
        if (readHead is Result.Failed) return readHead
        val head = (readHead as Result.Success).value ?: return lostLease()
        if (head.lease != lease || head.state != CollaborationSpaceProvisioningState.RUNNING)
            return lostLease()
        val readState = credentials.metadata(request.spaceId, actor.accountId, device.id)
        if (readState is Result.Failed) return readState
        val state = (readState as Result.Success).value
        if (
            state != null &&
                (state.spaceId != request.spaceId ||
                    state.accountId != actor.accountId ||
                    state.deviceId != device.id ||
                    state.nativeSessionId != request.nativeSessionId ||
                    state.matrixUserId != device.matrixUserId ||
                    state.matrixDeviceId != device.matrixDeviceId ||
                    state.clearedAt != null ||
                    state.generation !in 0..999999 ||
                    state.createdAt < device.createdAt ||
                    state.updatedAt > clock.instant())
        )
            return changed()
        val credential =
            if (decrypt && state != null && state.expiresAt > clock.instant().plusSeconds(15)) {
                val read = credentials.credential(state)
                if (read is Result.Failed) return read
                val value = (read as Result.Success).value
                if (value.state != state) return changed()
                value
            } else null
        val retained = observeRetained(device, identity, (allowed as Result.Success).value)
        if (retained is Result.Failed) return retained
        val refreshed = admit(actor, parent, lease, started)
        if (refreshed is Result.Failed) return refreshed
        val admission = (refreshed as Result.Success).value
        return Result.Success(
            Observation(
                requireNotNull(parent),
                device,
                identity,
                head,
                state,
                credential,
                (retained as Result.Success).value,
                admission,
                minOf(admission.expiresAt, lease.until, request.expiresAt, started.plusSeconds(60)),
            )
        )
    }

    private fun observeRetained(
        device: CollaborationSpaceDevice,
        identity: CollaborationSpaceIdentity,
        admission: CollaborationSpaceAdmission,
    ): Result<RetainedObservation?> {
        if (device.origin == CollaborationSpaceDeviceOrigin.SPACE) return Result.Success(null)
        val position = device.legacy ?: return changed()
        val fingerprint = device.legacyKeyFingerprint ?: return changed()
        val read =
            legacyParents.find(
                device.spaceId,
                device.accountId,
                admission.nativeSessionId,
                position,
            )
        if (read is Result.Failed) return read
        val source = (read as Result.Success).value
        val matched =
            collaborationLegacyFreezeOwner(
                identity,
                device,
                source,
                admission,
                device.version,
                fingerprint,
                clock.instant(),
            )
        if (matched is Result.Failed) return matched
        val owner = (matched as Result.Success).value
        val readRuntime = runtimes.find(owner, lock = true)
        if (readRuntime is Result.Failed) return readRuntime
        val runtime = (readRuntime as Result.Success).value ?: return waiting()
        val target = CollaborationLegacyRuntimeTarget(device.spaceId, device.id)
        val action = collaborationLegacyFreezeAction(runtime, owner, target, clock.instant())
        if (action is Result.Failed) return action
        if ((action as Result.Success).value == CollaborationLegacyFreezeAction.FREEZE)
            return waiting()
        val readSlots = runtimes.slots(owner)
        if (readSlots is Result.Failed) return readSlots
        val drained =
            collaborationLegacyRuntimeDrainStatus(
                runtime,
                owner,
                target,
                (readSlots as Result.Success).value,
                clock.instant(),
            )
        if (drained is Result.Failed) return drained
        if ((drained as Result.Success).value.activeRequests != 0) return waiting()
        val readGateway = gateways.find(device.spaceId, owner)
        if (readGateway is Result.Failed) return readGateway
        val gateway = (readGateway as Result.Success).value
        if (
            gateway != null &&
                (gateway.owner != owner ||
                    gateway.spaceId != device.spaceId ||
                    gateway.revokedAt != null ||
                    gateway.rotatedAt > clock.instant() ||
                    gateway.version !in 0..999999)
        )
            return changed()
        return Result.Success(RetainedObservation(requireNotNull(source), runtime, gateway))
    }

    private fun reobserve(
        actor: Actor,
        lease: CollaborationSpaceProvisioningLease,
        started: Instant,
        prepared: Observation,
    ): Result<Unit> =
        transactions.run(actor) {
            observe(actor, lease, started, decrypt = false).flatMap { current ->
                sameObservation(prepared, current).flatMap {
                    window(prepared, current, clock.instant())
                }
            }
        }

    private fun sameObservation(prepared: Observation, current: Observation): Result<Unit> =
        if (
            current.device != prepared.device ||
                current.head != prepared.head ||
                current.identity != prepared.identity ||
                current.state != prepared.state ||
                current.retained != prepared.retained
        )
            changed()
        else Result.Success(Unit)

    private fun completeRetained(
        actor: Actor,
        current: Observation,
        lease: CollaborationSpaceProvisioningLease,
        generation: Long,
        verified: VerifiedKeys?,
    ): Result<Unit> {
        val retained =
            current.retained ?: return if (verified == null) Result.Success(Unit) else changed()
        val proof = verified ?: return invalidKeys()
        val at = clock.instant()
        if (
            proof.observedAt > at ||
                proof.observedAt < at.minusSeconds(30) ||
                proof.observedAt < requireNotNull(retained.runtime.frozenAt) ||
                proof.fingerprint != current.device.legacyKeyFingerprint
        )
            return invalidKeys()
        return completions
            .complete(
                CompleteCollaborationLegacyRuntimeRequest(
                    retained.runtime.owner,
                    CollaborationLegacyRuntimeTarget(current.device.spaceId, current.device.id),
                    lease.request.nativeSessionVersion,
                    current.device.version + 1,
                    generation,
                    lease.request.revision,
                    retained.source.device.deviceVersion,
                    retained.source.device.credentialGeneration,
                    retained.gateway?.version,
                    proof.fingerprint,
                    proof.observedAt,
                )
            )
            .flatMap { completed ->
                if (
                    completed.owner != retained.runtime.owner ||
                        completed.target !=
                            CollaborationLegacyRuntimeTarget(
                                current.device.spaceId,
                                current.device.id,
                            ) ||
                        completed.matrixUserId != current.device.matrixUserId ||
                        completed.matrixDeviceId != current.device.matrixDeviceId ||
                        completed.fingerprint != proof.fingerprint ||
                        completed.nativeSessionVersion != lease.request.nativeSessionVersion ||
                        completed.targetDeviceVersion != current.device.version + 1 ||
                        completed.targetProviderGeneration != generation ||
                        completed.provisioningRevision != lease.request.revision ||
                        completed.keysObservedAt != proof.observedAt ||
                        completed.completedAt < proof.observedAt
                )
                    return@flatMap changed()
                val reservation =
                    CollaborationSpaceKeyPublication(
                        current.device.spaceId,
                        current.device.accountId,
                        current.device.id,
                        current.device.originNativeSessionId,
                        completed.matrixUserId,
                        completed.matrixDeviceId,
                        proof.fingerprint,
                        0,
                        completed.completedAt,
                    )
                // The provider identity was read and verified above. Completion, reservation and
                // acknowledgement share this transaction; a failed publication also rolls back
                // original-device retirement. No upload or new SDK request is required.
                publications
                    .find(reservation.spaceId, reservation.accountId, reservation.deviceId)
                    .flatMap { existing ->
                        if (existing != null) changed() else publications.reserve(reservation)
                    }
                    .flatMap { publications.publish(reservation, completed.completedAt) }
                    .flatMap {
                        publications.find(
                            reservation.spaceId,
                            reservation.accountId,
                            reservation.deviceId,
                        )
                    }
                    .flatMap { readback ->
                        if (
                            readback !=
                                reservation.copy(version = 1, publishedAt = completed.completedAt)
                        )
                            changed()
                        else
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "collaboration_space_device",
                                    current.device.id,
                                    "collaboration.space_device_keys_published",
                                    mapOf(
                                        "spaceId" to current.device.spaceId.toString(),
                                        "version" to "1",
                                    ),
                                ),
                            )
                    }
            }
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Collaboration activation interrupted")
    }

    private fun waiting() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_runtime_busy"))

    private fun invalidKeys() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"))

    private data class RetainedObservation(
        val source: CollaborationLegacyParent,
        val runtime: CollaborationLegacyRuntime,
        val gateway: CollaborationLegacyGateway?,
    )

    private data class VerifiedKeys(val fingerprint: String, val observedAt: Instant)

    private fun admit(
        actor: Actor,
        parent: NativeSession?,
        lease: CollaborationSpaceProvisioningLease,
        started: Instant,
    ): Result<CollaborationSpaceAdmission> {
        val acquired = identities.access(actor.accountId, null)
        if (acquired is Result.Failed) return acquired
        val current = spaces.current()
        if (current is Result.Failed) return current
        val now = clock.instant()
        val request = lease.request
        if (now < started || now < request.requestedAt || now >= started.plusSeconds(60))
            return changed()
        if (now >= lease.until || now >= request.expiresAt) return lostLease()
        return admitCollaborationSpaceSession(
                actor,
                (acquired as Result.Success).value,
                parent,
                request.nativeSessionId,
                request.nativeSessionVersion,
                (current as Result.Success).value,
                request.client,
                policy,
                security,
                now,
            )
            .flatMap { if (it.spaceId == request.spaceId) Result.Success(it) else denied() }
    }

    private fun candidateMatches(
        value: CollaborationMatrixCredential,
        device: CollaborationSpaceDevice,
        at: Instant,
    ): Result<Unit> {
        if (
            value.userId != device.matrixUserId ||
                value.deviceId != device.matrixDeviceId ||
                value.accessToken.length !in 1..2048 ||
                value.accessToken.any { it.code !in 33..126 } ||
                value.expiresAt <= at ||
                value.expiresAt > at.plusSeconds(600)
        )
            return Result.Failed(
                Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response")
            )
        return Result.Success(Unit)
    }

    private fun window(prepared: Observation, current: Observation, at: Instant): Result<Unit> =
        if (
            at < prepared.admission.admittedAt ||
                at < current.admission.admittedAt ||
                at >= minOf(prepared.deadline, current.deadline)
        )
            changed()
        else Result.Success(Unit)

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private fun lostLease() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_provisioning_lease_lost"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private class Observation(
        val parent: NativeSession,
        val device: CollaborationSpaceDevice,
        val identity: CollaborationSpaceIdentity,
        val head: CollaborationSpaceProvisioning,
        val state: CollaborationSpaceProviderState?,
        val credential: CollaborationSpaceProviderSession?,
        val retained: RetainedObservation?,
        val admission: CollaborationSpaceAdmission,
        val deadline: Instant,
    )
}
