package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollTaxOpeningDataSource
import dev.fajar.hris.payroll.data.models.PayrollTaxOpeningEmployeeRow
import dev.fajar.hris.payroll.data.models.PayrollTaxOpeningRow
import dev.fajar.hris.schema.tables.records.PayrollTaxOpeningRevisionsRecord
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollTaxOpeningProbe {
    @Volatile var afterRead: ((String, UUID) -> Unit)? = null
    @Volatile var omitRevision = false
    @Volatile var beforeAppend: ((PayrollTaxOpeningRevisionsRecord) -> Unit)? = null

    fun clear() {
        afterRead = null
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
            override fun find(company: UUID, employee: UUID, year: Int): PayrollTaxOpeningRow? =
                source.find(company, employee, year).also {
                    probe.afterRead?.invoke("detail", company)
                }

            override fun revision(
                company: UUID,
                employee: UUID,
                year: Int,
                revision: Long,
            ): PayrollTaxOpeningRow? =
                source.revision(company, employee, year, revision).also {
                    probe.afterRead?.invoke("revision", company)
                }

            override fun history(
                company: UUID,
                employee: UUID,
                year: Int,
                after: Long?,
                limit: Int,
            ): List<PayrollTaxOpeningRow> =
                source.history(company, employee, year, after, limit).also {
                    probe.afterRead?.invoke("history", company)
                }

            override fun list(
                company: UUID,
                year: Int,
                status: String?,
                after: UUID?,
                limit: Int,
            ): List<PayrollTaxOpeningRow> =
                source.list(company, year, status, after, limit).also {
                    probe.afterRead?.invoke("list", company)
                }

            override fun employees(
                company: UUID,
                year: Int,
                query: String,
                status: String?,
                after: String?,
                limit: Int,
            ): List<PayrollTaxOpeningEmployeeRow> =
                source.employees(company, year, query, status, after, limit).also {
                    probe.afterRead?.invoke("employees", company)
                }

            override fun authors(company: UUID, id: UUID): List<UUID> =
                source.authors(company, id).also { probe.afterRead?.invoke("authors", company) }

            override fun append(row: PayrollTaxOpeningRevisionsRecord) {
                probe.beforeAppend?.invoke(row)
                if (!probe.omitRevision) source.append(row)
            }
        }
}
