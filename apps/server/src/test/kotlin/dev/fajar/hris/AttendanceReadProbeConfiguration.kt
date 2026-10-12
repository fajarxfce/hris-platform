package dev.fajar.hris

import dev.fajar.hris.workforce.domain.entities.AttendanceExceptionCursor
import dev.fajar.hris.workforce.domain.entities.AttendanceExceptionKind
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class AttendanceReadProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()

    fun afterRead(company: UUID) {
        val barrier = current.get()
        if (barrier != null && barrier.company == company && current.compareAndSet(barrier, null)) {
            barrier.entered.countDown()
            check(barrier.release.await(5, TimeUnit.SECONDS))
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class AttendanceReadProbeConfiguration {
    @Bean fun attendanceReadProbe() = AttendanceReadProbe()

    @Bean
    @Primary
    fun probedAttendance(
        @Qualifier("attendance") delegate: AttendanceRepository,
        probe: AttendanceReadProbe,
    ): AttendanceRepository =
        object : AttendanceRepository by delegate {
            override fun exceptions(
                companyId: UUID,
                from: LocalDate,
                until: LocalDate,
                kind: AttendanceExceptionKind?,
                after: AttendanceExceptionCursor?,
                limit: Int,
            ) =
                delegate.exceptions(companyId, from, until, kind, after, limit).also {
                    probe.afterRead(companyId)
                }

            override fun entries(
                companyId: UUID,
                employeeId: UUID,
                from: LocalDate,
                until: LocalDate,
            ) =
                delegate.entries(companyId, employeeId, from, until).also {
                    probe.afterRead(companyId)
                }
        }
}
