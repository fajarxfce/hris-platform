package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentSourceDataSource
import dev.fajar.hris.payroll.data.models.PayrollAmendmentSourceRow
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentSourceRepository
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollAmendmentProbe {
    @Volatile var afterRead: ((UUID) -> Unit)? = null
    @Volatile
    var transform: ((UUID, List<PayrollAmendmentSourceRow>) -> List<PayrollAmendmentSourceRow>)? =
        null

    fun clear() {
        afterRead = null
        transform = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollAmendmentProbeConfiguration {
    @Bean fun payrollAmendmentProbe() = PayrollAmendmentProbe()

    @Bean
    @Primary
    fun probedAmendmentSource(
        @Qualifier("payrollAmendmentSource") source: PayrollAmendmentSourceDataSource,
        probe: PayrollAmendmentProbe,
    ): PayrollAmendmentSourceDataSource =
        object : PayrollAmendmentSourceDataSource {
            override fun sources(
                company: UUID,
                assessmentId: UUID,
            ): List<PayrollAmendmentSourceRow> {
                val rows = source.sources(company, assessmentId)
                return probe.transform?.invoke(company, rows) ?: rows
            }
        }

    @Bean
    @Primary
    fun probedAmendmentSources(
        @Qualifier("payrollAmendmentSources") source: PayrollAmendmentSourceRepository,
        probe: PayrollAmendmentProbe,
    ): PayrollAmendmentSourceRepository =
        object : PayrollAmendmentSourceRepository by source {
            override fun sources(company: UUID, assessmentId: UUID) =
                source.sources(company, assessmentId).also { probe.afterRead?.invoke(company) }
        }
}
