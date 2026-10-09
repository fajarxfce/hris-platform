package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.PayrollPaymentDataSource
import dev.fajar.hris.schema.tables.records.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollPaymentProbe {
    @Volatile var omitAction = false
    @Volatile var omitResults = false

    fun clear() {
        omitAction = false
        omitResults = false
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollPaymentProbeConfiguration {
    @Bean fun payrollPaymentProbe() = PayrollPaymentProbe()

    @Bean
    @Primary
    fun probedPayrollPayments(
        @Qualifier("payrollPaymentSource") source: PayrollPaymentDataSource,
        probe: PayrollPaymentProbe,
    ): PayrollPaymentDataSource =
        object : PayrollPaymentDataSource by source {
            override fun action(row: PayrollPaymentActionsRecord) {
                if (!probe.omitAction) source.action(row)
            }

            override fun recordResults(rows: List<PayrollPaymentResultsRecord>) {
                if (!probe.omitResults) source.recordResults(rows)
            }
        }
}
