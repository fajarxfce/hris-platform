package dev.fajar.hris

import dev.fajar.hris.reporting.data.datasources.HeadcountReportDataSource
import dev.fajar.hris.reporting.data.dto.HeadcountAggregateRow
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class HeadcountReportProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val afterRead = AtomicReference<Barrier?>()
    val failure = AtomicReference<Exception?>()
}

@TestConfiguration(proxyBeanMethods = false)
class HeadcountReportProbeConfiguration {
    @Bean fun headcountProbe() = HeadcountReportProbe()

    @Bean
    @Primary
    fun probedHeadcountSource(
        @Qualifier("headcountReportSource") delegate: HeadcountReportDataSource,
        probe: HeadcountReportProbe,
    ): HeadcountReportDataSource =
        object : HeadcountReportDataSource by delegate {
            override fun count(
                companies: Set<UUID>,
                asOf: LocalDate,
                statuses: Set<String>,
            ): List<HeadcountAggregateRow> {
                val result = delegate.count(companies, asOf, statuses)
                probe.afterRead
                    .get()
                    ?.takeIf { it.company in companies }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                probe.failure.getAndSet(null)?.let { throw it }
                return result
            }
        }
}
