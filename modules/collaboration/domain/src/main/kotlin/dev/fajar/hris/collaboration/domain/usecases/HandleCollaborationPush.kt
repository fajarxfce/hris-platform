package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.EffectiveClientPolicy
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Opaque wake admission and fenced delivery. Content and reply keep separate assurance gates. */
class HandleCollaborationPush(
    private val bindings: CollaborationPushBindingRepository,
    private val queue: CollaborationPushQueueRepository,
    private val gateway: CollaborationGatewaySessionRepository,
    private val registry: CollaborationRegistryRepository,
    private val rooms: CollaborationRoomScopeRepository,
    private val sessions: NativeSessionRepository,
    private val nativePush: NativePushRegistrationRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
    private val push: PushRepository,
) {
    fun claim(ownerId: UUID, limit: Int): Result<List<CollaborationPushLease>> {
        if (!service.enabled || !service.pushEnabled || !provider.enabled)
            return Result.Success(emptyList())
        if (limit !in 1..8)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_push_batch")
            )
        val actor = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, UUID.randomUUID())
        return transactions.run(actor) {
            queue.claim(ownerId, limit, 60, 8).flatMap { heads ->
                val leases = heads.mapNotNull { it.lease }
                if (leases.size != heads.size || leases.any { it.ownerId != ownerId })
                    collaborationPushLeaseLost()
                else Result.Success(leases)
            }
        }
    }

    /**
     * Prepare/acknowledge under current guards, with provider I/O strictly outside transactions.
     */
    fun deliver(lease: CollaborationPushLease): Result<Unit> {
        if (!service.enabled || !service.pushEnabled || !provider.enabled) return unavailable()
        val started = clock.instant()
        val budget = Budget(started, minOf(started.plusSeconds(20), lease.expiresAt))
        if (!current(budget)) return collaborationPushLeaseLost()
        val prepared =
            withLease<Preparation?>(lease, budget) { observation, head ->
                val target = observation.target(clock.instant())
                if (target !is CollaborationPushTarget.Ready)
                    return@withLease finish(lease).map { null }
                val event = collaborationPushVisibleEvent(head, lease, clock.instant())
                if (event != null) {
                    val room =
                        step(budget) {
                            rooms.find(
                                head.companyId,
                                head.accountId,
                                provider.serverName,
                                event.roomId,
                            )
                        }
                    if (room is Result.Failed) return@withLease room
                    if (
                        !collaborationPushRoomOwned(
                            target.binding,
                            event,
                            (room as Result.Success).value,
                            provider.serverName,
                        )
                    )
                        return@withLease finish(lease).map { null }
                }
                val acquired =
                    step(budget) {
                        nativePush.token(
                            head.accountId,
                            head.nativeSessionId,
                            target.registration.version,
                        )
                    }
                if (acquired is Result.Failed) return@withLease acquired
                val token = (acquired as Result.Success).value ?: return@withLease unavailable()
                val now = clock.instant()
                if (!sameCollaborationPushTarget(target, observation.target(now), now))
                    return@withLease unavailable()
                collaborationPushMessage(head, lease, target, token, now, budget.deadline).map {
                    Preparation(target, it)
                }
            }
        if (prepared is Result.Failed) return prepared
        val ready = (prepared as Result.Success).value ?: return Result.Success(Unit)
        if (!current(budget)) return unavailable()
        val outcome = push.send(ready.message)
        return withLease(lease, budget) { observation, head ->
            val now = clock.instant()
            val current = observation.target(now)
            if (current !is CollaborationPushTarget.Ready) return@withLease finish(lease)
            if (!sameCollaborationPushTarget(ready.target, current, now))
                return@withLease defer(lease, now)
            if (outcome is Result.Success && outcome.value == PushOutcome.UNREGISTERED) {
                val registration = current.registration
                val saved =
                    step(budget) {
                        nativePush.save(
                            registration.copy(
                                enabled = false,
                                version = registration.version + 1,
                                updatedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                            ),
                            null,
                            registration.version,
                        )
                    }
                if (saved is Result.Failed) return@withLease saved
                if (!(saved as Result.Success).value) return@withLease collaborationPushLeaseLost()
            }
            val retry =
                (outcome as? Result.Failed)?.let {
                    collaborationPushRetryAt(it.failure, head.attempts, now, head.expiresAt)
                }
            val completed = if (retry == null) finish(lease) else defer(lease, retry)
            if (completed is Result.Failed) return@withLease completed
            val after = clock.instant()
            // These immutable inputs were captured under the held guards. An intentional local
            // UNREGISTERED mutation must not invalidate its own atomic queue acknowledgment.
            if (!sameCollaborationPushTarget(ready.target, observation.target(after), after))
                unavailable()
            else Result.Success(Unit)
        }
    }

    private class Preparation(val target: CollaborationPushTarget.Ready, val message: PushMessage)

    private fun <T> withLease(
        lease: CollaborationPushLease,
        budget: Budget,
        operation: (Observation, CollaborationPushHead) -> Result<T>,
    ): Result<T> {
        val actor =
            Actor(lease.accountId, lease.companyId, emptySet(), budget.started, UUID.randomUUID())
        return step(budget) {
            transactions.run(actor) {
                val lookup =
                    step(budget) { bindings.find(lease.companyId, lease.accountId, lease.deviceId) }
                if (lookup is Result.Failed) return@run lookup
                val initial =
                    (lookup as Result.Success).value ?: return@run collaborationPushLeaseLost()
                if (initial.nativeSessionId != lease.nativeSessionId)
                    return@run collaborationPushLeaseLost()
                observe(initial, budget).flatMap { observation ->
                    val loaded =
                        step(budget) {
                            queue.find(
                                lease.companyId,
                                lease.accountId,
                                lease.deviceId,
                                lock = true,
                            )
                        }
                    if (loaded is Result.Failed) return@flatMap loaded
                    val head =
                        (loaded as Result.Success).value
                            ?: return@flatMap collaborationPushLeaseLost()
                    if (
                        head.lease != lease ||
                            head.nativeSessionId != lease.nativeSessionId ||
                            lease.expiresAt <= clock.instant() ||
                            head.deliveredRevision >= lease.revision ||
                            head.revision < lease.revision
                    )
                        return@flatMap collaborationPushLeaseLost()
                    operation(observation, head).flatMap { result ->
                        if (current(budget) && clock.instant() < lease.expiresAt)
                            Result.Success(result)
                        else collaborationPushLeaseLost()
                    }
                }
            }
        }
    }

    private fun finish(lease: CollaborationPushLease) =
        queue.finish(lease).flatMap {
            if (it) Result.Success(Unit) else collaborationPushLeaseLost()
        }

    private fun defer(lease: CollaborationPushLease, at: Instant) =
        queue.defer(lease, at).flatMap {
            if (it) Result.Success(Unit) else collaborationPushLeaseLost()
        }

    /** Each key commits independently. A failed batch can retry already committed keys safely. */
    fun accept(
        hint: CollaborationPushHint,
        correlationId: UUID,
        deadline: Instant,
    ): Result<CollaborationPushReceipt> {
        if (!service.enabled || !service.pushEnabled || !provider.enabled) return unavailable()
        val started = clock.instant()
        if (deadline <= started || deadline > started.plusSeconds(30)) return unavailable()
        if (
            hint.keys.size !in 1..32 ||
                hint.keys.any { !it.value.matches(KEY) } ||
                hint.keys.map { it.value }.toSet().size != hint.keys.size ||
                (hint.event != null && !validCollaborationPushEvent(hint.event))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_push"))
        val budget = Budget(started, deadline)
        val lookupScope = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        val rejected = mutableListOf<CollaborationPushKey>()
        for (key in hint.keys) {
            val lookup =
                step(budget) {
                    transactions.run(lookupScope) { step(budget) { bindings.findKey(key) } }
                }
            if (lookup is Result.Failed) return lookup
            val initial = (lookup as Result.Success).value
            if (initial == null) {
                rejected.add(key)
                continue
            }
            val scope =
                Actor(initial.accountId, initial.companyId, emptySet(), started, correlationId)
            val admitted =
                step(budget) {
                    transactions.run(scope) {
                        observe(initial, budget)
                            .flatMap { observation ->
                                when (val target = observation.target(clock.instant())) {
                                    CollaborationPushTarget.Retired -> Result.Success(true)
                                    CollaborationPushTarget.Suppressed -> Result.Success(false)
                                    is CollaborationPushTarget.Ready ->
                                        retain(hint.event, observation, target, budget).map {
                                            false
                                        }
                                }
                            }
                            .flatMap { rejectedKey ->
                                if (current(budget)) Result.Success(rejectedKey) else unavailable()
                            }
                    }
                }
            if (admitted is Result.Failed) return admitted
            if ((admitted as Result.Success).value) rejected.add(key)
        }
        return if (current(budget)) Result.Success(CollaborationPushReceipt(rejected))
        else unavailable()
    }

    private fun retain(
        event: CollaborationPushEvent?,
        observation: Observation,
        prepared: CollaborationPushTarget.Ready,
        budget: Budget,
    ): Result<Unit> {
        val binding = observation.binding
        if (event != null) {
            val room =
                step(budget) {
                    rooms.find(
                        binding.companyId,
                        binding.accountId,
                        provider.serverName,
                        event.roomId,
                    )
                }
            if (room is Result.Failed) return room
            // An unowned event cannot retire an otherwise valid device capability.
            if (
                !collaborationPushRoomOwned(
                    binding,
                    event,
                    (room as Result.Success).value,
                    provider.serverName,
                )
            )
                return Result.Success(Unit)
        }
        val before = clock.instant()
        if (!sameCollaborationPushTarget(prepared, observation.target(before), before))
            return unavailable()
        return step(budget) { queue.append(binding, event, 86400) }
            .flatMap {
                val after = clock.instant()
                if (sameCollaborationPushTarget(prepared, observation.target(after), after))
                    Result.Success(Unit)
                else unavailable()
            }
    }

    /**
     * Lookup identifies locks; every authority input is reacquired under ordered current guards.
     */
    private fun observe(initial: CollaborationPushBinding, budget: Budget): Result<Observation> {
        val company = initial.companyId
        val policyGuard = step(budget) { policies.lock(company, shared = true) }
        if (policyGuard is Result.Failed) return policyGuard
        val companyGuard = step(budget) { companies.lock(company, shared = true) }
        if (companyGuard is Result.Failed) return companyGuard
        val memberGuard = step(budget) { members.lock(company, shared = true) }
        if (memberGuard is Result.Failed) return memberGuard
        val accountGuard = step(budget) { identities.lockAccount(initial.accountId, shared = true) }
        if (accountGuard is Result.Failed) return accountGuard
        val parent = step(budget) { sessions.lock(initial.nativeSessionId, initial.accountId) }
        if (parent is Result.Failed) return parent
        val access = step(budget) { identities.access(initial.accountId, company) }
        if (access is Result.Failed) return access
        val device =
            step(budget) { registry.findBound(company, initial.accountId, initial.nativeSessionId) }
        if (device is Result.Failed) return device
        val gatewayHead =
            step(budget) { gateway.find(company, initial.accountId, initial.deviceId, lock = true) }
        if (gatewayHead is Result.Failed) return gatewayHead
        val bindingGuard = step(budget) { bindings.lock(company, initial.deviceId) }
        if (bindingGuard is Result.Failed) return bindingGuard
        val bindingRead =
            step(budget) { bindings.find(company, initial.accountId, initial.deviceId) }
        if (bindingRead is Result.Failed) return bindingRead
        val binding = (bindingRead as Result.Success).value ?: return unavailable()
        if (
            binding.version < initial.version ||
                binding.copy(version = initial.version, publishedAt = initial.publishedAt) !=
                    initial
        )
            return unavailable()
        val registration =
            step(budget) { nativePush.find(initial.accountId, initial.nativeSessionId) }
        if (registration is Result.Failed) return registration
        val effective = step(budget) { policies.effective(company, clock.instant()) }
        if (effective is Result.Failed) return effective
        return Result.Success(
            Observation(
                binding,
                (access as Result.Success).value,
                (parent as Result.Success).value,
                (device as Result.Success).value,
                (gatewayHead as Result.Success).value,
                (registration as Result.Success).value,
                (effective as Result.Success).value,
            )
        )
    }

    private class Observation(
        val binding: CollaborationPushBinding,
        val access: AccountAccess?,
        val parent: NativeSession?,
        val device: CollaborationDevice?,
        val gateway: CollaborationGatewaySession?,
        val registration: NativePushRegistration?,
        val effective: EffectiveClientPolicy,
    ) {
        fun target(now: Instant) =
            collaborationPushTarget(
                binding,
                access,
                parent,
                device,
                gateway,
                registration,
                effective,
                now,
            )
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

    private companion object {
        val KEY = Regex("hriscp1_[a-f0-9]{64}")
    }
}
