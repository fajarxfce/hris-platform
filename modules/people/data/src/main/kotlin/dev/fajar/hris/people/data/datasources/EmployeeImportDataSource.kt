package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.JSONB

interface EmployeeImportDataSource {
    fun insertBatch(record: EmployeeImportsRecord)

    fun insertRows(records: List<EmployeeImportRowsRecord>)

    fun insertAttempt(record: EmployeeImportAttemptsRecord)

    fun find(companyId: UUID, id: UUID, lock: Boolean): EmployeeImportsRecord?

    fun forJob(companyId: UUID, jobId: UUID, lock: Boolean): EmployeeImportsRecord?

    fun list(companyId: UUID, after: UUID?, limit: Int): List<EmployeeImportsRecord>

    fun rows(companyId: UUID, id: UUID, after: Int?, limit: Int): List<EmployeeImportRowsRecord>

    fun nextRow(companyId: UUID, id: UUID, status: String, after: Int): EmployeeImportRowsRecord?

    fun counts(companyId: UUID, id: UUID): Map<String, Int>

    fun outcome(
        companyId: UUID,
        id: UUID,
        rowNumber: Int,
        expectedStatus: String,
        status: String,
        issues: JSONB,
        employeeId: UUID?,
    ): Int

    fun transition(companyId: UUID, id: UUID, version: Long, status: String, jobId: UUID): Long?

    fun attempts(
        companyId: UUID,
        id: UUID,
        after: UUID?,
        limit: Int,
    ): List<EmployeeImportAttemptsRecord>
}
