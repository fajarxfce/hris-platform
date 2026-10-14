package dev.fajar.hris

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.data.datasources.PayrollPaymentDataSource
import dev.fajar.hris.payroll.domain.entities.PayrollPaymentBatchStatus
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollPaymentProbe {
    @Volatile var afterRead: ((UUID, String) -> Unit)? = null
    @Volatile var resultsFailure: Failure? = null
    @Volatile var omitAction = false
    @Volatile var omitResults = false

    fun clear() {
        afterRead = null
        resultsFailure = null
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

    @Bean
    @Primary
    fun probedPayrollPaymentRepository(
        @Qualifier("payrollPayments") delegate: PayrollPaymentRepository,
        probe: PayrollPaymentProbe,
    ): PayrollPaymentRepository =
        object : PayrollPaymentRepository by delegate {
            override fun find(companyId: UUID, id: UUID) =
                delegate.find(companyId, id).also { probe.afterRead?.invoke(companyId, "batch") }

            override fun candidates(companyId: UUID, assessmentIds: Set<UUID>) =
                delegate.candidates(companyId, assessmentIds).also {
                    probe.afterRead?.invoke(companyId, "candidates")
                }

            override fun payables(
                companyId: UUID,
                from: Instant,
                until: Instant,
                after: UUID?,
                limit: Int,
            ) =
                delegate.payables(companyId, from, until, after, limit).also {
                    probe.afterRead?.invoke(companyId, "payables")
                }

            override fun list(
                companyId: UUID,
                from: Instant,
                until: Instant,
                status: PayrollPaymentBatchStatus?,
                after: UUID?,
                limit: Int,
            ) =
                delegate.list(companyId, from, until, status, after, limit).also {
                    probe.afterRead?.invoke(companyId, "batches")
                }

            override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                delegate.history(companyId, id, after, limit).also {
                    probe.afterRead?.invoke(companyId, "history")
                }

            override fun results(companyId: UUID, id: UUID) =
                delegate.results(companyId, id).let { result ->
                    probe.afterRead?.invoke(companyId, "results")
                    probe.resultsFailure?.let { Result.Failed(it) } ?: result
                }
        }
}
