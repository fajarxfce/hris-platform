package dev.fajar.hris

import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import dev.fajar.hris.workforce.domain.repositories.OvertimeRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class OvertimeReadProbe {
    data class Barrier(
        val company: UUID,
        val kind: String,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()

    fun afterRead(company: UUID, kind: String) {
        val barrier = current.get()
        if (
            barrier != null &&
                barrier.company == company &&
                barrier.kind == kind &&
                current.compareAndSet(barrier, null)
        ) {
            barrier.entered.countDown()
            check(barrier.release.await(5, TimeUnit.SECONDS))
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class OvertimeReadProbeConfiguration {
    @Bean fun overtimeReadProbe() = OvertimeReadProbe()

    @Bean
    @Primary
    fun probedOvertimeReads(
        @Qualifier("overtime") delegate: OvertimeRepository,
        probe: OvertimeReadProbe,
    ): OvertimeRepository =
        object : OvertimeRepository by delegate {
            override fun list(
                companyId: UUID,
                employeeId: UUID?,
                from: LocalDate,
                until: LocalDate,
                status: OvertimeStatus?,
                after: UUID?,
                limit: Int,
            ) =
                delegate.list(companyId, employeeId, from, until, status, after, limit).also {
                    probe.afterRead(companyId, "list")
                }

            override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                delegate.history(companyId, id, after, limit).also {
                    probe.afterRead(companyId, "details")
                }
        }
}
