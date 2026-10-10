package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalTransition
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class ApprovalFenceProbe {
    class Barrier(val company: UUID) {
        val claimed = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun awaitOnce(id: UUID) {
            if (id != company || !claimed.compareAndSet(false, true)) return
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
    }

    class Observation(val company: UUID, val entered: CountDownLatch = CountDownLatch(1))

    val beforeLock = AtomicReference<Barrier?>()
    val beforeDecision = AtomicReference<Barrier?>()
    val observedLock = AtomicReference<Observation?>()
}

@TestConfiguration(proxyBeanMethods = false)
class ApprovalFenceProbeConfiguration {
    @Bean fun approvalFenceProbe() = ApprovalFenceProbe()

    @Bean
    @Primary
    fun probedApprovals(
        @Qualifier("approvals") delegate: ApprovalRepository,
        probe: ApprovalFenceProbe,
    ): ApprovalRepository =
        object : ApprovalRepository by delegate {
            override fun lock(companyId: UUID, shared: Boolean): Result<Unit> {
                probe.observedLock.get()?.takeIf { it.company == companyId }?.entered?.countDown()
                probe.beforeLock.get()?.awaitOnce(companyId)
                return delegate.lock(companyId, shared)
            }

            override fun decide(
                actor: Actor,
                id: UUID,
                version: Long,
                step: Int,
                transition: ApprovalTransition,
                reason: String,
                at: Instant,
            ): Result<MutationReceipt> {
                probe.beforeDecision.get()?.awaitOnce(requireNotNull(actor.companyId))
                return delegate.decide(actor, id, version, step, transition, reason, at)
            }
        }
}
