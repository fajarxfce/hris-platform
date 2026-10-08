package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.EmploymentTransfersRecord
import java.util.UUID

interface EmploymentTransferDataSource {
    fun insert(record: EmploymentTransfersRecord)

    fun forEmployee(companyId: UUID, id: UUID): List<EmploymentTransfersRecord>
}
