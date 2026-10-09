package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.data.datasources.LifecycleDataSource
import dev.fajar.hris.people.domain.repositories.LifecycleRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LifecycleReadProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val caseWait = AtomicReference<Barrier?>()
    val listRead = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class LifecycleReadProbeConfiguration {
    @Bean fun lifecycleReadProbe() = LifecycleReadProbe()

    @Bean
    @Primary
    fun probedLifecycleSource(
        @Qualifier("lifecycleSource") delegate: LifecycleDataSource,
        probe: LifecycleReadProbe,
    ): LifecycleDataSource =
        object : LifecycleDataSource by delegate {
            override fun cases(
                companyId: UUID,
                employeeId: UUID?,
                status: String?,
                after: UUID?,
                limit: Int,
            ) =
                delegate.cases(companyId, employeeId, status, after, limit).also {
                    probe.listRead
                        .get()
                        ?.takeIf {
                            it.company == companyId && probe.listRead.compareAndSet(it, null)
                        }
                        ?.let {
                            it.entered.countDown()
                            check(it.release.await(5, TimeUnit.SECONDS))
                        }
                }
        }

    @Bean
    @Primary
    fun probedLifecycle(
        @Qualifier("lifecycle") delegate: LifecycleRepository,
        probe: LifecycleReadProbe,
    ): LifecycleRepository =
        object : LifecycleRepository by delegate {
            override fun lockCase(companyId: UUID, id: UUID, shared: Boolean): Result<Unit> {
                probe.caseWait
                    .get()
                    ?.takeIf { it.company == companyId && probe.caseWait.compareAndSet(it, null) }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                return delegate.lockCase(companyId, id, shared)
            }
        }
}
