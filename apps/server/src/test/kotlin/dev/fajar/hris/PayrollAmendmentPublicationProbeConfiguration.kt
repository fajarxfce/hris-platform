package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentPublicationDataSource
import dev.fajar.hris.schema.tables.records.PayrollAmendmentPublicationsRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollAmendmentPublicationProbe {
    @Volatile var omitPublication = false
    @Volatile var omitRevisions = false
    @Volatile var omitHeads = false
    @Volatile var afterHeads: (() -> Unit)? = null
    @Volatile var afterRead: (() -> Unit)? = null
    @Volatile var transformPublication: ((PayrollAmendmentPublicationsRecord) -> Unit)? = null

    fun clear() {
        omitPublication = false
        omitRevisions = false
        omitHeads = false
        afterHeads = null
        afterRead = null
        transformPublication = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollAmendmentPublicationProbeConfiguration {
    @Bean fun amendmentPublicationProbe() = PayrollAmendmentPublicationProbe()

    @Bean
    @Primary
    fun probedAmendmentPublicationSource(
        @Qualifier("payrollAmendmentPublicationSource")
        source: PayrollAmendmentPublicationDataSource,
        probe: PayrollAmendmentPublicationProbe,
    ): PayrollAmendmentPublicationDataSource =
        object : PayrollAmendmentPublicationDataSource by source {
            override fun insert(row: PayrollAmendmentPublicationsRecord) {
                probe.transformPublication?.invoke(row)
                if (!probe.omitPublication) source.insert(row)
            }

            override fun list(
                company: UUID,
                amendment: UUID,
            ): List<PayrollAmendmentPublicationsRecord> =
                source.list(company, amendment).also { probe.afterRead?.invoke() }

            override fun find(
                company: UUID,
                amendment: UUID,
                id: UUID,
            ): PayrollAmendmentPublicationsRecord? =
                source.find(company, amendment, id).also { probe.afterRead?.invoke() }

            override fun insertRevisions(company: UUID, publication: UUID, at: OffsetDateTime) {
                if (!probe.omitRevisions) source.insertRevisions(company, publication, at)
            }

            override fun advanceHeads(company: UUID, publication: UUID) {
                if (!probe.omitHeads) source.advanceHeads(company, publication)
                probe.afterHeads?.invoke()
            }
        }
}
