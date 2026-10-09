package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollFinalizationDataSource
import dev.fajar.hris.schema.tables.records.PayrollFinalizationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*

class PayrollFinalizationProbe {
    @Volatile var omitAssessments = false
    @Volatile var afterAssessments: (() -> Unit)? = null
    @Volatile var afterInsert: (() -> Unit)? = null

    fun clear() {
        omitAssessments = false
        afterAssessments = null
        afterInsert = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollFinalizationProbeConfiguration {
    @Bean fun payrollFinalizationProbe() = PayrollFinalizationProbe()

    @Bean
    @Primary
    fun probedPayrollFinalizationSource(
        @Qualifier("payrollFinalizationSource") delegate: PayrollFinalizationDataSource,
        probe: PayrollFinalizationProbe,
    ): PayrollFinalizationDataSource =
        object : PayrollFinalizationDataSource by delegate {
            override fun insertAssessments(company: UUID, finalization: UUID, at: OffsetDateTime) {
                if (!probe.omitAssessments) delegate.insertAssessments(company, finalization, at)
                probe.afterAssessments?.invoke()
            }

            override fun insert(row: PayrollFinalizationsRecord) {
                delegate.insert(row)
                probe.afterInsert?.invoke()
            }
        }
}
