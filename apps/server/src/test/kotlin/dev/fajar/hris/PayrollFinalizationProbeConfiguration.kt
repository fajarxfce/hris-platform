package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollFinalizationDataSource
import dev.fajar.hris.schema.tables.records.PayrollFinalizationsRecord
import dev.fajar.hris.schema.tables.records.PayrollRunsRecord
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*

class PayrollFinalizationProbe {
    @Volatile var omitAssessments = false
    @Volatile var afterAssessments: (() -> Unit)? = null
    @Volatile var afterInsert: (() -> Unit)? = null
    @Volatile var afterRead: ((String, UUID) -> Unit)? = null

    fun clear() {
        omitAssessments = false
        afterAssessments = null
        afterInsert = null
        afterRead = null
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
            override fun publicationRuns(
                company: UUID,
                from: LocalDate,
                until: LocalDate,
                status: String?,
                after: UUID?,
                limit: Int,
            ): List<PayrollRunsRecord> =
                delegate.publicationRuns(company, from, until, status, after, limit).also {
                    probe.afterRead?.invoke("directory", company)
                }

            override fun find(company: UUID, id: UUID): PayrollFinalizationsRecord? =
                delegate.find(company, id).also { probe.afterRead?.invoke("detail", company) }

            override fun latest(company: UUID, run: UUID): PayrollFinalizationsRecord? =
                delegate.latest(company, run).also {
                    probe.afterRead?.invoke("preparation", company)
                }

            override fun list(
                company: UUID,
                run: UUID,
                after: Int?,
                limit: Int,
            ): List<PayrollFinalizationsRecord> =
                delegate.list(company, run, after, limit).also {
                    probe.afterRead?.invoke("list", company)
                }

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
