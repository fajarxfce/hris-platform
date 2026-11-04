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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Native recovery of an expired provider head; candidate I/O never holds SQL guards. */
class RenewNativeCollaborationSpaceProvider(
    private val requests: CollaborationSpaceRequestRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val matrix: CollaborationSpaceMatrixRepository,
    private val keys: CollaborationMatrixKeyRepository,
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
        client: ClientVersion?,
    ): Result<CollaborationSpaceProviderRenewal> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val enabled = validateCollaborationSpaceClient(client, policy)
        if (enabled is Result.Failed) return enabled
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (expectedDeviceVersion !in 0..999999) return changed()
        val currentClient = requireNotNull(client)
        val context =
            Context(
                actor,
                parentId,
                parentVersion,
                spaceId,
                deviceId,
                expectedDeviceVersion,
                currentClient,
                clock.instant(),
                OperationKey(
                    "collaboration.space_provider_renewal",
                    operationId,
                    listOf(
                        spaceId.toString(),
                        parentId.toString(),
                        deviceId.toString(),
                        expectedDeviceVersion.toString(),
                        currentClient.platform.name,
                        currentClient.build.toString(),
                    ),
                ),
            )
        val preparation = transactions.run(actor) { prepare(context) }
        if (preparation is Result.Failed) return preparation
        val prepared = (preparation as Result.Success).value
        prepared.receipt?.let {
            return Result.Success(it)
        }
        val lease = requireNotNull(prepared.lease)
        var originalFailure: Throwable? = null
        try {
            return exchange(context, prepared, lease)
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            // A stopped thread leaves only a finite operational lease. Never logout a
            // retained Matrix device: that deletes its tokens and E2EE keys together.
            if (!Thread.currentThread().isInterrupted) {
                try {
                    transactions.run(actor) { requests.release(lease) }
                } catch (cleanup: Throwable) {
                    val original = originalFailure
                    if (original == null) throw cleanup
                    if (original !== cleanup) original.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun prepare(context: Context): Result<Preparation> {
        val replay = operations.lockAndReplay(context.actor, context.key)
        if (replay is Result.Failed) return replay
        val read = observe(context)
        if (read is Result.Failed) return read
        val observed = (read as Result.Success).value
        val receipt = (replay as Result.Success).value
        if (receipt != null) {
            if (receipt.id != observed.device.id || receipt.version != observed.state.generation)
                return changed()
            if (observed.state.expiresAt <= clock.instant()) return renewalRequired()
            return Result.Success(Preparation(observed, receipt = response(observed.state)))
        }
        if (observed.device.version != context.expectedDeviceVersion) return changed()
        if (observed.state.expiresAt > clock.instant().plusSeconds(60)) {
            return record(context, observed.state).flatMap {
                observe(context).flatMap { current ->
                    if (!same(observed, current) || current.state.expiresAt <= clock.instant())
                        changed()
                    else Result.Success(Preparation(current, receipt = response(current.state)))
                }
            }
        }
        if (observed.state.generation >= 999999) return changed()
        val position =
            CollaborationSpaceRequestPosition(
                CollaborationSpaceRequestOwner(
                    context.spaceId,
                    context.actor.accountId,
                    context.deviceId,
                    context.parentId,
                ),
                CollaborationSpaceRequestKind.PROVIDER_RENEWAL,
            )
        val until =
            minOf(context.started.plusSeconds(30), observed.admission.expiresAt)
                .truncatedTo(ChronoUnit.MICROS)
        return requests.claim(position, until).flatMap { lease ->
            if (lease == null)
                return@flatMap Result.Failed(
                    Failure(
                        FailureKind.UNAVAILABLE,
                        "collaboration_space_provider_busy",
                        parameters = mapOf("retryAfterSeconds" to "2"),
                    )
                )
            observe(context).flatMap { current ->
                if (!same(observed, current)) changed()
                else validateLease(context, current, lease).map { Preparation(current, lease) }
            }
        }
    }

    private fun exchange(
        context: Context,
        prepared: Preparation,
        lease: CollaborationSpaceRequestLease,
    ): Result<CollaborationSpaceProviderRenewal> {
        val initial = prepared.observation
        // Recheck after commit before making the independently bounded provider call.
        val before = reobserve(context, initial, lease)
        if (before is Result.Failed) return before
        val candidate =
            matrix.login(
                CollaborationSpaceMatrixLogin(
                    context.spaceId,
                    context.actor.accountId,
                    context.deviceId,
                    initial.identity.legacy?.companyId,
                    initial.device.legacy,
                    initial.admission.admittedAt,
                    lease.expiresAt,
                )
            )
        checkInterrupted()
        val after = reobserve(context, initial, lease)
        if (after is Result.Failed) return after
        if (candidate is Result.Failed) return candidate
        val value = (candidate as Result.Success).value
        val valid = candidateMatches(value, initial.device)
        if (valid is Result.Failed) return valid
        if (initial.device.origin == CollaborationSpaceDeviceOrigin.LEGACY) {
            val read =
                keys.ownDeviceKeys(value.userId, value.deviceId, value.accessToken, lease.expiresAt)
            checkInterrupted()
            val current = reobserve(context, initial, lease)
            if (current is Result.Failed) return current
            if (read is Result.Failed) return read
            val deviceKeys = (read as Result.Success).value ?: return invalidKeys()
            val checked = validateCollaborationDeviceKeys(deviceKeys, value.userId, value.deviceId)
            if (
                checked is Result.Failed ||
                    (checked as Result.Success).value != initial.device.legacyKeyFingerprint
            )
                return invalidKeys()
        }
        return transactions.run(context.actor) {
            val replay = operations.lockAndReplay(context.actor, context.key)
            if (replay is Result.Failed) return@run replay
            if ((replay as Result.Success).value != null) return@run changed()
            val read = observe(context)
            if (read is Result.Failed) return@run read
            val current = (read as Result.Success).value
            if (!same(initial, current)) return@run changed()
            val ownership = validateLease(context, current, lease)
            if (ownership is Result.Failed) return@run ownership
            val live = candidateMatches(value, current.device)
            if (live is Result.Failed) return@run live
            val next =
                current.state.copy(
                    generation = current.state.generation + 1,
                    updatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
                    expiresAt = value.expiresAt.truncatedTo(ChronoUnit.MICROS),
                )
            providers
                .rotate(
                    CollaborationSpaceProviderSession(next, value.accessToken),
                    current.state.generation,
                )
                .flatMap { record(context, next) }
                .flatMap {
                    journal.record(
                        context.actor,
                        ChangeRecord(
                            "collaboration_space_device",
                            context.deviceId,
                            "collaboration.space_provider_renewed",
                            mapOf(
                                "spaceId" to context.spaceId.toString(),
                                "providerGeneration" to next.generation.toString(),
                            ),
                        ),
                    )
                }
                .flatMap { observe(context) }
                .flatMap { finished ->
                    if (
                        finished.device != initial.device ||
                            finished.identity != initial.identity ||
                            finished.state != next
                    )
                        changed()
                    else validateLease(context, finished, lease)
                }
                .flatMap { candidateMatches(value, current.device) }
                .map { response(next) }
        }
    }

    private fun reobserve(
        context: Context,
        initial: Observation,
        lease: CollaborationSpaceRequestLease,
    ): Result<Unit> =
        transactions.run(context.actor) {
            val replay = operations.lockAndReplay(context.actor, context.key)
            if (replay is Result.Failed) return@run replay
            if ((replay as Result.Success).value != null) return@run changed()
            observe(context).flatMap { current ->
                if (!same(initial, current)) changed() else validateLease(context, current, lease)
            }
        }

    private fun validateLease(
        context: Context,
        observed: Observation,
        lease: CollaborationSpaceRequestLease,
    ): Result<Unit> {
        val owner =
            CollaborationSpaceRequestOwner(
                context.spaceId,
                context.actor.accountId,
                context.deviceId,
                context.parentId,
            )
        return requests.find(owner, 0, lock = true).flatMap { stored ->
            val now = clock.instant()
            if (
                stored != lease ||
                    lease.position !=
                        CollaborationSpaceRequestPosition(
                            owner,
                            CollaborationSpaceRequestKind.PROVIDER_RENEWAL,
                        ) ||
                    lease.slot != 0 ||
                    lease.token == UUID(0, 0) ||
                    lease.startedAt > now ||
                    lease.expiresAt > context.started.plusSeconds(30) ||
                    now < observed.admission.admittedAt ||
                    now >= minOf(lease.expiresAt, observed.admission.expiresAt)
            )
                Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_request_lost"))
            else Result.Success(Unit)
        }
    }

    /** Operation -> account -> native -> device -> request; provider metadata grants no access. */
    private fun observe(context: Context): Result<Observation> {
        val actor = context.actor
        val guarded = identities.lockAccount(actor.accountId, shared = true)
        if (guarded is Result.Failed) return guarded
        val locked = sessions.lock(context.parentId, actor.accountId)
        if (locked is Result.Failed) return locked
        val parent = (locked as Result.Success).value ?: return nativeAuthenticationRequired()
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
                    context.parentVersion,
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
        // Only this explicit renewal accepts expired provider metadata. Retired or foreign
        // provenance never becomes renewable, and issuance still requires a live provider.
        val at = clock.instant()
        if (
            state.spaceId != scope.spaceId ||
                state.accountId != actor.accountId ||
                state.deviceId != device.id ||
                state.nativeSessionId != scope.nativeSessionId ||
                state.matrixUserId != device.matrixUserId ||
                state.matrixDeviceId != device.matrixDeviceId ||
                state.generation !in 0..999999 ||
                state.clearedAt != null ||
                state.createdAt < device.createdAt ||
                state.updatedAt < state.createdAt ||
                state.updatedAt > at ||
                state.expiresAt <= state.updatedAt
        )
            return denied()
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
        return admission().map { Observation(device, identity, state, it) }
    }

    private fun same(first: Observation, second: Observation) =
        first.device == second.device &&
            first.identity == second.identity &&
            first.state == second.state

    private fun candidateMatches(
        value: CollaborationMatrixCredential,
        device: CollaborationSpaceDevice,
    ): Result<Unit> {
        val now = clock.instant()
        if (
            value.userId != device.matrixUserId ||
                value.deviceId != device.matrixDeviceId ||
                value.accessToken.length !in 1..2048 ||
                value.accessToken.any { it.code !in 33..126 } ||
                value.expiresAt <= now.plusSeconds(60) ||
                value.expiresAt > now.plusSeconds(600)
        )
            return Result.Failed(
                Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response")
            )
        return Result.Success(Unit)
    }

    private fun record(context: Context, state: CollaborationSpaceProviderState) =
        operations.record(
            context.actor,
            context.key,
            MutationReceipt(state.deviceId, state.generation),
        )

    private fun response(state: CollaborationSpaceProviderState) =
        CollaborationSpaceProviderRenewal(state.deviceId, state.generation)

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Provider renewal interrupted")
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_provider_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun renewalRequired() =
        Result.Failed(
            Failure(FailureKind.CONFLICT, "collaboration_space_provider_renewal_required")
        )

    private fun invalidKeys() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_keys"))

    private data class Context(
        val actor: Actor,
        val parentId: UUID,
        val parentVersion: Long,
        val spaceId: UUID,
        val deviceId: UUID,
        val expectedDeviceVersion: Long,
        val client: ClientVersion,
        val started: Instant,
        val key: OperationKey,
    )

    private class Observation(
        val device: CollaborationSpaceDevice,
        val identity: CollaborationSpaceIdentity,
        val state: CollaborationSpaceProviderState,
        val admission: CollaborationSpaceAdmission,
    )

    private class Preparation(
        val observation: Observation,
        val lease: CollaborationSpaceRequestLease? = null,
        val receipt: CollaborationSpaceProviderRenewal? = null,
    )
}
