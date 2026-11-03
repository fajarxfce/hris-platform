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
                        null,
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
        if (candidate is Result.Failed) return candidate
        val value = (candidate as Result.Success).value
        val valid = candidateMatches(value, prepared.device, clock.instant())
        if (valid is Result.Failed) return valid
        // Synapse logout removes all tokens and E2EE keys for this device. A losing candidate
        // must expire within the provider's bounded lifetime, without cleanup logout.
        return transactions.run(actor) {
            observe(actor, lease, started, decrypt = false).flatMap { current ->
                if (
                    current.device != prepared.device ||
                        current.head != prepared.head ||
                        current.identity != prepared.identity ||
                        current.state != prepared.state
                )
                    return@flatMap changed()
                val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
                val window = window(prepared, current, clock.instant())
                if (window is Result.Failed) return@flatMap window
                val stillValid = candidateMatches(value, current.device, clock.instant())
                if (stillValid is Result.Failed) return@flatMap stillValid
                val prior = current.state
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
                                    (prior?.generation ?: -1) + 1,
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

    /** Account -> native parent -> device -> queue -> credential; claim committed before entry. */
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
        // Retained-device activation needs its own old/new gateway and SDK ownership transfer.
        // Metadata continuity alone cannot authorize two runtimes to use one E2EE device.
        if (device.origin != CollaborationSpaceDeviceOrigin.SPACE)
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "collaboration_device_migration_required")
            )
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
                admission,
                minOf(admission.expiresAt, lease.until, request.expiresAt, started.plusSeconds(60)),
            )
        )
    }

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
        val admission: CollaborationSpaceAdmission,
        val deadline: Instant,
    )
}
