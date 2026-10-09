package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID

/** Shared with leave: guards source changes while a run retains its frozen scope. */
interface PayrollCutoffRepository {
    fun lock(company: UUID): Result<Unit>

    fun frozenMonths(company: UUID, employee: UUID?, months: Set<YearMonth>): Result<Boolean>

    fun frozenFrom(company: UUID, employee: UUID?, from: YearMonth): Result<Boolean>
}
