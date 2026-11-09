package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.NativeSessionRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * One current-target guard owner for independent readiness channels. Matrix sends outside SQL; PUSH
 * atomically hands off to the existing FCM queue, whose worker owns actual delivery.
 */
class FanOutCollaborationSpaceReadiness(
    private val queue: CollaborationSpaceReadinessQueueRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val transport: CollaborationSpaceMatrixReadinessRepository,
    private val pushBindings: CollaborationSpacePushBindingRepository,
    private val pushQueue: CollaborationSpacePushQueueRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val keys: CollaborationSpaceKeyRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val service: CollaborationServicePolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun claim(
        channel: CollaborationSpaceReadinessChannel,
        owner: UUID,
        limit: Int,
    ): Result<List<CollaborationSpaceReadinessLease>> {
        if (!enabled(channel)) return Result.Success(emptyList())
        if (owner == UUID(0, 0) || limit !in 1..4) return lost()
        return transactions.run(actor(UUID(0, 0))) {
            queue.claim(channel, owner, limit).flatMap { leases ->
                if (
                    leases.size > limit ||
                        leases.map { it.eventId }.distinct().size != leases.size ||
                        leases.map { it.token }.distinct().size != leases.size ||
                        leases.any {
                            !validCollaborationSpaceReadinessLease(it) ||
                                it.ownerId != owner ||
                                it.channel != channel ||
                                it.until <= clock.instant() ||
                                it.until > clock.instant().plusSeconds(60)
                        }
                )
                    lost()
                else Result.Success(leases)
            }
        }
    }

    fun fanOut(lease: CollaborationSpaceReadinessLease): Result<Unit> {
        if (!enabled(lease.channel)) return unavailable()
        if (!validCollaborationSpaceReadinessLease(lease)) return lost()
        val started = clock.instant()
        val budget = Budget(started, minOf(started.plusSeconds(20), lease.until), lease.channel)
        val page =
            step(budget) {
                transactions.run(actor(lease.publisherAccountId)) {
                    queue.find(lease, lock = false).flatMap { checkpoint ->
                        if (
                            checkpoint == null ||
                                checkpoint.lease != lease ||
                                !validCollaborationSpaceReadinessCheckpoint(checkpoint)
                        )
                            return@flatMap lost()
                        queue.targets(checkpoint, 8).flatMap { targets ->
                            if (
                                targets.size > 8 ||
                                    targets.map { it.nativeSessionId }.distinct().size !=
                                        targets.size ||
                                    targets.zipWithNext().any { (first, second) ->
                                        first.position >= second.position
                                    } ||
                                    targets.any {
                                        it.nativeSessionId == UUID(0, 0) ||
                                            it.position.accountId == UUID(0, 0) ||
                                            it.position.deviceId == UUID(0, 0) ||
                                            it.position.accountId == lease.publisherAccountId ||
                                            checkpoint.after?.let { after ->
                                                it.position <= after
                                            } == true
                                    }
                            )
                                lost()
                            else Result.Success(checkpoint to targets)
                        }
                    }
                }
            }
        if (page is Result.Failed) return page
        val (initial, targets) = (page as Result.Success).value
        var checkpoint = initial
        for (target in targets) {
            val expected = checkpoint
            if (lease.channel == CollaborationSpaceReadinessChannel.PUSH) {
                val handedOff =
                    withTarget(expected, target, budget) { admission ->
                        if (admission == null) return@withTarget advance(expected, target)
                        step(budget) {
                                pushBindings.find(
                                    lease.spaceId,
                                    target.position.accountId,
                                    target.position.deviceId,
                                )
                            }
                            .flatMap { binding ->
                                val lifetime =
                                    collaborationSpaceReadinessPushLifetime(
                                        expected,
                                        admission,
                                        binding,
                                        clock.instant(),
                                    )
                                if (lifetime == null) advance(expected, target)
                                else
                                    step(budget) {
                                            pushQueue.append(
                                                requireNotNull(binding),
                                                null,
                                                lifetime,
                                            )
                                        }
                                        .flatMap { advance(expected, target) }
                            }
                    }
                if (handedOff is Result.Failed) return handedOff
                checkpoint = expected.copy(after = target.position, attempts = 0)
                continue
            }
            val prepared =
                withTarget(expected, target, budget) { admission ->
                    if (admission == null)
                        return@withTarget advance(expected, target).map { Preparation.Suppressed }
                    if (admission.provider.expiresAt <= clock.instant())
                        return@withTarget Result.Success(Preparation.Deferred)
                    step(budget) { providers.credential(admission.provider) }
                        .map { credential ->
                            if (
                                collaborationSpaceReadinessCredential(
                                    admission,
                                    credential,
                                    clock.instant(),
                                )
                            )
                                Preparation.Ready(admission, credential)
                            else Preparation.Deferred
                        }
                }
            if (prepared is Result.Failed) return prepared
            when (val ready = (prepared as Result.Success).value) {
                Preparation.Deferred ->
                    return settle(expected, budget, complete = false, retry = true)
                Preparation.Suppressed -> Unit
                is Preparation.Ready -> {
                    val deadline =
                        minOf(
                            budget.deadline,
                            ready.admission.expiresAt,
                            ready.credential.state.expiresAt,
                        )
                    val control =
                        CollaborationSpaceReadinessControl(
                            lease.eventId,
                            lease.spaceId,
                            target.position.accountId,
                            target.nativeSessionId,
                            target.position.deviceId,
                            expected.publicationCutoff.toEpochMilli(),
                            expected.publicationRevision,
                        )
                    val sent =
                        step(Budget(budget.started, deadline, budget.channel)) {
                            transport.send(
                                control,
                                ready.admission.owner,
                                ready.credential.accessToken,
                                deadline,
                            )
                        }
                    val acknowledged =
                        withTarget(expected, target, budget) { current ->
                            if (current == null)
                                return@withTarget advance(expected, target).map { true }
                            if (
                                sent is Result.Failed ||
                                    !sameCollaborationSpaceReadinessAdmission(
                                        ready.admission,
                                        current,
                                        clock.instant(),
                                    ) ||
                                    current.provider.expiresAt <= clock.instant()
                            )
                                return@withTarget Result.Success(false)
                            step(budget) { providers.credential(current.provider) }
                                .flatMap { credential ->
                                    if (
                                        !collaborationSpaceReadinessCredential(
                                            current,
                                            credential,
                                            clock.instant(),
                                        ) || credential.accessToken != ready.credential.accessToken
                                    )
                                        Result.Success(false)
                                    else advance(expected, target).map { true }
                                }
                        }
                    if (acknowledged is Result.Failed) return acknowledged
                    if (!(acknowledged as Result.Success).value)
                        return settle(
                            expected,
                            budget,
                            complete = false,
                            retry = true,
                            failure = (sent as? Result.Failed)?.failure,
                        )
                }
            }
            checkpoint = expected.copy(after = target.position, attempts = 0)
        }
        return settle(checkpoint, budget, complete = targets.size < 8, retry = false)
    }

    /** Account -> native -> device -> gateway -> queue. No actor switching or nested owner. */
    private fun <T> withTarget(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        target: CollaborationReadinessTarget,
        budget: Budget,
        operation: (CollaborationSpaceReadinessAdmission?) -> Result<T>,
    ): Result<T> =
        step(budget) {
            transactions.run(actor(target.position.accountId)) {
                observe(checkpoint, target, budget).flatMap { snapshot ->
                    val admission =
                        collaborationSpaceReadinessAdmission(
                            checkpoint,
                            target,
                            snapshot,
                            policy,
                            provider.serverName,
                            clock.instant(),
                        )
                    step(budget) { queue.find(checkpoint.lease, lock = true) }
                        .flatMap { current ->
                            if (current != checkpoint) return@flatMap lost()
                            operation(admission).flatMap { result ->
                                // Device/native/gateway state is guarded and identity/publication
                                // is immutable.
                                // Group membership may change independently; reread it after the
                                // final write.
                                step(budget) { spaces.current() }
                                    .flatMap { space ->
                                        if (
                                            space != snapshot.currentSpaceId ||
                                                (admission != null &&
                                                    admission.expiresAt <= clock.instant())
                                        )
                                            lost()
                                        else Result.Success(result)
                                    }
                            }
                        }
                }
            }
        }

    private fun advance(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        target: CollaborationReadinessTarget,
    ): Result<Unit> =
        queue.advance(checkpoint, target.position).flatMap {
            if (it) Result.Success(Unit) else lost()
        }

    private fun observe(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        target: CollaborationReadinessTarget,
        budget: Budget,
    ): Result<CollaborationSpaceReadinessSnapshot> {
        val space = checkpoint.lease.spaceId
        val account = target.position.accountId
        val deviceId = target.position.deviceId
        val accountGuard = step(budget) { identities.lockAccount(account, shared = true) }
        if (accountGuard is Result.Failed) return accountGuard
        val parent = step(budget) { sessions.lock(target.nativeSessionId, account) }
        if (parent is Result.Failed) return parent
        val deviceRead = step(budget) { registry.metadata(space, account, deviceId, lock = true) }
        if (deviceRead is Result.Failed) return deviceRead
        val bound = step(budget) { registry.findBound(space, account, target.nativeSessionId) }
        if (bound is Result.Failed) return bound
        val gatewayRead = step(budget) { gateway.find(space, account, deviceId, lock = true) }
        if (gatewayRead is Result.Failed) return gatewayRead
        val access = step(budget) { identities.access(account, null) }
        if (access is Result.Failed) return access
        val currentSpace = step(budget) { spaces.current() }
        if (currentSpace is Result.Failed) return currentSpace
        val selection = step(budget) { selections.selection(space, account, provider.serverName) }
        if (selection is Result.Failed) return selection
        val publication = step(budget) { keys.find(space, account, deviceId) }
        if (publication is Result.Failed) return publication
        val state = step(budget) { providers.metadata(space, account, deviceId) }
        if (state is Result.Failed) return state
        val device = (deviceRead as Result.Success).value
        var source: CollaborationLegacyParent? = null
        var completion: CollaborationLegacyRuntimeCompletion? = null
        var head: CollaborationSpaceProvisioning? = null
        val legacy = device?.legacy
        if (device?.origin == CollaborationSpaceDeviceOrigin.LEGACY && legacy != null) {
            val original =
                step(budget) { legacyParents.find(space, account, target.nativeSessionId, legacy) }
            if (original is Result.Failed) return original
            source = (original as Result.Success).value
            val completed =
                step(budget) {
                    completions.find(
                        CollaborationLegacyRuntimeOwner(
                            legacy.companyId,
                            legacy.deviceId,
                            account,
                            target.nativeSessionId,
                        )
                    )
                }
            if (completed is Result.Failed) return completed
            completion = (completed as Result.Success).value
            val provisioned = step(budget) { provisioning.metadata(space, account, deviceId) }
            if (provisioned is Result.Failed) return provisioned
            head = (provisioned as Result.Success).value
        }
        return Result.Success(
            CollaborationSpaceReadinessSnapshot(
                (currentSpace as Result.Success).value,
                (access as Result.Success).value,
                (parent as Result.Success).value,
                device,
                (bound as Result.Success).value,
                (gatewayRead as Result.Success).value,
                (selection as Result.Success).value,
                (publication as Result.Success).value,
                (state as Result.Success).value,
                source,
                completion,
                head,
            )
        )
    }

    /** Deferral owns no target authority and runs only after the target transaction has ended. */
    private fun settle(
        checkpoint: CollaborationSpaceReadinessCheckpoint,
        budget: Budget,
        complete: Boolean,
        retry: Boolean,
        failure: Failure? = null,
    ): Result<Unit> =
        step(budget) {
            transactions.run(actor(checkpoint.lease.publisherAccountId)) {
                queue.find(checkpoint.lease, lock = true).flatMap { current ->
                    if (current != checkpoint) return@flatMap lost()
                    val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
                    val retryAt =
                        if (retry) collaborationSpaceReadinessRetryAt(checkpoint, failure, now)
                        else now
                    queue.release(checkpoint, complete || retryAt == null, retryAt ?: now).flatMap {
                        if (!it) lost() else step(budget) { Result.Success(Unit) }
                    }
                }
            }
        }

    private fun actor(account: UUID) =
        Actor(account, null, emptySet(), clock.instant(), UUID.randomUUID())

    private fun enabled(channel: CollaborationSpaceReadinessChannel) =
        service.enabled &&
            policy.enabled &&
            provider.enabled &&
            (channel != CollaborationSpaceReadinessChannel.PUSH || service.pushEnabled)

    private class Budget(
        val started: Instant,
        val deadline: Instant,
        val channel: CollaborationSpaceReadinessChannel,
    )

    private fun <T> step(budget: Budget, operation: () -> Result<T>): Result<T> {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Readiness delivery interrupted")
        if (
            !enabled(budget.channel) ||
                clock.instant() < budget.started ||
                clock.instant() >= budget.deadline
        )
            return lost()
        val result = operation()
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Readiness delivery interrupted")
        return if (
            !enabled(budget.channel) ||
                clock.instant() < budget.started ||
                clock.instant() >= budget.deadline
        )
            lost()
        else result
    }

    private fun lost() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_readiness_lease_lost"))

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_space_readiness_unavailable"))

    private sealed interface Preparation {
        data object Suppressed : Preparation

        data object Deferred : Preparation

        class Ready(
            val admission: CollaborationSpaceReadinessAdmission,
            val credential: CollaborationSpaceProviderSession,
        ) : Preparation
    }
}
