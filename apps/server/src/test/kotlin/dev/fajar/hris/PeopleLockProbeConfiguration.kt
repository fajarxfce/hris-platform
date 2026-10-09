package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PeopleLockProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class PeopleLockProbeConfiguration {
    @Bean fun peopleLockProbe() = PeopleLockProbe()

    @Bean
    @Primary
    fun probedPeople(
        @Qualifier("people") delegate: PeopleRepository,
        probe: PeopleLockProbe,
    ): PeopleRepository =
        object : PeopleRepository by delegate {
            override fun lockReportingLines(companyId: UUID, shared: Boolean): Result<Unit> {
                val barrier = probe.current.get()
                if (
                    barrier != null &&
                        barrier.company == companyId &&
                        probe.current.compareAndSet(barrier, null)
                ) {
                    barrier.entered.countDown()
                    check(barrier.release.await(5, TimeUnit.SECONDS))
                }
                return delegate.lockReportingLines(companyId, shared)
            }
        }
}
