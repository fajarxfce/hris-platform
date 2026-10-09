package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.schema.tables.records.OvertimeChangesRecord
import dev.fajar.hris.workforce.data.datasources.OvertimeDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class OvertimeProbe {
    @Volatile var omitHistory = false
    @Volatile var beforeJournal: ((JournalRow) -> Unit)? = null

    fun clear() {
        omitHistory = false
        beforeJournal = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class OvertimeProbeConfiguration {
    @Bean fun overtimeProbe() = OvertimeProbe()

    @Bean
    @Primary
    fun probedOvertimeSource(
        @Qualifier("overtimeSource") source: OvertimeDataSource,
        probe: OvertimeProbe,
    ): OvertimeDataSource =
        object : OvertimeDataSource by source {
            override fun append(row: OvertimeChangesRecord) {
                if (!probe.omitHistory) source.append(row)
            }
        }

    @Bean
    @Primary
    fun probedOvertimeJournal(
        @Qualifier("journalSource") source: ChangeJournalDataSource,
        probe: OvertimeProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                probe.beforeJournal?.invoke(row)
                source.append(row)
            }
        }
}
