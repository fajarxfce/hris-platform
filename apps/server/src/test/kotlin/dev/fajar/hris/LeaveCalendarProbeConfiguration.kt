package dev.fajar.hris

import dev.fajar.hris.leave.domain.repositories.LeaveCalendarRepository
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

class LeaveCalendarProbe {
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
class LeaveCalendarProbeConfiguration {
    @Bean fun leaveCalendarProbe() = LeaveCalendarProbe()

    @Bean
    @Primary
    fun probedLeaveCalendar(
        @Qualifier("leaveCalendar") source: LeaveCalendarRepository,
        probe: LeaveCalendarProbe,
    ): LeaveCalendarRepository =
        object : LeaveCalendarRepository by source {
            override fun month(
                companyId: UUID,
                managerAccountId: UUID?,
                accessDate: LocalDate,
                month: YearMonth,
            ) =
                source.month(companyId, managerAccountId, accessDate, month).also {
                    probe.afterRead(companyId)
                }

            override fun entries(
                companyId: UUID,
                managerAccountId: UUID?,
                accessDate: LocalDate,
                date: LocalDate,
                after: UUID?,
                limit: Int,
            ) =
                source.entries(companyId, managerAccountId, accessDate, date, after, limit).also {
                    probe.afterRead(companyId)
                }
        }
}
