package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class TransferAccessProbe {
    data class Barrier(
        val account: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class TransferAccessProbeConfiguration {
    @Bean fun transferAccessProbe() = TransferAccessProbe()

    @Bean
    @Primary
    fun probedIdentities(
        @Qualifier("identities") delegate: IdentityRepository,
        probe: TransferAccessProbe,
    ): IdentityRepository =
        object : IdentityRepository by delegate {
            override fun lockAccount(accountId: UUID): Result<Unit> {
                probe.current
                    .get()
                    ?.takeIf { it.account == accountId }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                return delegate.lockAccount(accountId)
            }
        }
}
