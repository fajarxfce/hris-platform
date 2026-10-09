package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollTaxOpeningDataSource
import dev.fajar.hris.schema.tables.records.PayrollTaxOpeningRevisionsRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollTaxOpeningProbe {
    @Volatile var omitRevision = false
    @Volatile var beforeAppend: ((PayrollTaxOpeningRevisionsRecord) -> Unit)? = null

    fun clear() {
        omitRevision = false
        beforeAppend = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollTaxOpeningProbeConfiguration {
    @Bean fun openingProbe() = PayrollTaxOpeningProbe()

    @Bean
    @Primary
    fun probedPayrollTaxOpeningSource(
        @Qualifier("payrollTaxOpeningSource") source: PayrollTaxOpeningDataSource,
        probe: PayrollTaxOpeningProbe,
    ): PayrollTaxOpeningDataSource =
        object : PayrollTaxOpeningDataSource by source {
            override fun append(row: PayrollTaxOpeningRevisionsRecord) {
                probe.beforeAppend?.invoke(row)
                if (!probe.omitRevision) source.append(row)
            }
        }
}
