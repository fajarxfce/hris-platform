package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentSourceDataSource
import dev.fajar.hris.payroll.data.models.PayrollAmendmentCandidateRow
import dev.fajar.hris.payroll.data.models.PayrollAmendmentSourceRow
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentSourceRepository
import java.time.LocalDate
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
    @Volatile
    var candidateTransform:
        ((UUID, List<PayrollAmendmentCandidateRow>) -> List<PayrollAmendmentCandidateRow>)? =
        null

    fun clear() {
        afterRead = null
        transform = null
        candidateTransform = null
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
            override fun candidates(
                company: UUID,
                from: LocalDate,
                until: LocalDate,
                employeeId: UUID?,
                query: String,
                excludedAccountId: UUID,
                after: UUID?,
                limit: Int,
            ): List<PayrollAmendmentCandidateRow> {
                val rows =
                    source.candidates(
                        company,
                        from,
                        until,
                        employeeId,
                        query,
                        excludedAccountId,
                        after,
                        limit,
                    )
                return probe.candidateTransform?.invoke(company, rows) ?: rows
            }

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
            override fun candidates(
                company: UUID,
                year: Int,
                employeeId: UUID?,
                query: String,
                excludedAccountId: UUID,
                after: UUID?,
                limit: Int,
            ) =
                source
                    .candidates(company, year, employeeId, query, excludedAccountId, after, limit)
                    .also { probe.afterRead?.invoke(company) }

            override fun sources(company: UUID, assessmentId: UUID) =
                source.sources(company, assessmentId).also { probe.afterRead?.invoke(company) }
        }
}
