package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Independent finite Matrix fanout; the original publication and target transaction never change.
 */
class FanOutCollaborationReadinessControl(
    private val queue: CollaborationReadinessControlQueueRepository,
    private val providerSessions: CollaborationProviderSessionRepository,
    private val transport: CollaborationMatrixReadinessRepository,
    private val registry: CollaborationRegistryRepository,
    private val gateway: CollaborationGatewaySessionRepository,
    private val keys: CollaborationDeviceKeyRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun claim(ownerId: UUID, limit: Int): Result<List<CollaborationReadinessLease>> {
        if (!enabled()) return Result.Success(emptyList())
        if (limit !in 1..4) return lost()
        return transactions.run(actor(UUID(0, 0), null)) {
            queue.claim(ownerId, limit).flatMap { leases ->
                if (
                    leases.size > limit ||
                        leases.map { it.eventId }.distinct().size != leases.size ||
                        leases.any { it.ownerId != ownerId || it.expiresAt <= clock.instant() }
                )
                    lost()
                else Result.Success(leases)
            }
        }
    }

    fun fanOut(lease: CollaborationReadinessLease): Result<Unit> {
        if (!enabled()) return unavailable()
        val deadline = minOf(clock.instant().plusSeconds(20), lease.expiresAt)
        val publisher = actor(lease.publisherAccountId, lease.companyId)
        val acquired =
            step(deadline) {
                transactions.run(publisher) {
                    queue.find(lease, false).flatMap { checkpoint ->
                        if (checkpoint == null || checkpoint.lease != lease) return@flatMap lost()
                        queue.targets(checkpoint, 8).flatMap { targets ->
                            val positions = targets.map { it.position }
                            if (
                                targets.size > 8 ||
                                    positions != positions.distinct().sorted() ||
                                    positions.any {
                                        it.accountId == lease.publisherAccountId ||
                                            (checkpoint.after != null && it <= checkpoint.after)
                                    }
                            )
                                lost()
                            else Result.Success(checkpoint to targets)
                        }
                    }
                }
            }
        if (acquired is Result.Failed) return acquired
        val (initial, targets) = (acquired as Result.Success).value
        var checkpoint = initial
        for (target in targets) {
            val expected = checkpoint
            val control =
                CollaborationReadinessControl(
                    lease.eventId,
                    lease.companyId,
                    target.position.accountId,
                    target.nativeSessionId,
                    target.position.deviceId,
                    expected.publicationCutoff.toEpochMilli(),
                )
            val prepared =
                withTarget(expected, control, deadline) { admission, credential ->
                    when {
                        admission == null ->
                            advance(expected, target.position).map { Preparation.Skipped }
                        !collaborationReadinessCredential(
                            control,
                            credential,
                            provider.serverName,
                            clock.instant(),
                        ) -> defer(expected).map { Preparation.Deferred }
                        else ->
                            Result.Success(Preparation.Ready(admission, checkNotNull(credential)))
                    }
                }
            if (prepared is Result.Failed) return prepared
            when (val ready = (prepared as Result.Success).value) {
                Preparation.Deferred -> return Result.Success(Unit)
                Preparation.Skipped -> Unit
                is Preparation.Ready -> {
                    val sendDeadline =
                        minOf(
                            deadline,
                            ready.admission.expiresAt,
                            checkNotNull(ready.credential.expiresAt),
                        )
                    val sent =
                        step(sendDeadline) {
                            transport.send(
                                control,
                                provider.serverName,
                                ready.credential.accessToken,
                                sendDeadline,
                            )
                        }
                    val acknowledged =
                        withTarget(expected, control, deadline) { admission, credential ->
                            when {
                                admission == null -> advance(expected, target.position).map { true }
                                sent is Result.Success &&
                                    sameCollaborationReadinessAdmission(
                                        ready.admission,
                                        admission,
                                        clock.instant(),
                                    ) &&
                                    collaborationReadinessCredential(
                                        control,
                                        credential,
                                        provider.serverName,
                                        clock.instant(),
                                    ) &&
                                    credential?.generation == ready.credential.generation ->
                                    advance(expected, target.position).map { true }
                                else -> defer(expected).map { false }
                            }
                        }
                    if (acknowledged is Result.Failed) return acknowledged
                    if (!(acknowledged as Result.Success).value) return Result.Success(Unit)
                }
            }
            checkpoint = expected.copy(after = target.position)
        }
        val expected = checkpoint
        return step(deadline) {
            transactions.run(publisher) {
                queue.find(lease, true).flatMap { current ->
                    if (current != expected) lost()
                    else
                        queue.release(expected, targets.size < 8, clock.instant()).flatMap {
                            if (it) step(deadline) { Result.Success(Unit) } else lost()
                        }
                }
            }
        }
    }

    /** Ordered current guards surround capture/acknowledgement, never external provider I/O. */
    private fun <T> withTarget(
        checkpoint: CollaborationReadinessCheckpoint,
        control: CollaborationReadinessControl,
        deadline: Instant,
        operation: (CollaborationReadinessAdmission?, CollaborationProviderSession?) -> Result<T>,
    ): Result<T> =
        step(deadline) {
            transactions.run(actor(control.accountId, control.companyId)) {
                val policyGuard = step(deadline) { policies.lock(control.companyId, shared = true) }
                if (policyGuard is Result.Failed) return@run policyGuard
                val companyGuard =
                    step(deadline) { companies.lock(control.companyId, shared = true) }
                if (companyGuard is Result.Failed) return@run companyGuard
                val memberGuard = step(deadline) { members.lock(control.companyId, shared = true) }
                if (memberGuard is Result.Failed) return@run memberGuard
                val accountGuard =
                    step(deadline) { identities.lockAccount(control.accountId, shared = true) }
                if (accountGuard is Result.Failed) return@run accountGuard
                val parent =
                    step(deadline) { sessions.lock(control.nativeSessionId, control.accountId) }
                if (parent is Result.Failed) return@run parent
                val access =
                    step(deadline) { identities.access(control.accountId, control.companyId) }
                if (access is Result.Failed) return@run access
                val device =
                    step(deadline) {
                        registry.findBound(
                            control.companyId,
                            control.accountId,
                            control.nativeSessionId,
                        )
                    }
                if (device is Result.Failed) return@run device
                val gatewayHead =
                    step(deadline) {
                        gateway.find(control.companyId, control.accountId, control.deviceId)
                    }
                if (gatewayHead is Result.Failed) return@run gatewayHead
                val publication =
                    step(deadline) {
                        keys.find(control.companyId, control.accountId, control.deviceId)
                    }
                if (publication is Result.Failed) return@run publication
                val effective =
                    step(deadline) { policies.effective(control.companyId, clock.instant()) }
                if (effective is Result.Failed) return@run effective
                fun admitted() =
                    collaborationReadinessAdmission(
                        control,
                        (access as Result.Success).value,
                        (parent as Result.Success).value,
                        (device as Result.Success).value,
                        (gatewayHead as Result.Success).value,
                        (publication as Result.Success).value,
                        (effective as Result.Success).value,
                        clock.instant(),
                    )
                val credentials =
                    if (admitted() != null)
                        step(deadline) {
                            providerSessions.find(
                                control.companyId,
                                control.accountId,
                                control.deviceId,
                            )
                        }
                    else Result.Success(null)
                if (credentials is Result.Failed) return@run credentials
                val found = step(deadline) { queue.find(checkpoint.lease, true) }
                if (found is Result.Failed) return@run found
                if ((found as Result.Success).value != checkpoint) return@run lost()
                operation(admitted(), (credentials as Result.Success).value).flatMap { result ->
                    step(deadline) { Result.Success(result) }
                }
            }
        }

    private fun advance(
        checkpoint: CollaborationReadinessCheckpoint,
        position: CollaborationReadinessPosition,
    ) = queue.advance(checkpoint, position).flatMap { if (it) Result.Success(Unit) else lost() }

    private fun defer(checkpoint: CollaborationReadinessCheckpoint) =
        queue.release(checkpoint, false, clock.instant().plusSeconds(2)).flatMap {
            if (it) Result.Success(Unit) else lost()
        }

    private fun actor(account: UUID, company: UUID?) =
        Actor(account, company, emptySet(), clock.instant(), UUID.randomUUID())

    private fun enabled() = service.enabled && provider.enabled

    private fun <T> step(deadline: Instant, work: () -> Result<T>): Result<T> {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (clock.instant() >= deadline) return lost()
        val result = work()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        return if (clock.instant() >= deadline) lost() else result
    }

    private fun lost() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_readiness_control_lease_lost"))

    private fun unavailable() =
        Result.Failed(
            Failure(FailureKind.UNAVAILABLE, "collaboration_readiness_control_unavailable")
        )

    private sealed interface Preparation {
        data object Skipped : Preparation

        data object Deferred : Preparation

        class Ready(
            val admission: CollaborationReadinessAdmission,
            val credential: CollaborationProviderSession,
        ) : Preparation
    }
}
