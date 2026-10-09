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
    @Volatile var snapshot: ((String?) -> String?)? = null

    fun clear() {
        invalidTarget = false
        omitTargets = false
        omitResult = false
        omitCounter = false
        afterResult = null
        snapshot = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PayrollRunProbeConfiguration {
    @Bean fun payrollRunProbe() = PayrollRunProbe()

    @Bean
    @Primary
    fun probedPayrollRunSource(
        @Qualifier("payrollRunSource") source: PayrollRunDataSource,
        probe: PayrollRunProbe,
    ): PayrollRunDataSource =
        object : PayrollRunDataSource by source {
            override fun insertTargets(rows: List<PayrollRunTargetsRecord>) {
                if (probe.invalidTarget) rows.first().employmentVersion += 1
                if (!probe.omitTargets) source.insertTargets(rows)
            }

            override fun insertResult(row: PayrollRunResultsRecord) {
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
