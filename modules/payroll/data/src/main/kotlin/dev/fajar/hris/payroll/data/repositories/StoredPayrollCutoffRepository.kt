package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class StoredPayrollCutoffRepository(private val source: PayrollCutoffDataSource) :
    PayrollCutoffRepository {
    override fun lock(company: UUID): Result<Unit> = safeDatabaseCall { source.lock(company) }

    override fun frozenMonths(
        company: UUID,
        employee: UUID?,
        months: Set<YearMonth>,
    ): Result<Boolean> = safeDatabaseCall {
        require(months.size <= 13)
        source.frozenMonths(company, employee, months.map { it.atDay(1) }.toSet())
    }

    override fun frozenFrom(company: UUID, employee: UUID?, from: YearMonth): Result<Boolean> =
        safeDatabaseCall {
            source.frozenFrom(company, employee, from.atDay(1))
        }
}
