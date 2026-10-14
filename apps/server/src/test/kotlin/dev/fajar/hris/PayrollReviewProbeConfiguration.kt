package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollReviewDataSource
import dev.fajar.hris.payroll.data.models.PayrollRunTotalsRow
import dev.fajar.hris.schema.tables.records.PayrollReviewChangesRecord
import dev.fajar.hris.schema.tables.records.PayrollReviewsRecord
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*

class PayrollReviewProbe {
    @Volatile var omitChange = false
    @Volatile var afterChange: (() -> Unit)? = null
    @Volatile var afterRead: ((String, UUID) -> Unit)? = null

    fun clear() {
        omitChange = false
        afterChange = null
        afterRead = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollReviewProbeConfiguration {
    @Bean fun payrollReviewProbe() = PayrollReviewProbe()

    @Bean
    @Primary
    fun probedPayrollReviewSource(
        @Qualifier("payrollReviewSource") delegate: PayrollReviewDataSource,
        probe: PayrollReviewProbe,
    ): PayrollReviewDataSource =
        object : PayrollReviewDataSource by delegate {
            override fun totals(company: UUID, run: UUID): PayrollRunTotalsRow =
                delegate.totals(company, run).also {
                    probe.afterRead?.invoke("preparation", company)
                }

            override fun list(
                company: UUID,
                run: UUID,
                after: Int?,
                limit: Int,
            ): List<PayrollReviewsRecord> =
                delegate.list(company, run, after, limit).also {
                    probe.afterRead?.invoke("list", company)
                }

            override fun changes(company: UUID, review: UUID): List<PayrollReviewChangesRecord> =
                delegate.changes(company, review).also {
                    probe.afterRead?.invoke("detail", company)
                }

            override fun append(row: PayrollReviewChangesRecord) {
                if (!probe.omitChange) delegate.append(row)
                probe.afterChange?.invoke()
            }
        }
}
