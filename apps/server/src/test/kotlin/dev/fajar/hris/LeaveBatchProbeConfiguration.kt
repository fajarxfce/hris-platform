package dev.fajar.hris

import dev.fajar.hris.leave.data.datasources.LeaveBatchDataSource
import dev.fajar.hris.schema.tables.records.LeaveBatchResultsRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LeaveBatchProbe {
    @Volatile var omitResult = false
    @Volatile var afterResult: (() -> Unit)? = null

    fun clear() {
        omitResult = false
        afterResult = null
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
            override fun outcome(row: LeaveBatchResultsRecord) {
                if (!probe.omitResult) source.outcome(row)
                probe.afterResult?.invoke()
            }
        }
}
