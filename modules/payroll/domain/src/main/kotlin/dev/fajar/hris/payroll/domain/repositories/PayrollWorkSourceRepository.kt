package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.PayrollWorkSource
import java.time.YearMonth
import java.util.UUID

interface PayrollWorkSourceRepository {
    fun find(
        company: UUID,
        employee: UUID,
        month: YearMonth,
        lock: Boolean,
    ): Result<PayrollWorkSource?>
}
