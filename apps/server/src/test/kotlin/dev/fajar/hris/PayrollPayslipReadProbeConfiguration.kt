package dev.fajar.hris

import dev.fajar.hris.payroll.domain.entities.PayrollPayslipCursor
import dev.fajar.hris.payroll.domain.repositories.PayrollAssessmentRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollPayslipReadProbe {
    data class Barrier(
        val company: UUID,
        val kind: String,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()

    fun afterRead(company: UUID, kind: String) {
        val barrier = current.get()
        if (
            barrier != null &&
                barrier.company == company &&
                barrier.kind == kind &&
                current.compareAndSet(barrier, null)
        ) {
            barrier.entered.countDown()
            check(barrier.release.await(5, TimeUnit.SECONDS))
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollPayslipReadProbeConfiguration {
    @Bean fun payrollPayslipReadProbe() = PayrollPayslipReadProbe()

    @Bean
    @Primary
    fun probedPayslipAssessments(
        @Qualifier("payrollAssessments") delegate: PayrollAssessmentRepository,
        probe: PayrollPayslipReadProbe,
    ): PayrollAssessmentRepository =
        object : PayrollAssessmentRepository by delegate {
            override fun payslip(company: UUID, id: UUID, allowedEmployees: Set<UUID>?) =
                delegate.payslip(company, id, allowedEmployees).also {
                    probe.afterRead(company, "details")
                }

            override fun payslips(
                company: UUID,
                from: YearMonth,
                until: YearMonth,
                employeeId: UUID?,
                allowedEmployees: Set<UUID>?,
                after: PayrollPayslipCursor?,
                limit: Int,
                query: String,
            ) =
                delegate
                    .payslips(
                        company,
                        from,
                        until,
                        employeeId,
                        allowedEmployees,
                        after,
                        limit,
                        query,
                    )
                    .also { probe.afterRead(company, if (query.isEmpty()) "list" else "search") }
        }

    @Bean
    @Primary
    fun probedPayslipPayments(
        @Qualifier("payrollPayments") delegate: PayrollPaymentRepository,
        probe: PayrollPayslipReadProbe,
    ): PayrollPaymentRepository =
        object : PayrollPaymentRepository by delegate {
            override fun progress(
                companyId: UUID,
                assessmentId: UUID,
                allowedEmployees: Set<UUID>?,
            ) =
                delegate.progress(companyId, assessmentId, allowedEmployees).also {
                    probe.afterRead(companyId, "payments")
                }
        }
}
