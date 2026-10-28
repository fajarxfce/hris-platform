package dev.fajar.hris

import dev.fajar.hris.reporting.data.datasources.AttendanceExceptionReportDataSource
import dev.fajar.hris.reporting.data.dto.AttendanceExceptionAggregateRow
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class AttendanceReportProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val afterRead = AtomicReference<Barrier?>()
    val failure = AtomicReference<Exception?>()
}

@TestConfiguration(proxyBeanMethods = false)
class AttendanceReportProbeConfiguration {
    @Bean fun attendanceReportProbe() = AttendanceReportProbe()

    @Bean
    @Primary
    fun probedAttendanceReportSource(
        @Qualifier("attendanceExceptionReportSource") delegate: AttendanceExceptionReportDataSource,
        probe: AttendanceReportProbe,
    ): AttendanceExceptionReportDataSource =
        object : AttendanceExceptionReportDataSource {
            override fun aggregate(
                company: UUID,
                from: LocalDate,
                until: LocalDate,
            ): List<AttendanceExceptionAggregateRow> {
                val result = delegate.aggregate(company, from, until)
                probe.afterRead
                    .get()
                    ?.takeIf { it.company == company }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                probe.failure.getAndSet(null)?.let { throw it }
                return result
            }
        }
}
