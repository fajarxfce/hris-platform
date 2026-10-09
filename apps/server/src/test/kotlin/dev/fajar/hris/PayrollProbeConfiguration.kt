package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.schema.tables.records.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollProbe {
    @Volatile var omitPolicyRevision = false
    @Volatile var omitCompensationRevision = false
    @Volatile var beforeJournal: ((JournalRow) -> Unit)? = null

    fun clear() {
        omitPolicyRevision = false
        omitCompensationRevision = false
        beforeJournal = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollProbeConfiguration {
    @Bean fun payrollProbe() = PayrollProbe()

    @Bean
    @Primary
    fun probedPayrollPolicySource(
        @Qualifier("payrollPolicySource") source: PayrollPolicyDataSource,
        probe: PayrollProbe,
    ): PayrollPolicyDataSource =
        object : PayrollPolicyDataSource by source {
            override fun append(row: PayrollPolicyRevisionsRecord) {
                if (!probe.omitPolicyRevision) source.append(row)
            }
        }

    @Bean
    @Primary
    fun probedCompensationSource(
        @Qualifier("compensationSource") source: CompensationDataSource,
        probe: PayrollProbe,
    ): CompensationDataSource =
        object : CompensationDataSource by source {
            override fun append(row: EmployeeCompensationRevisionsRecord) {
                if (!probe.omitCompensationRevision) source.append(row)
            }
        }

    @Bean
    @Primary
    fun probedPayrollJournal(
        @Qualifier("journalSource") source: ChangeJournalDataSource,
        probe: PayrollProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                probe.beforeJournal?.invoke(row)
                source.append(row)
            }
        }
}
