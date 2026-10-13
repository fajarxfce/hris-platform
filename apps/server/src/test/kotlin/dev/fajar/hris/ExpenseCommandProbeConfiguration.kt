package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.ChangeJournalDataSource
import dev.fajar.hris.core.database.datasources.JournalRow
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class ExpenseCommandProbe {
    @Volatile var afterClaim: ((UUID) -> Unit)? = null
    @Volatile var afterJournal: ((JournalRow) -> Unit)? = null

    fun clear() {
        afterClaim = null
        afterJournal = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class ExpenseCommandProbeConfiguration {
    @Bean fun expenseCommandProbe() = ExpenseCommandProbe()

    @Bean
    @Primary
    fun expenseCommandClaims(
        @Qualifier("expenseClaims") delegate: ExpenseClaimRepository,
        probe: ExpenseCommandProbe,
    ): ExpenseClaimRepository =
        object : ExpenseClaimRepository by delegate {
            override fun find(companyId: UUID, id: UUID) =
                delegate.find(companyId, id).also { probe.afterClaim?.invoke(companyId) }
        }

    @Bean
    @Primary
    fun expenseCommandJournal(
        @Qualifier("journalSource") delegate: ChangeJournalDataSource,
        probe: ExpenseCommandProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                delegate.append(row)
                probe.afterJournal?.invoke(row)
            }
        }
}
