package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.payroll.data.models.CompensationRow
import dev.fajar.hris.payroll.data.models.PayrollEmployeeRow
import dev.fajar.hris.payroll.data.models.PayrollPolicyRow
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollProbe {
    @Volatile var omitPolicyRevision = false
    @Volatile var omitCompensationRevision = false
    @Volatile var beforeJournal: ((JournalRow) -> Unit)? = null
    @Volatile var afterJournal: ((JournalRow) -> Unit)? = null
    @Volatile var afterPolicyRead: ((String, UUID) -> Unit)? = null
    @Volatile var beforePolicyLock: ((UUID, Boolean) -> Unit)? = null
    @Volatile var afterCompensationRead: ((String, UUID) -> Unit)? = null

    fun clear() {
        omitPolicyRevision = false
        omitCompensationRevision = false
        beforeJournal = null
        afterJournal = null
        afterPolicyRead = null
        beforePolicyLock = null
        afterCompensationRead = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollProbeConfiguration {
    @Bean fun payrollProbe() = PayrollProbe()

    @Bean
    @Primary
    fun probedPayrollPolicySource(
        @Qualifier("payrollPolicySource") source: PayrollPolicyDataSource,
        probe: PayrollProbe,
    ): PayrollPolicyDataSource =
        object : PayrollPolicyDataSource by source {
            override fun lock(company: UUID, shared: Boolean) {
                probe.beforePolicyLock?.invoke(company, shared)
                source.lock(company, shared)
            }

            override fun current(company: UUID): PayrollPolicyRow? =
                source.current(company).also { probe.afterPolicyRead?.invoke("current", company) }

            override fun effective(company: UUID, month: LocalDate): PayrollPolicyRow? =
                source.effective(company, month).also {
                    probe.afterPolicyRead?.invoke("effective", company)
                }

            override fun history(company: UUID, after: Long?, limit: Int): List<PayrollPolicyRow> =
                source.history(company, after, limit).also {
                    probe.afterPolicyRead?.invoke("history", company)
                }

            override fun append(row: PayrollPolicyRevisionsRecord) {
                if (!probe.omitPolicyRevision) source.append(row)
            }
        }

    @Bean
    @Primary
    fun probedPayrollEmployeeSource(
        @Qualifier("payrollEmployeeSource") source: PayrollEmployeeDataSource,
        probe: PayrollProbe,
    ): PayrollEmployeeDataSource =
        object : PayrollEmployeeDataSource by source {
            override fun employee(company: UUID, employee: UUID): PayrollEmployeeRow? =
                source.employee(company, employee).also {
                    probe.afterCompensationRead?.invoke("employee", company)
                }

            override fun employees(
                company: UUID,
                query: String,
                after: String?,
                limit: Int,
            ): List<PayrollEmployeeRow> =
                source.employees(company, query, after, limit).also {
                    probe.afterCompensationRead?.invoke("employees", company)
                }
        }

    @Bean
    @Primary
    fun probedCompensationSource(
        @Qualifier("compensationSource") source: CompensationDataSource,
        probe: PayrollProbe,
    ): CompensationDataSource =
        object : CompensationDataSource by source {
            override fun current(company: UUID, employee: UUID): CompensationRow? =
                source.current(company, employee).also {
                    probe.afterCompensationRead?.invoke("current", company)
                }

            override fun effective(
                company: UUID,
                employee: UUID,
                month: LocalDate,
            ): CompensationRow? =
                source.effective(company, employee, month).also {
                    probe.afterCompensationRead?.invoke("effective", company)
                }

            override fun history(
                company: UUID,
                employee: UUID,
                after: Long?,
                limit: Int,
            ): List<CompensationRow> =
                source.history(company, employee, after, limit).also {
                    probe.afterCompensationRead?.invoke("history", company)
                }

            override fun list(
                company: UUID,
                month: LocalDate,
                after: UUID?,
                limit: Int,
            ): List<CompensationRow> =
                source.list(company, month, after, limit).also {
                    probe.afterCompensationRead?.invoke("list", company)
                }

            override fun append(row: EmployeeCompensationRevisionsRecord) {
                if (!probe.omitCompensationRevision) source.append(row)
            }
        }

    @Bean
    @Primary
    fun probedPayrollJournal(
        @Qualifier("journalSource") source: ChangeJournalDataSource,
        probe: PayrollProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                probe.beforeJournal?.invoke(row)
                source.append(row)
                probe.afterJournal?.invoke(row)
            }
        }
}
