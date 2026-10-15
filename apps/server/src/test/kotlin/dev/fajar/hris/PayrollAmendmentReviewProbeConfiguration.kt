package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentReviewDataSource
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollAmendmentReviewProbe {
    @Volatile var omitReview = false
    @Volatile var omitChange = false
    @Volatile var afterChange: ((UUID) -> Unit)? = null
    @Volatile var afterRead: ((UUID) -> Unit)? = null
    @Volatile
    var transformReview: ((PayrollAmendmentReviewsRecord) -> PayrollAmendmentReviewsRecord)? = null
    @Volatile
    var transformChange:
        ((PayrollAmendmentReviewChangesRecord) -> PayrollAmendmentReviewChangesRecord)? =
        null

    fun clear() {
        omitReview = false
        omitChange = false
        afterChange = null
        afterRead = null
        transformReview = null
        transformChange = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollAmendmentReviewProbeConfiguration {
    @Bean fun payrollAmendmentReviewProbe() = PayrollAmendmentReviewProbe()

    @Bean
    @Primary
    fun probedPayrollAmendmentReviewSource(
        @Qualifier("payrollAmendmentReviewSource") source: PayrollAmendmentReviewDataSource,
        probe: PayrollAmendmentReviewProbe,
    ): PayrollAmendmentReviewDataSource =
        object : PayrollAmendmentReviewDataSource by source {
            override fun insert(row: PayrollAmendmentReviewsRecord) {
                if (!probe.omitReview) source.insert(probe.transformReview?.invoke(row) ?: row)
            }

            override fun append(row: PayrollAmendmentReviewChangesRecord) {
                if (!probe.omitChange) source.append(probe.transformChange?.invoke(row) ?: row)
                probe.afterChange?.invoke(row.companyId)
            }

            override fun find(company: UUID, id: UUID): PayrollAmendmentReviewsRecord? =
                source.find(company, id).also { probe.afterRead?.invoke(company) }

            override fun list(
                company: UUID,
                amendmentId: UUID,
            ): List<PayrollAmendmentReviewsRecord> =
                source.list(company, amendmentId).also { probe.afterRead?.invoke(company) }

            override fun latest(company: UUID, amendmentId: UUID): PayrollAmendmentReviewsRecord? =
                source.latest(company, amendmentId).also { probe.afterRead?.invoke(company) }

            override fun changes(
                company: UUID,
                id: UUID,
            ): List<PayrollAmendmentReviewChangesRecord> =
                source.changes(company, id).also { probe.afterRead?.invoke(company) }
        }
}
