package dev.fajar.hris

import dev.fajar.hris.reporting.data.datasources.LeaveReportDataSource
import dev.fajar.hris.reporting.data.dto.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LeaveReportProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val afterRead = AtomicReference<Barrier?>()
    val failure = AtomicReference<Exception?>()

    fun acquired(company: UUID) {
        afterRead
            .get()
            ?.takeIf { it.company == company }
            ?.let {
                it.entered.countDown()
                check(it.release.await(5, TimeUnit.SECONDS))
            }
        failure.getAndSet(null)?.let { throw it }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class LeaveReportProbeConfiguration {
    @Bean fun leaveReportProbe() = LeaveReportProbe()

    @Bean
    @Primary
    fun probedLeaveReportSource(
        @Qualifier("leaveReportSource") delegate: LeaveReportDataSource,
        probe: LeaveReportProbe,
    ): LeaveReportDataSource =
        object : LeaveReportDataSource {
            override fun aggregate(
                company: UUID,
                from: LocalDate,
                until: LocalDate,
            ): List<LeaveReportAggregateRow> =
                delegate.aggregate(company, from, until).also { probe.acquired(company) }

            override fun requests(
                company: UUID,
                from: LocalDate,
                until: LocalDate,
                status: String?,
                afterAt: OffsetDateTime?,
                afterId: UUID?,
                limit: Int,
            ): List<LeaveReportRequestRow> =
                delegate.requests(company, from, until, status, afterAt, afterId, limit).also {
                    probe.acquired(company)
                }
        }
}
