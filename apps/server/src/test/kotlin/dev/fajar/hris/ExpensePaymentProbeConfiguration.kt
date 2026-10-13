package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.ChangeJournalDataSource
import dev.fajar.hris.core.database.datasources.JournalRow
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.expenses.domain.entities.ExpensePaymentBatchStatus
import dev.fajar.hris.expenses.domain.repositories.ExpensePaymentRepository
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class ExpensePaymentProbe {
    @Volatile var afterRead: ((UUID, String) -> Unit)? = null
    @Volatile var afterJournal: ((JournalRow) -> Unit)? = null
    @Volatile var resultsFailure: Failure? = null

    fun clear() {
        afterRead = null
        afterJournal = null
        resultsFailure = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class ExpensePaymentProbeConfiguration {
    @Bean fun expensePaymentProbe() = ExpensePaymentProbe()

    @Bean
    @Primary
    fun probedPaymentRepository(
        @Qualifier("expensePaymentRepository") delegate: ExpensePaymentRepository,
        probe: ExpensePaymentProbe,
    ): ExpensePaymentRepository =
        object : ExpensePaymentRepository by delegate {
            override fun find(companyId: UUID, id: UUID) =
                delegate.find(companyId, id).also { probe.afterRead?.invoke(companyId, "batch") }

            override fun candidates(companyId: UUID, submissionIds: Set<UUID>) =
                delegate.candidates(companyId, submissionIds).also {
                    probe.afterRead?.invoke(companyId, "candidates")
                }

            override fun payables(
                companyId: UUID,
                from: Instant,
                until: Instant,
                after: UUID?,
                limit: Int,
            ) =
                delegate.payables(companyId, from, until, after, limit).also {
                    probe.afterRead?.invoke(companyId, "payables")
                }

            override fun list(
                companyId: UUID,
                from: Instant,
                until: Instant,
                status: ExpensePaymentBatchStatus?,
                after: UUID?,
                limit: Int,
            ) =
                delegate.list(companyId, from, until, status, after, limit).also {
                    probe.afterRead?.invoke(companyId, "batches")
                }

            override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                delegate.history(companyId, id, after, limit).also {
                    probe.afterRead?.invoke(companyId, "history")
                }

            override fun results(companyId: UUID, id: UUID) =
                delegate.results(companyId, id).let { result ->
                    probe.afterRead?.invoke(companyId, "results")
                    probe.resultsFailure?.let { Result.Failed(it) } ?: result
                }
        }

    @Bean
    @Primary
    fun paymentAssuranceJournal(
        @Qualifier("journalSource") delegate: ChangeJournalDataSource,
        probe: ExpensePaymentProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                delegate.append(row)
                probe.afterJournal?.invoke(row)
            }
        }
}
