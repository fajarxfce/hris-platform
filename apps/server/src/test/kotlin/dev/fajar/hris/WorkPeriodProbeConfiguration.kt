package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class WorkPeriodProbe {
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
class WorkPeriodProbeConfiguration {
    @Bean fun workPeriodProbe() = WorkPeriodProbe()

    @Bean
    @Primary
    fun probedWorkPeriods(
        @Qualifier("workPeriods") delegate: WorkPeriodRepository,
        probe: WorkPeriodProbe,
    ): WorkPeriodRepository =
        object : WorkPeriodRepository by delegate {
            override fun state(companyId: UUID, month: YearMonth) =
                delegate.state(companyId, month).also { probe.acquired(companyId, "overview") }

            override fun publishedEmployees(
                companyId: UUID,
                jobId: UUID,
                asOf: LocalDate,
                query: String,
                after: String?,
                limit: Int,
            ) =
                delegate.publishedEmployees(companyId, jobId, asOf, query, after, limit).also {
                    probe.acquired(companyId, "employees")
                }

            override fun list(companyId: UUID, from: YearMonth, until: YearMonth) =
                delegate.list(companyId, from, until).also { probe.acquired(companyId, "list") }

            override fun snapshot(companyId: UUID, jobId: UUID, employeeId: UUID) =
                delegate.snapshot(companyId, jobId, employeeId).also {
                    probe.acquired(companyId, "snapshot")
                }
        }

    @Bean
    @Primary
    fun workPeriodJournal(
        @Qualifier("changeJournal") delegate: ChangeJournalRepository,
        probe: WorkPeriodProbe,
    ): ChangeJournalRepository =
        object : ChangeJournalRepository by delegate {
            override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                delegate.record(actor, change).also {
                    if (
                        change.action in
                            setOf("workforce.period_close_started", "workforce.period_recovered")
                    )
                        probe.acquired(requireNotNull(actor.companyId), change.action)
                }
        }
}
