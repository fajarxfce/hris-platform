package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollReviewDataSource
import dev.fajar.hris.schema.tables.records.PayrollReviewChangesRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*

class PayrollReviewProbe {
    @Volatile var omitChange = false
    @Volatile var afterChange: (() -> Unit)? = null

    fun clear() {
        omitChange = false
        afterChange = null
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
            override fun append(row: PayrollReviewChangesRecord) {
                if (!probe.omitChange) delegate.append(row)
                probe.afterChange?.invoke()
            }
        }
}
