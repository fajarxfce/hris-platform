package dev.fajar.hris.payroll.delivery.di

import dev.fajar.hris.payroll.delivery.pdf.PayrollPayslipPdfWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class PayrollDeliveryConfiguration {
    @Bean fun payslipPdfWriter() = PayrollPayslipPdfWriter()
}
