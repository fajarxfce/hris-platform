package dev.fajar.hris.payroll.data.datasources

import java.time.*
import java.util.UUID

interface PayrollCutoffDataSource {
    fun lock(company: UUID)

    fun frozenMonths(company: UUID, employee: UUID?, months: Set<LocalDate>): Boolean

    fun frozenFrom(company: UUID, employee: UUID?, from: LocalDate): Boolean
}
