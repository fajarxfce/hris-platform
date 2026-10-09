package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.leave.data.datasources.LeaveLedgerDataSource
import dev.fajar.hris.schema.tables.records.LeaveLedgerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LeaveAccountingProbe {
    @Volatile var omittedKind: String? = null
    @Volatile var beforeJournal: ((JournalRow) -> Unit)? = null

    fun clear() {
        omittedKind = null
        beforeJournal = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class LeaveAccountingProbeConfiguration {
    @Bean fun leaveAccountingProbe() = LeaveAccountingProbe()

    @Bean
    @Primary
    fun probedLeaveAccountingLedger(
        @Qualifier("leaveLedgerSource") source: LeaveLedgerDataSource,
        probe: LeaveAccountingProbe,
    ): LeaveLedgerDataSource =
        object : LeaveLedgerDataSource by source {
            override fun append(rows: List<LeaveLedgerRecord>) {
                val retained = rows.filter { it.kind != probe.omittedKind }
                if (retained.isNotEmpty()) source.append(retained)
            }
        }

    @Bean
    @Primary
    fun probedLeaveAccountingJournal(
        @Qualifier("journalSource") source: ChangeJournalDataSource,
        probe: LeaveAccountingProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                probe.beforeJournal?.invoke(row)
                source.append(row)
            }
        }
}
