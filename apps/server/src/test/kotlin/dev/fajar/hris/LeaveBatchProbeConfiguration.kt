package dev.fajar.hris

import dev.fajar.hris.leave.data.datasources.LeaveBatchDataSource
import dev.fajar.hris.leave.data.models.LeaveBatchEmployeeRow
import dev.fajar.hris.schema.tables.records.LeaveBatchResultsRecord
import dev.fajar.hris.schema.tables.records.LeaveBatchesRecord
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LeaveBatchProbe {
    @Volatile var omitResult = false
    @Volatile var afterResult: (() -> Unit)? = null
    @Volatile var afterRead: (() -> Unit)? = null

    fun clear() {
        omitResult = false
        afterResult = null
        afterRead = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class LeaveBatchProbeConfiguration {
    @Bean fun leaveBatchProbe() = LeaveBatchProbe()

    @Bean
    @Primary
    fun probedLeaveBatchSource(
        @Qualifier("leaveBatchSource") source: LeaveBatchDataSource,
        probe: LeaveBatchProbe,
    ): LeaveBatchDataSource =
        object : LeaveBatchDataSource by source {
            override fun list(company: UUID, after: UUID?, limit: Int): List<LeaveBatchesRecord> =
                source.list(company, after, limit).also { probe.afterRead?.invoke() }

            override fun employees(company: UUID, ids: Set<UUID>): List<LeaveBatchEmployeeRow> =
                source.employees(company, ids).also { probe.afterRead?.invoke() }

            override fun active(
                company: UUID,
                type: UUID,
                kind: String,
                period: LocalDate,
            ): Boolean =
                source.active(company, type, kind, period).also { probe.afterRead?.invoke() }

            override fun outcome(row: LeaveBatchResultsRecord) {
                if (!probe.omitResult) source.outcome(row)
                probe.afterResult?.invoke()
            }
        }
}
