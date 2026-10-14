package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.schema.tables.records.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollPeriodProbe {
    @Volatile var omitTransition = false
    @Volatile var omitMembers = false
    @Volatile var omitPeriodChange = false
    @Volatile var afterRead: ((String, java.util.UUID) -> Unit)? = null
    @Volatile var omitInputRevision = false
    @Volatile var beforeInputAppend: ((PayrollInputRevisionsRecord) -> Unit)? = null

    fun clear() {
        afterRead = null
        omitTransition = false
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
            override fun list(
                company: java.util.UUID,
                from: java.time.LocalDate,
                until: java.time.LocalDate,
                status: String?,
                after: java.util.UUID?,
                limit: Int,
            ): List<PayrollPeriodsRecord> =
                source.list(company, from, until, status, after, limit).also {
                    probe.afterRead?.invoke("list", company)
                }

            override fun history(
                company: java.util.UUID,
                id: java.util.UUID,
                after: Long?,
                limit: Int,
            ): List<PayrollPeriodChangesRecord> =
                source.history(company, id, after, limit).also {
                    probe.afterRead?.invoke("detail", company)
                }

            override fun transition(
                company: java.util.UUID,
                id: java.util.UUID,
                version: Long,
                status: String,
                runId: java.util.UUID?,
            ): Long? =
                if (probe.omitTransition) version + 1
                else source.transition(company, id, version, status, runId)

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
