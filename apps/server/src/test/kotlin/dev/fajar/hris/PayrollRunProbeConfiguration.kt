package dev.fajar.hris

import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PayrollRunProbe {
    @Volatile var invalidTarget = false
    @Volatile var omitTargets = false
    @Volatile var omitResult = false
    @Volatile var omitCounter = false
    @Volatile var afterResult: (() -> Unit)? = null
    @Volatile var beforeResult: ((PayrollRunResultsRecord) -> Unit)? = null
    @Volatile var targetSnapshot: ((List<PayrollRunTargetsRecord>) -> Unit)? = null
    @Volatile var snapshot: ((String?) -> String?)? = null
    @Volatile var afterCutoffLock: ((UUID) -> Unit)? = null

    fun clear() {
        invalidTarget = false
        omitTargets = false
        omitResult = false
        omitCounter = false
        afterResult = null
        beforeResult = null
        targetSnapshot = null
        snapshot = null
        afterCutoffLock = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollRunProbeConfiguration {
    @Bean fun payrollRunProbe() = PayrollRunProbe()

    @Bean
    @Primary
    fun probedPayrollCutoffSource(
        @Qualifier("payrollCutoffSource") source: PayrollCutoffDataSource,
        probe: PayrollRunProbe,
    ): PayrollCutoffDataSource =
        object : PayrollCutoffDataSource by source {
            override fun lock(company: UUID) {
                source.lock(company)
                probe.afterCutoffLock?.invoke(company)
            }
        }

    @Bean
    @Primary
    fun probedPayrollRunSource(
        @Qualifier("payrollRunSource") source: PayrollRunDataSource,
        probe: PayrollRunProbe,
    ): PayrollRunDataSource =
        object : PayrollRunDataSource by source {
            override fun insertTargets(rows: List<PayrollRunTargetsRecord>) {
                probe.targetSnapshot?.invoke(rows)
                if (probe.invalidTarget) rows.first().employmentVersion += 1
                if (!probe.omitTargets) source.insertTargets(rows)
            }

            override fun insertResult(row: PayrollRunResultsRecord) {
                probe.beforeResult?.invoke(row)
                if (!probe.omitResult) source.insertResult(row)
                probe.afterResult?.invoke()
            }

            override fun advanceProgress(
                company: UUID,
                run: UUID,
                processed: Int,
                success: Boolean,
            ): Boolean =
                if (probe.omitCounter) true
                else source.advanceProgress(company, run, processed, success)
        }

    @Bean
    @Primary
    fun probedPayrollCalculationSource(
        @Qualifier("payrollCalculationSource") source: PayrollCalculationSourceDataSource,
        probe: PayrollRunProbe,
    ): PayrollCalculationSourceDataSource =
        object : PayrollCalculationSourceDataSource by source {
            override fun workSnapshot(company: UUID, job: UUID, employee: UUID): String? {
                val raw = source.workSnapshot(company, job, employee)
                return probe.snapshot?.invoke(raw) ?: raw
            }
        }
}
