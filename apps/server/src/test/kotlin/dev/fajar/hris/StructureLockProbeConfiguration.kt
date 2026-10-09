package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class StructureLockProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class StructureLockProbeConfiguration {
    @Bean fun structureLockProbe() = StructureLockProbe()

    @Bean
    @Primary
    fun probedStructure(
        @Qualifier("organization") delegate: OrganizationRepository,
        probe: StructureLockProbe,
    ): OrganizationRepository =
        object : OrganizationRepository by delegate {
            override fun lockStructure(companyId: UUID, shared: Boolean): Result<Unit> {
                val barrier = probe.current.get()
                if (
                    barrier != null &&
                        barrier.company == companyId &&
                        probe.current.compareAndSet(barrier, null)
                ) {
                    barrier.entered.countDown()
                    check(barrier.release.await(5, TimeUnit.SECONDS))
                }
                return delegate.lockStructure(companyId, shared)
            }
        }
}
