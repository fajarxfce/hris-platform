package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Root-owned opaque wake admission and fenced worker delivery. Content stays separately admitted.
 */
class HandleCollaborationSpacePush(
    private val bindings: CollaborationSpacePushBindingRepository,
    private val queue: CollaborationSpacePushQueueRepository,
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val spaces: CollaborationSpaceRepository,
    private val rooms: CollaborationSpaceRoomRepository,
    private val conversations: CollaborationSpaceConversationRepository,
    private val sessions: NativeSessionRepository,
    private val nativePush: NativePushRegistrationRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val service: CollaborationServicePolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
    private val push: PushRepository,
) {
    fun claim(ownerId: UUID, limit: Int): Result<List<CollaborationSpacePushLease>> {
        if (!service.enabled || !service.pushEnabled || !provider.enabled || !policy.enabled)
            return Result.Success(emptyList())
        if (ownerId == UUID(0, 0) || limit !in 1..8)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_push_batch")
            )
        return transactions.run(
            Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, UUID.randomUUID())
        ) {
            queue.claim(ownerId, limit, 60, 8).flatMap { heads ->
                val leases = heads.mapNotNull { it.lease }
                if (
                    heads.size > limit ||
                        heads.any { !validCollaborationSpacePushHead(it) } ||
                        leases.size != heads.size ||
                        leases.any { it.ownerId != ownerId } ||
                        leases.map { it.spaceId to it.deviceId }.toSet().size != leases.size
                )
                    collaborationPushLeaseLost()
                else Result.Success(leases)
            }
        }
    }

    /** Current owner is captured/acknowledged under guards; FCM runs outside every transaction. */
    fun deliver(lease: CollaborationSpacePushLease): Result<Unit> {
        if (!service.enabled || !service.pushEnabled || !provider.enabled || !policy.enabled)
            return unavailable()
        if (!validCollaborationSpacePushLease(lease)) return collaborationPushLeaseLost()
        val start = clock.instant()
        val budget = Budget(start, minOf(start.plusSeconds(20), lease.expiresAt))
        val prepared =
            withLease<PushPreparation?>(lease, budget) { target, head ->
                if (
                    head.expiresAt <= clock.instant() ||
                        target == CollaborationSpacePushTarget.Retired
                )
                    return@withLease finish(lease).map { null }
                if (target !is CollaborationSpacePushTarget.Ready)
                    return@withLease retryOrFinish(lease, head, budget).map { null }
                val event = collaborationSpacePushVisibleEvent(head, lease, clock.instant())
                if (event != null) {
                    val owned = ownedRoom(target, event, budget)
                    if (owned is Result.Failed) return@withLease owned
                    if (!(owned as Result.Success).value)
                        return@withLease finish(lease).map { null }
                }
                val acquired =
                    step(budget) {
                        nativePush.token(
                            lease.accountId,
                            lease.nativeSessionId,
                            target.registration.version,
                        )
                    }
                if (acquired is Result.Failed) return@withLease acquired
                val token = (acquired as Result.Success).value ?: return@withLease unavailable()
                confirm(target, budget).flatMap {
                    collaborationSpacePushMessage(
                            head,
                            lease,
                            target,
                            token,
                            clock.instant(),
                            budget.deadline,
                        )
                        .map { PushPreparation(target, it) }
                }
            }
        if (prepared is Result.Failed) return prepared
        val ready = (prepared as Result.Success).value ?: return Result.Success(Unit)
        if (!current(budget)) return unavailable()
        val outcome = push.send(ready.message)
        return withLease(lease, budget) { target, head ->
            val at = clock.instant()
            if (head.expiresAt <= at || target == CollaborationSpacePushTarget.Retired)
                return@withLease finish(lease)
            if (
                target !is CollaborationSpacePushTarget.Ready ||
                    !sameCollaborationSpacePushTarget(ready.target, target, at)
            )
                return@withLease retryOrFinish(lease, head, budget)
            val retry =
                (outcome as? Result.Failed)?.let {
                    collaborationPushRetryAt(it.failure, head.attempts, at, head.expiresAt)
                }
            val ack = if (retry == null) finish(lease) else defer(lease, retry)
            if (ack is Result.Failed) return@withLease ack
            // Failed confirmation rolls back the actual captured-revision acknowledgment.
            confirm(target, budget).flatMap {
                if (outcome is Result.Success && outcome.value == PushOutcome.UNREGISTERED)
                    disableExactRegistration(target, budget)
                else Result.Success(Unit)
            }
        }
    }

    private class PushPreparation(
        val target: CollaborationSpacePushTarget.Ready,
        val message: PushMessage,
    )

    private fun <T> withLease(
        lease: CollaborationSpacePushLease,
        budget: Budget,
        operation: (CollaborationSpacePushTarget, CollaborationSpacePushHead) -> Result<T>,
    ): Result<T> =
        step(budget) {
            val actor = Actor(lease.accountId, null, emptySet(), budget.started, UUID.randomUUID())
            transactions.run(actor) {
                val read =
                    step(budget) { bindings.find(lease.spaceId, lease.accountId, lease.deviceId) }
                if (read is Result.Failed) return@run read
                val binding =
                    (read as Result.Success).value ?: return@run collaborationPushLeaseLost()
                if (binding.nativeSessionId != lease.nativeSessionId)
                    return@run collaborationPushLeaseLost()
                observe(binding, budget).flatMap { target ->
                    val loaded =
                        step(budget) {
                            queue.find(lease.spaceId, lease.accountId, lease.deviceId, lock = true)
                        }
                    if (loaded is Result.Failed) return@flatMap loaded
                    val head =
                        (loaded as Result.Success).value
                            ?: return@flatMap collaborationPushLeaseLost()
                    if (
                        !validCollaborationSpacePushHead(head) ||
                            head.lease != lease ||
                            head.nativeSessionId != lease.nativeSessionId ||
                            lease.expiresAt <= clock.instant()
                    )
                        return@flatMap collaborationPushLeaseLost()
                    operation(target, head).flatMap {
                        if (current(budget) && clock.instant() < lease.expiresAt) Result.Success(it)
                        else collaborationPushLeaseLost()
                    }
                }
            }
        }

    private fun finish(lease: CollaborationSpacePushLease): Result<Unit> =
        queue.finish(lease).flatMap {
            if (it) Result.Success(Unit) else collaborationPushLeaseLost()
        }

    private fun defer(lease: CollaborationSpacePushLease, at: Instant): Result<Unit> =
        queue.defer(lease, at).flatMap {
            if (it) Result.Success(Unit) else collaborationPushLeaseLost()
        }

    private fun retryOrFinish(
        lease: CollaborationSpacePushLease,
        head: CollaborationSpacePushHead,
        budget: Budget,
    ): Result<Unit> {
        if (!current(budget)) return unavailable()
        val at =
            collaborationPushRetryAt(
                Failure(FailureKind.UNAVAILABLE, "collaboration_push_unavailable"),
                head.attempts,
                clock.instant(),
                head.expiresAt,
            )
        return if (at == null) finish(lease) else defer(lease, at)
    }

    /**
     * Current target was confirmed after queue ACK under account/native/device/gateway guards. Only
     * this exact local mutation is allowed; readback and final directory/time checks share its
     * transaction. An intentionally disabled registration must never be readmitted as Ready.
     */
    private fun disableExactRegistration(
        target: CollaborationSpacePushTarget.Ready,
        budget: Budget,
    ): Result<Unit> {
        val old = target.registration
        val expected =
            old.copy(
                enabled = false,
                version = old.version + 1,
                updatedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
            )
        return step(budget) { nativePush.save(expected, null, old.version) }
            .flatMap { saved ->
                if (!saved) return@flatMap collaborationPushLeaseLost()
                step(budget) { nativePush.find(old.accountId, old.sessionId) }
                    .flatMap readback@{ current ->
                        if (current != expected) return@readback collaborationPushLeaseLost()
                        step(budget) { spaces.current() }
                            .flatMap { space ->
                                if (
                                    space == target.binding.spaceId &&
                                        clock.instant() < target.expiresAt
                                )
                                    Result.Success(Unit)
                                else unavailable()
                            }
                    }
            }
    }

    /**
     * Validate the complete bounded input before any write; partial committed keys are retryable.
     */
    fun accept(
        hint: CollaborationSpacePushHint,
        correlationId: UUID,
        deadline: Instant,
    ): Result<CollaborationSpacePushReceipt> {
        if (!service.enabled || !service.pushEnabled || !provider.enabled) return unavailable()
        val started = clock.instant()
        val budget = Budget(started, deadline)
        if (deadline <= started || deadline > started.plusSeconds(30)) return unavailable()
        if (
            hint.keys.size !in 1..32 ||
                hint.keys.any { !validCollaborationSpacePushKey(it.value) } ||
                hint.keys.map { it.value }.toSet().size != hint.keys.size ||
                hint.event?.let { !validCollaborationPushEvent(it) } == true
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_push"))
        // Temporarily disabling group rollout must not delete retained provider capabilities.
        if (!policy.enabled) return Result.Success(CollaborationSpacePushReceipt(emptyList()))
        val rejected = mutableListOf<CollaborationSpacePushKey>()
        val lookupScope = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        for (key in hint.keys) {
            val lookup =
                step(budget) {
                    transactions.run(lookupScope) { step(budget) { bindings.findKey(key) } }
                }
            if (lookup is Result.Failed) return lookup
            val initial = (lookup as Result.Success).value
            if (initial == null) {
                rejected += key
                continue
            }
            val scope = Actor(initial.accountId, null, emptySet(), started, correlationId)
            val handled =
                step(budget) {
                    transactions.run(scope) {
                        observe(initial, budget)
                            .flatMap { target ->
                                when (target) {
                                    CollaborationSpacePushTarget.Retired -> Result.Success(true)
                                    CollaborationSpacePushTarget.Suppressed -> Result.Success(false)
                                    is CollaborationSpacePushTarget.Ready ->
                                        retain(hint.event, target, budget).map { false }
                                }
                            }
                            .flatMap { if (current(budget)) Result.Success(it) else unavailable() }
                    }
                }
            if (handled is Result.Failed) return handled
            if ((handled as Result.Success).value) rejected += key
        }
        return if (current(budget)) Result.Success(CollaborationSpacePushReceipt(rejected))
        else unavailable()
    }

    private fun retain(
        event: CollaborationPushEvent?,
        target: CollaborationSpacePushTarget.Ready,
        budget: Budget,
    ): Result<Unit> {
        if (event != null) {
            val owned = ownedRoom(target, event, budget)
            if (owned is Result.Failed) return owned
            // A forged/unowned event does not permanently retire a valid pusher.
            if (!(owned as Result.Success).value) return Result.Success(Unit)
        }
        return confirm(target, budget)
            .flatMap { step(budget) { queue.append(target.binding, event, 86400) } }
            .flatMap { confirm(target, budget) }
    }

    private fun confirm(target: CollaborationSpacePushTarget.Ready, budget: Budget): Result<Unit> =
        observe(target.binding, budget).flatMap {
            if (sameCollaborationSpacePushTarget(target, it, clock.instant())) Result.Success(Unit)
            else unavailable()
        }

    /** Pair metadata is immutable; receiving eligibility is deliberately not a history ACL. */
    private fun ownedRoom(
        target: CollaborationSpacePushTarget.Ready,
        event: CollaborationPushEvent,
        budget: Budget,
    ): Result<Boolean> {
        if (!validCollaborationPushEvent(event)) return Result.Success(false)
        val device = target.device
        val owner =
            CollaborationSpaceHistoryOwner(
                device.spaceId,
                device.accountId,
                device.id,
                provider.serverName,
                device.matrixUserId,
                device.matrixDeviceId,
                device.legacy,
            )
        if (!validCollaborationSpaceHistoryIdentity(owner)) return unavailable()
        return step(budget) {
                rooms.boundRoom(owner.spaceId, owner.accountId, owner.serverName, event.roomId)
            }
            .flatMap { bound ->
                if (bound != null) {
                    snapshotCollaborationSpaceBoundRoom(
                            owner.spaceId,
                            owner.accountId,
                            owner.serverName,
                            event.roomId,
                            bound,
                            clock.instant(),
                        )
                        .map { validCollaborationSpaceHistoryOwner(owner, it) }
                } else {
                    val company =
                        collaborationSpaceHistoryOriginalCompany(owner)
                            ?: return@flatMap Result.Success(false)
                    step(budget) {
                            conversations.legacyBoundRoom(
                                owner.spaceId,
                                owner.accountId,
                                company,
                                owner.serverName,
                                event.roomId,
                            )
                        }
                        .flatMap { original ->
                            if (original == null) Result.Success(false)
                            else
                                snapshotCollaborationSpaceOriginalReadRoom(
                                        owner,
                                        original,
                                        clock.instant(),
                                    )
                                    .map { it.room?.roomId == event.roomId }
                        }
                }
            }
    }

    /**
     * Account -> native -> retained device -> gateway -> binding; current eligibility is reread.
     */
    private fun observe(
        initial: CollaborationSpacePushBinding,
        budget: Budget,
    ): Result<CollaborationSpacePushTarget> {
        val guarded = step(budget) { identities.lockAccount(initial.accountId, shared = true) }
        if (guarded is Result.Failed) return guarded
        val parentRead = step(budget) { sessions.lock(initial.nativeSessionId, initial.accountId) }
        if (parentRead is Result.Failed) return parentRead
        val deviceRead =
            step(budget) {
                registry.metadata(initial.spaceId, initial.accountId, initial.deviceId, lock = true)
            }
        if (deviceRead is Result.Failed) return deviceRead
        val boundRead =
            step(budget) {
                registry.findBound(initial.spaceId, initial.accountId, initial.nativeSessionId)
            }
        if (boundRead is Result.Failed) return boundRead
        val gatewayRead =
            step(budget) {
                gateway.find(initial.spaceId, initial.accountId, initial.deviceId, lock = true)
            }
        if (gatewayRead is Result.Failed) return gatewayRead
        val bindingGuard = step(budget) { bindings.lock(initial.spaceId, initial.deviceId) }
        if (bindingGuard is Result.Failed) return bindingGuard
        val bindingRead =
            step(budget) { bindings.find(initial.spaceId, initial.accountId, initial.deviceId) }
        if (bindingRead is Result.Failed) return bindingRead
        val binding = (bindingRead as Result.Success).value ?: return unavailable()
        if (!sameCollaborationSpacePushBinding(initial, binding)) return unavailable()
        val access = step(budget) { identities.access(binding.accountId, null) }
        if (access is Result.Failed) return access
        val currentSpace = step(budget) { spaces.current() }
        if (currentSpace is Result.Failed) return currentSpace
        val selection =
            step(budget) {
                selections.selection(binding.spaceId, binding.accountId, provider.serverName)
            }
        if (selection is Result.Failed) return selection
        val state =
            step(budget) {
                providers.metadata(binding.spaceId, binding.accountId, binding.deviceId)
            }
        if (state is Result.Failed) return state
        val registration =
            step(budget) { nativePush.find(binding.accountId, binding.nativeSessionId) }
        if (registration is Result.Failed) return registration
        val device = (deviceRead as Result.Success).value
        var source: CollaborationLegacyParent? = null
        var completion: CollaborationLegacyRuntimeCompletion? = null
        var provision: CollaborationSpaceProvisioning? = null
        if (device?.origin == CollaborationSpaceDeviceOrigin.LEGACY && device.legacy != null) {
            val legacy = device.legacy
            val old =
                step(budget) {
                    legacyParents.find(
                        binding.spaceId,
                        binding.accountId,
                        binding.nativeSessionId,
                        legacy,
                    )
                }
            if (old is Result.Failed) return old
            source = (old as Result.Success).value
            val completed =
                step(budget) {
                    completions.find(
                        CollaborationLegacyRuntimeOwner(
                            legacy.companyId,
                            legacy.deviceId,
                            binding.accountId,
                            binding.nativeSessionId,
                        )
                    )
                }
            if (completed is Result.Failed) return completed
            completion = (completed as Result.Success).value
            val queued =
                step(budget) {
                    provisioning.metadata(binding.spaceId, binding.accountId, binding.deviceId)
                }
            if (queued is Result.Failed) return queued
            provision = (queued as Result.Success).value
        }
        return step(budget) {
            Result.Success(
                collaborationSpacePushTarget(
                    binding,
                    (access as Result.Success).value,
                    (parentRead as Result.Success).value,
                    device,
                    (boundRead as Result.Success).value,
                    (gatewayRead as Result.Success).value,
                    (registration as Result.Success).value,
                    (currentSpace as Result.Success).value,
                    (selection as Result.Success).value,
                    (state as Result.Success).value,
                    source,
                    completion,
                    provision,
                    policy,
                    provider.serverName,
                    clock.instant(),
                )
            )
        }
    }

    private class Budget(val started: Instant, val deadline: Instant)

    private fun current(budget: Budget): Boolean =
        clock.instant().let { it >= budget.started && it < budget.deadline }

    private fun <T> step(budget: Budget, operation: () -> Result<T>): Result<T> {
        if (!current(budget)) return unavailable()
        val result = operation()
        return if (current(budget)) result else unavailable()
    }

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_push_unavailable"))
}
