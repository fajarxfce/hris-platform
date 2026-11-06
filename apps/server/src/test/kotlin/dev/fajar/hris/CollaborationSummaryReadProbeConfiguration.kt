package dev.fajar.hris

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.schema.tables.records.MobileSyncHeadsRecord
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class CollaborationSummaryReadProbe {
    val afterRead = AtomicReference<((String) -> Unit)?>()
    val hydrationCalls = AtomicInteger()

    fun clear() {
        afterRead.set(null)
        hydrationCalls.set(0)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class CollaborationSummaryReadProbeConfiguration {
    @Bean fun summaryReadProbe() = CollaborationSummaryReadProbe()

    @Bean
    @Primary
    fun probedSummaryReadSource(
        @Qualifier("collaborationSummaryReadSource") delegate: CollaborationSummaryReadDataSource,
        probe: CollaborationSummaryReadProbe,
    ): CollaborationSummaryReadDataSource =
        object : CollaborationSummaryReadDataSource by delegate {
            override fun head(company: UUID): MobileSyncHeadsRecord =
                delegate.head(company).also { probe.afterRead.get()?.invoke("head") }

            override fun snapshot(
                company: UUID,
                account: UUID,
                after: UUID?,
                limit: Int,
            ): List<CollaborationSummaryFactRow> =
                delegate.snapshot(company, account, after, limit).also {
                    probe.afterRead.get()?.invoke("snapshot")
                }

            override fun facts(
                company: UUID,
                account: UUID,
                ids: List<UUID>,
            ): List<CollaborationSummaryFactRow> {
                probe.hydrationCalls.incrementAndGet()
                return delegate.facts(company, account, ids).also {
                    probe.afterRead.get()?.invoke("facts")
                }
            }

            override fun pending(company: UUID, account: UUID): Boolean =
                delegate.pending(company, account).also { probe.afterRead.get()?.invoke("pending") }
        }
}
