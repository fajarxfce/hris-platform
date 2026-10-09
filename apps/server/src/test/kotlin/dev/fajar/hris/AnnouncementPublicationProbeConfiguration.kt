package dev.fajar.hris

import dev.fajar.hris.communications.data.datasources.*
import dev.fajar.hris.communications.data.models.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.schema.tables.records.AnnouncementPublicationsRecord
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

class AnnouncementPublicationProbe {
    @Volatile var afterInbox: (() -> Unit)? = null
    @Volatile var omitInbox = false
    val timings = ConcurrentHashMap<String, Long>()

    fun <T> measure(stage: String, work: () -> T): T {
        val start = System.nanoTime()
        return try {
            work()
        } finally {
            timings[stage] = Duration.ofNanos(System.nanoTime() - start).toMillis()
        }
    }

    fun clear() {
        afterInbox = null
        omitInbox = false
        timings.clear()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class AnnouncementPublicationProbeConfiguration {
    @Bean fun announcementPublicationProbe() = AnnouncementPublicationProbe()

    @Bean
    @Primary
    fun announcementTestTransactions(manager: PlatformTransactionManager, jdbc: JdbcTemplate) =
        PostgresTransactionRunner(manager, jdbc, Duration.ofSeconds(10), Duration.ofSeconds(2))

    @Bean
    @Primary
    fun probedAnnouncementPublicationSource(
        @Qualifier("announcementPublicationSource") delegate: AnnouncementPublicationDataSource,
        probe: AnnouncementPublicationProbe,
    ): AnnouncementPublicationDataSource =
        object : AnnouncementPublicationDataSource by delegate {
            override fun insertPublication(row: AnnouncementPublicationsRecord) =
                probe.measure("publication") { delegate.insertPublication(row) }

            override fun insertInbox(companyId: UUID, publicationId: UUID): Int {
                val result =
                    if (probe.omitInbox) 0
                    else probe.measure("inbox") { delegate.insertInbox(companyId, publicationId) }
                probe.afterInbox?.invoke()
                return result
            }
        }

    @Bean
    @Primary
    fun probedAnnouncementAudienceSource(
        @Qualifier("announcementAudienceSource") delegate: AnnouncementAudienceDataSource,
        probe: AnnouncementPublicationProbe,
    ): AnnouncementAudienceDataSource =
        object : AnnouncementAudienceDataSource by delegate {
            override fun recipients(
                query: AnnouncementRecipientQuery,
                limit: Int,
            ): List<AnnouncementRecipientRow> =
                probe.measure("audience") { delegate.recipients(query, limit) }
        }
}
