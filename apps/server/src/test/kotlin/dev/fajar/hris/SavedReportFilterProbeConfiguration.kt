package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.SavedReportFilterDataSource
import dev.fajar.hris.reporting.data.dto.SavedReportFilterRow
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class SavedReportFilterProbe {
    data class Gate(
        val account: UUID,
        val stage: String,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val gate = AtomicReference<Gate?>()
    val failAfterSave = AtomicReference<Exception?>()
    val rejectJournal = AtomicBoolean(false)

    fun reached(account: UUID, stage: String) {
        gate
            .get()
            ?.takeIf { it.account == account && it.stage == stage }
            ?.let {
                it.entered.countDown()
                check(it.release.await(5, TimeUnit.SECONDS))
            }
    }

    fun clear() {
        gate.getAndSet(null)?.release?.countDown()
        failAfterSave.set(null)
        rejectJournal.set(false)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class SavedReportFilterProbeConfiguration {
    @Bean fun savedFilterProbe() = SavedReportFilterProbe()

    @Bean
    @Primary
    fun probedSavedFilterSource(
        @Qualifier("savedReportFilterSource") delegate: SavedReportFilterDataSource,
        probe: SavedReportFilterProbe,
    ): SavedReportFilterDataSource =
        object : SavedReportFilterDataSource by delegate {
            override fun lock(company: UUID, account: UUID) {
                probe.reached(account, "lock")
                delegate.lock(company, account)
            }

            override fun list(company: UUID, account: UUID, limit: Int) =
                delegate.list(company, account, limit).also { probe.reached(account, "list") }

            override fun find(company: UUID, account: UUID, id: UUID) =
                delegate.find(company, account, id).also { probe.reached(account, "find") }

            override fun countActive(company: UUID, account: UUID, limit: Int) =
                delegate.countActive(company, account, limit).also {
                    probe.reached(account, "count")
                }

            override fun save(row: SavedReportFilterRow, expectedVersion: Long?): Int =
                delegate.save(row, expectedVersion).also {
                    probe.reached(row.accountId, "save")
                    probe.failAfterSave.getAndSet(null)?.let { throw it }
                }
        }

    @Bean
    @Primary
    fun savedFilterJournal(
        @Qualifier("changeJournal") delegate: ChangeJournalRepository,
        probe: SavedReportFilterProbe,
    ): ChangeJournalRepository =
        object : ChangeJournalRepository by delegate {
            override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                delegate.record(actor, change).flatMap {
                    if (change.resourceType == "report_filter") {
                        probe.reached(actor.accountId, "journal")
                        if (probe.rejectJournal.compareAndSet(true, false))
                            return@flatMap Result.Failed(
                                Failure(FailureKind.UNAVAILABLE, "database_busy")
                            )
                    }
                    Result.Success(Unit)
                }
        }
}
