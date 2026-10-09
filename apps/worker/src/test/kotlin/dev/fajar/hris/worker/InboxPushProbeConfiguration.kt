package dev.fajar.hris.worker

import dev.fajar.hris.communications.data.datasources.InboxPushDataSource
import dev.fajar.hris.communications.domain.entities.InboxPushPolicy
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager

class InboxPushProbe {
    data class Barrier(
        val account: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val messages = ConcurrentLinkedQueue<PushMessage>()
    val send =
        AtomicReference<(PushMessage) -> Result<PushOutcome>>({
            Result.Success(PushOutcome.ACCEPTED)
        })
    val failAdvance = AtomicBoolean()
    val barrier = AtomicReference<Barrier?>()

    fun clear() {
        messages.clear()
        send.set { Result.Success(PushOutcome.ACCEPTED) }
        failAdvance.set(false)
        barrier.getAndSet(null)?.release?.countDown()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class InboxPushProbeConfiguration {
    @Bean fun inboxPushProbe() = InboxPushProbe()

    @Bean @Primary fun enabledInboxPushPolicy() = InboxPushPolicy(true)

    @Bean
    @Primary
    fun fixturePushRepository(probe: InboxPushProbe): PushRepository = PushRepository { message ->
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "External delivery must not retain a transaction"
        }
        check(probe.messages.size < 100)
        probe.messages.add(message)
        probe.send.get().invoke(message)
    }

    @Bean
    @Primary
    fun inboxPushSourceProbe(
        @Qualifier("inboxPushSource") delegate: InboxPushDataSource,
        probe: InboxPushProbe,
    ): InboxPushDataSource =
        object : InboxPushDataSource by delegate {
            override fun advance(
                companyId: UUID,
                inboxId: UUID,
                owner: UUID,
                token: UUID,
                sessionId: UUID,
                accepted: Boolean,
                rejected: Boolean,
                code: String?,
                now: Instant,
            ): Boolean =
                if (probe.failAdvance.get()) false
                else
                    delegate.advance(
                        companyId,
                        inboxId,
                        owner,
                        token,
                        sessionId,
                        accepted,
                        rejected,
                        code,
                        now,
                    )
        }

    @Bean
    @Primary
    fun inboxPushIdentityProbe(
        @Qualifier("identities") delegate: IdentityRepository,
        probe: InboxPushProbe,
    ): IdentityRepository =
        object : IdentityRepository by delegate {
            override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                probe.barrier
                    .get()
                    ?.takeIf { it.account == accountId }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                return delegate.lockAccount(accountId, shared)
            }
        }
}
