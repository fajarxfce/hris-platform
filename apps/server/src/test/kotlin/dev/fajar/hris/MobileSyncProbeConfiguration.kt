package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.ChangeJournalDataSource
import dev.fajar.hris.core.database.datasources.JournalRow
import dev.fajar.hris.schema.tables.records.MobileSyncHeadsRecord
import dev.fajar.hris.sync.data.datasources.SyncDataSource
import dev.fajar.hris.sync.data.models.*
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class MobileSyncProbe {
    @Volatile var afterHead: ((UUID) -> Unit)? = null
    @Volatile var afterSnapshot: ((SyncSelection) -> Unit)? = null
    @Volatile var beforeJournal: ((JournalRow) -> Unit)? = null

    fun clear() {
        afterHead = null
        afterSnapshot = null
        beforeJournal = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class MobileSyncProbeConfiguration {
    @Bean fun mobileSyncProbe() = MobileSyncProbe()

    @Bean
    @Primary
    fun probedSyncSource(
        @Qualifier("syncSource") delegate: SyncDataSource,
        probe: MobileSyncProbe,
    ): SyncDataSource =
        object : SyncDataSource by delegate {
            override fun head(companyId: UUID): MobileSyncHeadsRecord =
                delegate.head(companyId).also { probe.afterHead?.invoke(companyId) }

            override fun snapshot(
                selection: SyncSelection,
                afterCollection: String?,
                afterId: UUID?,
                limit: Int,
            ): List<SyncResourceRow> =
                delegate.snapshot(selection, afterCollection, afterId, limit).also {
                    probe.afterSnapshot?.invoke(selection)
                }
        }

    @Bean
    @Primary
    fun syncProbedJournal(
        @Qualifier("journalSource") delegate: ChangeJournalDataSource,
        probe: MobileSyncProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                probe.beforeJournal?.invoke(row)
                delegate.append(row)
            }
        }
}
