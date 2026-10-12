package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class JobAssuranceProbe {
    data class Barrier(
        val company: UUID,
        val point: String,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()

    fun acquired(company: UUID, point: String) {
        val barrier = current.get()
        if (
            barrier != null &&
                barrier.company == company &&
                barrier.point == point &&
                current.compareAndSet(barrier, null)
        ) {
            barrier.entered.countDown()
            check(barrier.release.await(5, TimeUnit.SECONDS))
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class JobAssuranceProbeConfiguration {
    @Bean fun jobAssuranceProbe() = JobAssuranceProbe()

    @Bean
    @Primary
    fun assuredJobs(
        @Qualifier("jobs") delegate: JobRepository,
        probe: JobAssuranceProbe,
    ): JobRepository =
        object : JobRepository by delegate {
            override fun find(companyId: UUID, id: UUID, lock: Boolean) =
                delegate.find(companyId, id, lock).also {
                    if (!lock) probe.acquired(companyId, "get")
                }

            override fun list(
                companyId: UUID,
                actorId: UUID?,
                size: Int,
                beforeAt: Instant?,
                beforeId: UUID?,
            ) =
                delegate.list(companyId, actorId, size, beforeAt, beforeId).also {
                    probe.acquired(companyId, "list")
                }
        }

    @Bean
    @Primary
    fun assuredJobJournal(
        @Qualifier("changeJournal") delegate: ChangeJournalRepository,
        probe: JobAssuranceProbe,
    ): ChangeJournalRepository =
        object : ChangeJournalRepository by delegate {
            override fun record(actor: Actor, change: ChangeRecord) =
                delegate.record(actor, change).also {
                    if (change.action == "jobs.cancellation_requested")
                        probe.acquired(requireNotNull(actor.companyId), "cancel")
                }
        }
}
