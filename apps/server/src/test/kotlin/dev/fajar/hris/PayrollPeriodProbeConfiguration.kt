package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.schema.tables.records.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollPeriodProbe {
    @Volatile var omitMembers = false
    @Volatile var omitPeriodChange = false
    @Volatile var omitInputRevision = false
    @Volatile var beforeInputAppend: ((PayrollInputRevisionsRecord) -> Unit)? = null

    fun clear() {
        omitMembers = false
        omitPeriodChange = false
        omitInputRevision = false
        beforeInputAppend = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollPeriodProbeConfiguration {
    @Bean fun periodProbe() = PayrollPeriodProbe()

    @Bean
    @Primary
    fun probedPayrollPeriodSource(
        @Qualifier("payrollPeriodSource") source: PayrollPeriodDataSource,
        probe: PayrollPeriodProbe,
    ): PayrollPeriodDataSource =
        object : PayrollPeriodDataSource by source {
            override fun insertMembers(rows: List<PayrollPeriodMembersRecord>) {
                if (!probe.omitMembers) source.insertMembers(rows)
            }

            override fun append(row: PayrollPeriodChangesRecord) {
                if (!probe.omitPeriodChange) source.append(row)
            }
        }

    @Bean
    @Primary
    fun probedPayrollInputSource(
        @Qualifier("payrollInputSource") source: PayrollInputDataSource,
        probe: PayrollPeriodProbe,
    ): PayrollInputDataSource =
        object : PayrollInputDataSource by source {
            override fun append(row: PayrollInputRevisionsRecord) {
                probe.beforeInputAppend?.invoke(row)
                if (!probe.omitInputRevision) source.append(row)
            }
        }
}
