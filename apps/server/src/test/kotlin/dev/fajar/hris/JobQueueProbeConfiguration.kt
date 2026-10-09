package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class JobQueueProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class JobQueueProbeConfiguration {
    @Bean fun jobQueueProbe() = JobQueueProbe()

    @Bean
    @Primary
    fun probedJobQueue(
        @Qualifier("jobs") delegate: JobRepository,
        probe: JobQueueProbe,
    ): JobRepository =
        object : JobRepository by delegate {
            override fun lockQueue(companyId: UUID): Result<Unit> {
                probe.current
                    .get()
                    ?.takeIf { it.company == companyId && probe.current.compareAndSet(it, null) }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                return delegate.lockQueue(companyId)
            }
        }
}
