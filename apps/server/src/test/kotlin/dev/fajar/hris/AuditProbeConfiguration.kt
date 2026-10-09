package dev.fajar.hris

import dev.fajar.hris.administration.data.datasources.AuditDataSource
import dev.fajar.hris.administration.data.dto.AuditEventRow
import dev.fajar.hris.administration.data.dto.AuditQueryRow
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class AuditProbe {
    data class Barrier(
        val company: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val afterRead = AtomicReference<Barrier?>()
    val failure = AtomicReference<Exception?>()
    val reads = AtomicInteger()
}

@TestConfiguration(proxyBeanMethods = false)
class AuditProbeConfiguration {
    @Bean fun auditProbe() = AuditProbe()

    @Bean
    @Primary
    fun probedAuditSource(
        @Qualifier("auditSource") delegate: AuditDataSource,
        probe: AuditProbe,
    ): AuditDataSource =
        object : AuditDataSource by delegate {
            override fun search(companyId: UUID, query: AuditQueryRow): List<AuditEventRow> {
                probe.reads.incrementAndGet()
                val values = delegate.search(companyId, query)
                probe.afterRead
                    .get()
                    ?.takeIf { it.company == companyId }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                probe.failure.getAndSet(null)?.let { throw it }
                return values
            }
        }
}
