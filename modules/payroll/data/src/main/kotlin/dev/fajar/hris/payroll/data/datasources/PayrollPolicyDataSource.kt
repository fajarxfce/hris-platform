package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollPolicyRow
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

interface PayrollPolicyDataSource {
    fun lock(company: UUID, shared: Boolean)

    fun current(company: UUID): PayrollPolicyRow?

    fun effective(company: UUID, month: LocalDate): PayrollPolicyRow?

    fun history(company: UUID, after: Long?, limit: Int): List<PayrollPolicyRow>

    fun insert(row: PayrollPoliciesRecord)

    fun advance(company: UUID, expectedVersion: Long): Long?

    fun append(row: PayrollPolicyRevisionsRecord)
}
