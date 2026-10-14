package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentDataSource
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollAmendmentDraftProbe {
    @Volatile var omitRevision = false
    @Volatile var omitCalculation = false
    @Volatile var omitResult = false
    @Volatile var afterRevision: ((UUID) -> Unit)? = null
    @Volatile var afterRead: ((UUID) -> Unit)? = null
    @Volatile
    var transformRevision: ((PayrollAmendmentRevisionsRecord) -> PayrollAmendmentRevisionsRecord)? =
        null
    @Volatile
    var transformResults:
        ((List<PayrollAmendmentResultsRecord>) -> List<PayrollAmendmentResultsRecord>)? =
        null
    @Volatile
    var readResults: ((List<PayrollAmendmentResultRow>) -> List<PayrollAmendmentResultRow>)? = null

    fun clear() {
        omitRevision = false
        omitCalculation = false
        omitResult = false
        afterRevision = null
        afterRead = null
        transformRevision = null
        transformResults = null
        readResults = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollAmendmentDraftProbeConfiguration {
    @Bean fun payrollAmendmentDraftProbe() = PayrollAmendmentDraftProbe()

    @Bean
    @Primary
    fun probedPayrollAmendmentDraftSource(
        @Qualifier("payrollAmendmentDraftSource") source: PayrollAmendmentDataSource,
        probe: PayrollAmendmentDraftProbe,
    ): PayrollAmendmentDataSource =
        object : PayrollAmendmentDataSource by source {
            override fun append(row: PayrollAmendmentRevisionsRecord) {
                if (!probe.omitRevision) source.append(probe.transformRevision?.invoke(row) ?: row)
                probe.afterRevision?.invoke(row.companyId)
            }

            override fun insertCalculation(row: PayrollAmendmentCalculationsRecord) {
                if (!probe.omitCalculation) source.insertCalculation(row)
            }

            override fun insertResults(rows: List<PayrollAmendmentResultsRecord>) {
                val transformed = probe.transformResults?.invoke(rows) ?: rows
                source.insertResults(if (probe.omitResult) transformed.dropLast(1) else transformed)
            }

            override fun find(company: UUID, id: UUID): PayrollAmendmentRow? =
                source.find(company, id).also { probe.afterRead?.invoke(company) }

            override fun summary(company: UUID, id: UUID): PayrollAmendmentSummaryRow? =
                source.summary(company, id).also { probe.afterRead?.invoke(company) }

            override fun list(
                company: UUID,
                from: LocalDate,
                until: LocalDate,
                employeeId: UUID?,
                status: String?,
                query: String,
                excludedAccountId: UUID,
                after: UUID?,
                limit: Int,
            ): List<PayrollAmendmentSummaryRow> =
                source
                    .list(
                        company,
                        from,
                        until,
                        employeeId,
                        status,
                        query,
                        excludedAccountId,
                        after,
                        limit,
                    )
                    .also { probe.afterRead?.invoke(company) }

            override fun results(
                company: UUID,
                calculationId: UUID,
            ): List<PayrollAmendmentResultRow> {
                val rows = source.results(company, calculationId)
                return probe.readResults?.invoke(rows) ?: rows
            }
        }
}
