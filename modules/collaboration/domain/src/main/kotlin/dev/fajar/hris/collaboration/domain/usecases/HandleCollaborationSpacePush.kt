package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Root-owned opaque wake admission. No provider credential or native push token is acquired. */
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
) {
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
