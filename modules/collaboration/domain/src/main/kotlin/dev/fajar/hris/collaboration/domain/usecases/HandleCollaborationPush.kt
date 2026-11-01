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
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Opaque wake admission only. Interactive content and reply keep their separate assurance gates.
 */
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
) {
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
