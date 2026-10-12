package dev.fajar.hris

import dev.fajar.hris.people.domain.entities.EmployeeVisibility
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class OvertimePlanningProbe {
    data class Barrier(
        val company: UUID,
        val review: Boolean,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()

    fun afterRead(company: UUID, review: Boolean) {
        val barrier = current.get()
        if (
            barrier != null &&
                barrier.company == company &&
                barrier.review == review &&
                current.compareAndSet(barrier, null)
        ) {
            barrier.entered.countDown()
            check(barrier.release.await(5, TimeUnit.SECONDS))
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class OvertimePlanningProbeConfiguration {
    @Bean fun overtimePlanningProbe() = OvertimePlanningProbe()

    @Bean
    @Primary
    fun planningPeople(
        @Qualifier("people") delegate: PeopleRepository,
        probe: OvertimePlanningProbe,
    ): PeopleRepository =
        object : PeopleRepository by delegate {
            override fun list(
                companyId: UUID,
                accountId: UUID,
                visibility: EmployeeVisibility,
                asOf: LocalDate,
                accessAt: Instant,
                query: String,
                after: String?,
                limit: Int,
            ) =
                delegate
                    .list(companyId, accountId, visibility, asOf, accessAt, query, after, limit)
                    .also { probe.afterRead(companyId, false) }
        }

    @Bean
    @Primary
    fun planningSchedules(
        @Qualifier("schedules") delegate: ScheduleRepository,
        probe: OvertimePlanningProbe,
    ): ScheduleRepository =
        object : ScheduleRepository by delegate {
            override fun calendar(
                companyId: UUID,
                employeeId: UUID,
                from: LocalDate,
                until: LocalDate,
            ) =
                delegate.calendar(companyId, employeeId, from, until).also {
                    probe.afterRead(companyId, true)
                }
        }
}
